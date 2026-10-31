package com.example.nutrimate.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nutrimate.ui.components.GlassBox
import com.example.nutrimate.ui.components.GlassButton
import com.example.nutrimate.ui.theme.*
import com.example.nutrimate.viewmodel.NutriMateViewModel

@Composable
fun ScanPayScreen(
    viewModel: NutriMateViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Laser scan animation
    val infiniteTransition = rememberInfiniteTransition(label = "scanLaser")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 220f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserPos"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF5060A14))
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Close button at top right
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                .testTag("close_scan_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close scanner",
                tint = Color.White
            )
        }

        if (uiState.scanPaidSuccess) {
            // Success confirmation view
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Payment Successful",
                            tint = MintAccent,
                            modifier = Modifier.size(68.dp)
                        )

                        Text(
                            text = "Opening ${uiState.scanPaymentMethod} · ৳${uiState.scanNetPayable}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Receipt saved to your billing history and monthly food budget.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GlassButton(
                            text = "Done",
                            onClick = {
                                viewModel.dismissScan()
                                onClose()
                            },
                            testTag = "scan_done_btn"
                        )
                    }
                }
            }
        } else if (uiState.scanFoundPartner) {
            // Payment breakdown view
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassBox(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Paying at",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Star Kabab & Biriyani",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TagYellow)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "PARTNER −10%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TagYellowText
                                )
                            }
                        }

                        OutlinedTextField(
                            value = uiState.scanBillAmount.toString(),
                            onValueChange = { str ->
                                viewModel.updateScanBillAmount(str.toIntOrNull() ?: 0)
                            },
                            label = { Text("Bill Amount (৳)", color = Color.White.copy(alpha = 0.7f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = MintAccent,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                                focusedContainerColor = GlassInputBg,
                                unfocusedContainerColor = GlassInputBg
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_bill_amount_input")
                        )

                        // Breakdown lines
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Bill", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                                Text("৳${uiState.scanBillAmount}", fontSize = 14.sp, color = Color.White)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("NutriMate discount", fontSize = 14.sp, color = MintAccentGlow)
                                Text("−৳${uiState.scanDiscountAmount}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MintAccentGlow)
                            }

                            Divider(color = Color.White.copy(alpha = 0.25f), thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("You pay", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    "৳${uiState.scanNetPayable}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        // Payment Methods
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.completePayment("bKash") },
                                colors = ButtonDefaults.buttonColors(containerColor = BKashColor),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("pay_bkash_btn")
                            ) {
                                Text("bKash", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Button(
                                onClick = { viewModel.completePayment("Nagad") },
                                colors = ButtonDefaults.buttonColors(containerColor = NagadColor),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("pay_nagad_btn")
                            ) {
                                Text("Nagad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Viewfinder scanning view
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan partner QR",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Bangla QR · bKash · Nagad",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 26.dp)
                )

                // Viewfinder Box with corner marks and animated scanning line
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                ) {
                    // Corner accents
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.TopStart)
                            .border(
                                width = 4.dp,
                                color = MintAccent,
                                shape = RoundedCornerShape(topStart = 30.dp)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.BottomEnd)
                            .border(
                                width = 4.dp,
                                color = MintAccent,
                                shape = RoundedCornerShape(bottomEnd = 30.dp)
                            )
                    )

                    // Laser line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .offset(y = laserOffset.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(MintAccent.copy(alpha = 0.3f), MintAccent, MintAccent.copy(alpha = 0.3f))
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                GlassButton(
                    text = "Simulate scan",
                    onClick = { viewModel.simulatePartnerFound() },
                    modifier = Modifier.widthIn(max = 280.dp),
                    testTag = "simulate_scan_btn"
                )
            }
        }
    }
}
