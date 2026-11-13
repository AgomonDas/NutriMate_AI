package com.example.nutrimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.data.weather.WeatherCondition
import com.example.nutrimate.model.AppNavTab
import com.example.nutrimate.ui.components.WeatherBackground
import com.example.nutrimate.ui.screens.*
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.DashboardViewModel
import com.example.nutrimate.viewmodel.NutriMateViewModel
import java.util.Calendar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.nutrimate.data.configureAppCheck(this, intent)
        enableEdgeToEdge()

        val dbId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(dbId)
        val repository = NutriMateFirestoreRepository(firestore)

        setContent {
            NutriMateTheme {
                var currentUser by remember { mutableStateOf<FirebaseUser?>(Firebase.auth.currentUser) }

                DisposableEffect(Unit) {
                    val authListener = FirebaseAuth.AuthStateListener { auth ->
                        currentUser = auth.currentUser
                    }
                    Firebase.auth.addAuthStateListener(authListener)
                    onDispose {
                        Firebase.auth.removeAuthStateListener(authListener)
                    }
                }

                val user = currentUser
                if (user == null) {
                    // Auth Gate: user must sign in with Google
                    AuthScreen(
                        onAuthSuccess = {
                            currentUser = Firebase.auth.currentUser
                        }
                    )
                } else {
                    // Authenticated App Session scoped by user UID
                    key(user.uid) {
                        val gatekeeper = remember(user.uid) {
                            com.example.nutrimate.data.feast.FeastBudgetGatekeeper(firestore, 700_000L)
                        }
                        val viewModel: NutriMateViewModel = viewModel(
                            key = user.uid,
                            factory = viewModelFactory {
                                initializer {
                                    NutriMateViewModel(
                                        repository = repository,
                                        currentUserId = user.uid,
                                        gatekeeper = gatekeeper
                                    )
                                }
                            }
                        )

                        val dashboardViewModel: DashboardViewModel = viewModel(
                            key = user.uid + "_dash",
                            factory = viewModelFactory {
                                initializer {
                                    DashboardViewModel(
                                        repository = repository,
                                        currentUserId = user.uid
                                    )
                                }
                            }
                        )

                        AppContent(
                            viewModel = viewModel,
                            dashboardViewModel = dashboardViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppContent(
    viewModel: NutriMateViewModel,
    dashboardViewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val isHomeTab = uiState.currentTab == AppNavTab.HOME

    val dashState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val weatherCondition = dashState.currentWeather?.condition ?: WeatherCondition.CLEAR
    val isDayTime = dashState.currentWeather?.isDay ?: run {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        hour in 6..17
    }

    // Back navigation handling
    BackHandler(enabled = uiState.isScanning) {
        viewModel.dismissScan()
    }
    BackHandler(enabled = !uiState.isScanning && uiState.isChatOpen) {
        viewModel.closeChat()
    }
    BackHandler(
        enabled = !uiState.isScanning && !uiState.isChatOpen &&
                uiState.onboardingCompleted && uiState.currentTab != AppNavTab.HOME
    ) {
        viewModel.selectTab(AppNavTab.HOME)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Super-blurry ambient atmospheric background across ALL screens (including Home)
        WeatherBackground(
            condition = weatherCondition,
            isDay = isDayTime,
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    radius = if (isHomeTab) 72.dp else 88.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                )
        )

        // 2. Soft frosted scrim overlay so cards & welcome text remain crisp and legible everywhere
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (isHomeTab) {
                            listOf(
                                Color(0x45060E1C),
                                Color(0x660A1529),
                                Color(0x88060D1A)
                            )
                        } else {
                            listOf(
                                Color(0xB8081224),
                                Color(0xD60E1B38),
                                Color(0xF0080E1E)
                            )
                        }
                    )
                )
        )

        // 3. Screen content container
        Box(
            modifier = if (isHomeTab) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            }
        ) {
            if (uiState.isCheckingOnboarding) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MintAccent)
                }
            } else if (!uiState.onboardingCompleted) {
                OnboardingScreen(
                    viewModel = viewModel,
                    onSignOut = { Firebase.auth.signOut() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (uiState.currentTab) {
                        AppNavTab.HOME -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            userName = uiState.userGoals.name.ifBlank { "Student" },
                            monthlyBudgetTk = uiState.userGoals.monthlyBudget,
                            onOpenChat = { viewModel.openChat() }
                        )
                        AppNavTab.TODAY -> HomeScreen(viewModel = viewModel)
                        AppNavTab.MONTH -> MonthScreen(viewModel = viewModel)
                        AppNavTab.ME -> ProfileScreen(viewModel = viewModel)
                        else -> DashboardScreen(
                            viewModel = dashboardViewModel,
                            userName = uiState.userGoals.name.ifBlank { "Student" },
                            monthlyBudgetTk = uiState.userGoals.monthlyBudget,
                            onOpenChat = { viewModel.openChat() }
                        )
                    }
                }
            }
        }

        // 4. Floating bottom navigation bar
        val bottomNavModifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)

        if (uiState.onboardingCompleted) {
            NutriMateBottomNavBar(
                currentTab = uiState.currentTab,
                onTabSelected = { tab ->
                    if (tab == AppNavTab.CHAT) {
                        viewModel.openChat()
                    } else {
                        viewModel.selectTab(tab)
                    }
                },
                onScanClick = { viewModel.startScan() },
                modifier = bottomNavModifier
            )
        }

        // 5. AI Chat Sheet (ChatSheet handles its own imePadding and navigationBarsPadding)
        AnimatedVisibility(
            visible = uiState.isChatOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            ) + fadeOut()
        ) {
            ChatSheet(
                viewModel = viewModel,
                onDismiss = { viewModel.closeChat() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 6. QR Scanner / Pay Overlay
        AnimatedVisibility(
            visible = uiState.isScanning,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(250))
        ) {
            ScanPayScreen(
                viewModel = viewModel,
                onClose = { viewModel.dismissScan() },
                modifier = Modifier.systemBarsPadding()
            )
        }
    }
}

