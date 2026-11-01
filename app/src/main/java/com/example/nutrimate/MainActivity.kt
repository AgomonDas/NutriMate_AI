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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nutrimate.data.repository.NutriMateFirestoreRepository
import com.example.nutrimate.model.AppNavTab
import com.example.nutrimate.ui.screens.*
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                    AppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun AppContent(viewModel: NutriMateViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Back navigation handling
    BackHandler(enabled = uiState.isScanning) {
        viewModel.dismissScan()
    }
    BackHandler(enabled = !uiState.isScanning && uiState.isChatOpen) {
        viewModel.closeChat()
    }
    BackHandler(
        enabled = !uiState.isScanning && !uiState.isChatOpen &&
                uiState.onboardingCompleted && uiState.currentTab != AppNavTab.TODAY
    ) {
        viewModel.selectTab(AppNavTab.TODAY)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F8F78),
                        Color(0xFF2A4BD6),
                        Color(0xFF131A32),
                        Color(0xFF0B1020)
                    )
                )
            )
            .systemBarsPadding()
    ) {
        if (!uiState.onboardingCompleted) {
            OnboardingScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Main content based on active tab
            Box(modifier = Modifier.fillMaxSize()) {
                when (uiState.currentTab) {
                    AppNavTab.TODAY -> HomeScreen(viewModel = viewModel)
                    AppNavTab.MONTH -> MonthScreen(viewModel = viewModel)
                    AppNavTab.ME -> ProfileScreen(viewModel = viewModel)
                    else -> HomeScreen(viewModel = viewModel)
                }
            }

            // Floating bottom navigation bar
            NutriMateBottomNavBar(
                currentTab = uiState.currentTab,
                onTabSelected = { tab -> viewModel.selectTab(tab) },
                onScanClick = { viewModel.startScan() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            )

            // AI Chat Sheet
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
                    onDismiss = { viewModel.closeChat() }
                )
            }

            // QR Scanner / Pay Overlay
            AnimatedVisibility(
                visible = uiState.isScanning,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(250))
            ) {
                ScanPayScreen(
                    viewModel = viewModel,
                    onClose = { viewModel.dismissScan() }
                )
            }
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
            .height(72.dp)
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
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarItem(
                icon = Icons.Default.Home,
                label = "Today",
                isSelected = currentTab == AppNavTab.TODAY,
                onClick = { onTabSelected(AppNavTab.TODAY) },
                testTag = "nav_today"
            )

            NavBarItem(
                icon = Icons.Default.ChatBubbleOutline,
                label = "Ask AI",
                isSelected = false,
                onClick = { onTabSelected(AppNavTab.CHAT) },
                testTag = "nav_chat"
            )

            // Center floating Scan button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .offset(y = (-6).dp)
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
                    modifier = Modifier.size(28.dp)
                )
            }

            NavBarItem(
                icon = Icons.Default.CalendarMonth,
                label = "Month",
                isSelected = currentTab == AppNavTab.MONTH,
                onClick = { onTabSelected(AppNavTab.MONTH) },
                testTag = "nav_month"
            )

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
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MintAccent else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MintAccent else Color.White.copy(alpha = 0.65f)
        )
    }
}
