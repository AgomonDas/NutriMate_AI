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
                    // Authenticated App Session
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
        // 1. Dynamic Weather Background across ALL panels:
        // Rendered crisp on HOME; beautifully blurred with Gaussian blur on all other pages!
        WeatherBackground(
            condition = weatherCondition,
            isDay = isDayTime,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isHomeTab) Modifier.blur(25.dp) else Modifier
                )
        )

        // 2. Frosted glass scrim overlay on other pages to ensure maximum text & card legibility
        if (!isHomeTab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xBF081224),
                                Color(0xD90E1B38),
                                Color(0xF2080E1E)
                            )
                        )
                    )
            )
        }

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
            if (!uiState.onboardingCompleted) {
                OnboardingScreen(
                    viewModel = viewModel,
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
            .height(70.dp)
            .shadow(16.dp, RoundedCornerShape(36.dp), ambientColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(36.dp))
            .background(Color(0xD90E182E))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(36.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Home
            NavBarItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = currentTab == AppNavTab.HOME,
                onClick = { onTabSelected(AppNavTab.HOME) },
                testTag = "nav_home"
            )

            // 2. Plan (former Today)
            NavBarItem(
                icon = Icons.Default.RestaurantMenu,
                label = "Plan",
                isSelected = currentTab == AppNavTab.TODAY,
                onClick = { onTabSelected(AppNavTab.TODAY) },
                testTag = "nav_plan"
            )

            // 3. Center floating Scan button
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .offset(y = (-4).dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFB9FFD7), Color(0xFF34D98A))
                        )
                    )
                    .border(1.dp, Color.White, CircleShape)
                    .clickable(onClick = onScanClick)
                    .testTag("nav_scan_qr"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = MintAccentDark,
                    modifier = Modifier.size(26.dp)
                )
            }

            // 4. Ask AI
            NavBarItem(
                icon = Icons.Default.ChatBubbleOutline,
                label = "Ask AI",
                isSelected = false,
                onClick = { onTabSelected(AppNavTab.CHAT) },
                testTag = "nav_chat"
            )

            // 5. Month
            NavBarItem(
                icon = Icons.Default.CalendarMonth,
                label = "Month",
                isSelected = currentTab == AppNavTab.MONTH,
                onClick = { onTabSelected(AppNavTab.MONTH) },
                testTag = "nav_month"
            )

            // 6. Me
            NavBarItem(
                icon = Icons.Default.PersonOutline,
                label = "Me",
                isSelected = currentTab == AppNavTab.ME,
                onClick = { onTabSelected(AppNavTab.ME) },
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
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MintAccent else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MintAccent else Color.White.copy(alpha = 0.65f)
        )
    }
}
