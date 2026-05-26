package com.auracam.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.auracam.app.ui.theme.CrimsonAlert
import com.auracam.app.ui.theme.PremiumGold
import com.auracam.app.ui.theme.StudioWhite

@Composable
fun ShutterButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth press feedback scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        label = "shutterScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(82.dp)
            .scale(scale)
            .border(4.dp, if (isRecording) CrimsonAlert else StudioWhite, CircleShape)
            .padding(6.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                // Tactile feedback on click
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isRecording) CrimsonAlert else PremiumGold,
                    CircleShape
                )
        )
    }
}
