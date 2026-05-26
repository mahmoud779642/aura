package com.auracam.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.app.domain.repository.ThermalThrottlingLevel
import com.auracam.app.ui.theme.CardBlack
import com.auracam.app.ui.theme.PremiumGold
import com.auracam.app.ui.theme.SoftGray
import com.auracam.app.ui.theme.StudioWhite

@Composable
fun ProControlPanel(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChanged: (Float) -> Unit,
    thermalLevel: ThermalThrottlingLevel,
    modifier: Modifier = Modifier
) {
    var evOffset by remember { mutableStateOf(0.0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardBlack.copy(alpha = 0.85f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(4.dp)
                .background(SoftGray, CircleShape)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(1.0f, 2.0f, 4.0f, 8.0f).forEach { preset ->
                val isSelected = Math.abs(zoomRatio - preset) < 0.1f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(if (isSelected) PremiumGold else Color.DarkGray, CircleShape)
                        .clickable { onZoomChanged(preset) }
                ) {
                    Text(
                        text = "${preset.toInt()}x",
                        color = if (isSelected) Color.Black else StudioWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = "EV",
                color = StudioWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp)
            )
            
            Slider(
                value = evOffset,
                onValueChange = { evOffset = it },
                valueRange = -2.0f..2.0f,
                steps = 4,
                colors = SliderDefaults.colors(
                    thumbColor = PremiumGold,
                    activeTrackColor = PremiumGold,
                    inactiveTrackColor = Color.DarkGray
                ),
                modifier = Modifier.weight(1f)
            )

            Text(
                text = String.format("%+.1f", evOffset),
                color = StudioWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            val badgeColor = when (thermalLevel) {
                ThermalThrottlingLevel.NORMAL -> Color.Green
                ThermalThrottlingLevel.MODERATE -> Color.Yellow
                ThermalThrottlingLevel.SEVERE -> Color.Red
                ThermalThrottlingLevel.CRITICAL -> Color.Red
            }
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(badgeColor, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "PIPELINE PROFILE: ${thermalLevel.name}",
                color = StudioWhite,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