@Composable
fun NutriMateBottomNavBar(
    currentTab: AppNavTab,
    onTabSelected: (AppNavTab) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(16.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xE60E182E))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            NavBarItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentTab == AppNavTab.HOME,
                onClick = { onTabSelected(AppNavTab.HOME) },
                modifier = Modifier.weight(1f),
                testTag = "nav_home"
            )

            // 2. Plan
            NavBarItem(
                icon = Icons.Default.RestaurantMenu,
                label = "Plan",
                isSelected = currentTab == AppNavTab.TODAY,
                onClick = { onTabSelected(AppNavTab.TODAY) },
                modifier = Modifier.weight(1f),
                testTag = "nav_plan"
            )

            // 3. Center Scan Action
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onScanClick)
                    .padding(vertical = 2.dp)
                    .testTag("nav_scan_qr"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0xFFB9FFD7), Color(0xFF34D98A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan",
                        tint = MintAccentDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scan",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7CE7AC),
                    maxLines = 1
                )
            }

            // 4. Ask AI
            NavBarItem(
                icon = Icons.Default.ChatBubbleOutline,
                label = "Ask AI",
                isSelected = false,
                onClick = { onTabSelected(AppNavTab.CHAT) },
                modifier = Modifier.weight(1f),
                testTag = "nav_chat"
            )

            // 5. Month
            NavBarItem(
                icon = Icons.Default.CalendarMonth,
                label = "Month",
                isSelected = currentTab == AppNavTab.MONTH,
                onClick = { onTabSelected(AppNavTab.MONTH) },
                modifier = Modifier.weight(1f),
                testTag = "nav_month"
            )

            // 6. Me
            NavBarItem(
                icon = Icons.Default.PersonOutline,
                label = "Me",
                isSelected = currentTab == AppNavTab.ME,
                onClick = { onTabSelected(AppNavTab.ME) },
                modifier = Modifier.weight(1f),
                testTag = "nav_me"
            )
        }
    }
}

@Composable
fun NavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "nav_item"
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) MintAccent.copy(alpha = 0.2f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MintAccent else Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MintAccent else Color.White.copy(alpha = 0.65f),
            maxLines = 1
        )
    }
}
