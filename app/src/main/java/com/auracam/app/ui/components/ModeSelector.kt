package com.auracam.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracam.app.domain.model.CameraMode
import com.auracam.app.ui.theme.PremiumGold
import com.auracam.app.ui.theme.SoftGray

@Composable
fun ModeSelector(
    activeMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = CameraMode.values()
    val listState = rememberLazyListState()

    LaunchedEffect(activeMode) {
        val index = modes.indexOf(activeMode)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 140.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(modes) { mode ->
                val isSelected = mode == activeMode
                Text(
                    text = mode.name,
                    color = if (isSelected) PremiumGold else SoftGray,
                    fontSize = if (isSelected) 14.sp else 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier
                        .padding(horizontal = 18.dp)
                        .clickable { onModeSelected(mode) }
                )
            }
        }
    }
}
