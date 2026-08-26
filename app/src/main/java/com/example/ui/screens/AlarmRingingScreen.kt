package com.example.ui.screens

import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AlarmViewModel

@Composable
fun AlarmRingingScreen(
    viewModel: AlarmViewModel,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enteredCode by viewModel.enteredPasscode.collectAsState()
    val keypadDigits by viewModel.keypadDigits.collectAsState()
    val statusMessage by viewModel.unlockStatusMessage.collectAsState()
    val isIncorrect by viewModel.isPasscodeIncorrect.collectAsState()
    val isSuccess by viewModel.isDeactivatedSuccessfully.collectAsState()

    // Color definitions
    val DarkBgColor = Color(0xFF141316)
    val LightPurpleColor = Color(0xFFD0BCFF)
    val KeypadBgColor = Color(0xFF333038)
    val TextColor = Color(0xFFE6E1E5)
    val DetailTextColor = Color(0xFFCAC4D0)

    // Trigger physical device vibration when incorrect pin is input
    LaunchedEffect(isIncorrect) {
        if (isIncorrect) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let {
                if (it.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(300)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBgColor),
        contentAlignment = Alignment.Center
    ) {
        if (isSuccess) {
            // Spectacular Celebration Deactivation animation screen
            SuccessDeactivationView()
        } else {
            // Normal Ringing with Siren lights
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header of Ringing screen with highly attractive Custom Siren Beacon
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .weight(1.3f, fill = false)
                ) {
                    SirenBeaconComponent(isRinging = true)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "ALARM RINGING",
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = label,
                        color = TextColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Verify your 10-digit PIN to stop the alarm.",
                        color = DetailTextColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Text(
                        text = "Keys shuffle after every press",
                        color = DetailTextColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Central PIN Progress Bubble Row showing entered letters/digits horizontally
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        for (i in 0 until 10) {
                            val isFilled = i < enteredCode.length
                            val letterText = if (isFilled) enteredCode[i].toString() else "•"
                            val currentBoxColor = if (isFilled) Color(0xFFFF5252) else KeypadBgColor
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(currentBoxColor)
                                    .border(
                                        width = 1.dp,
                                        color = if (isFilled) Color.Transparent else DetailTextColor.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            ) {
                                Text(
                                    text = letterText,
                                    color = if (isFilled) Color.White else DetailTextColor.copy(alpha = 0.5f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (statusMessage.isNotEmpty()) {
                        Text(
                            text = statusMessage,
                            color = if (isIncorrect) Color(0xFFF2B8B5) else Color(0xFFFF8A80),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // keypad Grid
                Column(
                    modifier = Modifier
                        .weight(3.6f)
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (keypadDigits.size >= 10) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // First 9 items
                            items(keypadDigits.take(9)) { digit ->
                                Button(
                                    onClick = { viewModel.onDigitPressed(context, digit) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KeypadBgColor,
                                        contentColor = TextColor
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(64.dp)
                                        .testTag("keypad_$digit")
                                ) {
                                    Text(
                                        text = digit.toString(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Row 4 Left: Backspace
                            item {
                                OutlinedButton(
                                    onClick = { viewModel.onBackspacePressed() },
                                    border = BorderStroke(1.5.dp, KeypadBgColor),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = LightPurpleColor
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(64.dp)
                                        .testTag("keypad_backspace")
                                ) {
                                    Text(
                                        text = "⌫",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Row 4 Center: The 10th active digit
                            item {
                                val digit = keypadDigits[9]
                                Button(
                                    onClick = { viewModel.onDigitPressed(context, digit) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KeypadBgColor,
                                        contentColor = TextColor
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(64.dp)
                                        .testTag("keypad_$digit")
                                ) {
                                    Text(
                                        text = digit.toString(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Row 4 Right: Clear
                            item {
                                OutlinedButton(
                                    onClick = { viewModel.onClearPressed() },
                                    border = BorderStroke(1.5.dp, KeypadBgColor),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = LightPurpleColor
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .height(64.dp)
                                        .testTag("keypad_clear")
                                ) {
                                    Text(
                                        text = "CLR",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SirenBeaconComponent(
    isRinging: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "siren_glow")
    val alphaGlow by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate_angle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(130.dp)
    ) {
        // Rotating beams and radial pulses
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2f

            if (isRinging) {
                rotate(rotateAngle, pivot = center) {
                    val rayCount = 9
                    for (i in 0 until rayCount) {
                        val angle = (360f / rayCount) * i
                        val rad = Math.toRadians(angle.toDouble())
                        val startLen = radius * 0.45f
                        val endLen = radius * 0.95f
                        val startX = center.x + (Math.cos(rad) * startLen).toFloat()
                        val startY = center.y + (Math.sin(rad) * startLen).toFloat()
                        val endX = center.x + (Math.cos(rad) * endLen).toFloat()
                        val endY = center.y + (Math.sin(rad) * endLen).toFloat()

                        drawLine(
                            color = Color(0xFFFF1744).copy(alpha = alphaGlow),
                            start = androidx.compose.ui.geometry.Offset(startX, startY),
                            end = androidx.compose.ui.geometry.Offset(endX, endY),
                            strokeWidth = 7.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }
                }
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFF1744).copy(alpha = alphaGlow * 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 0.8f
                )
            )
        }

        // Custom drawn high fidelity 3D style Alarm Siren Dome
        androidx.compose.foundation.Canvas(modifier = Modifier.size(64.dp)) {
            val w = size.width
            val h = size.height

            // Base shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, h * 0.86f),
                size = androidx.compose.ui.geometry.Size(w, h * 0.14f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
            )

            // Red beacon dome path
            val domePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.15f, h * 0.72f)
                cubicTo(
                    w * 0.15f, h * 0.15f,
                    w * 0.85f, h * 0.15f,
                    w * 0.85f, h * 0.72f
                )
                close()
            }
            drawPath(
                path = domePath,
                color = Color(0xFFFF1744)
            )

            // Base stand pedestal
            drawRoundRect(
                color = Color(0xFFC62828),
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.05f, h * 0.70f),
                size = androidx.compose.ui.geometry.Size(w * 0.90f, h * 0.12f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )

            // White shiny 3D glossy highlight
            val shinePath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.68f, h * 0.32f)
                cubicTo(
                    w * 0.73f, h * 0.42f,
                    w * 0.75f, h * 0.54f,
                    w * 0.74f, h * 0.65f
                )
                lineTo(w * 0.78f, h * 0.65f)
                cubicTo(
                    w * 0.79f, h * 0.54f,
                    w * 0.77f, h * 0.40f,
                    w * 0.72f, h * 0.28f
                )
                close()
            }
            drawPath(
                path = shinePath,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
fun SuccessDeactivationView(modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    
    val pulse = rememberInfiniteTransition(label = "pulse_success")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(700, easing = LinearOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1A12)) // Deep Forest Emerald Sleep Deactivation Bg
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            // Ripple radial waves propagating outwards indicating success deactivation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(pulseScale)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.08f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(pulseScale * 0.85f)
                    .background(Color(0xFF81C784).copy(alpha = 0.12f), CircleShape)
            )

            // Solid high contrast green badge with check icon inside
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale.value)
                    .background(Color(0xFF4CAF50), CircleShape)
                    .border(3.dp, Color(0xFFA5D6A7), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(68.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ALARM DEACTIVATED SECURELY",
            color = Color(0xFF81C784),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.scale(scale.value)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Success! Wide Awake",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Your 10-digit passcode was successfully matched. Safe wakefulness confirmed.",
            color = Color(0xFFCAC4D0).copy(alpha = 0.75f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
