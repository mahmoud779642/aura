package com.auracam.app.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.auracam.app.domain.model.CameraMode
import com.auracam.app.domain.repository.CameraRepository
import com.auracam.app.domain.repository.ThermalThrottlingLevel
import com.auracam.app.ui.components.ModeSelector
import com.auracam.app.ui.components.ProControlPanel
import com.auracam.app.ui.components.ShutterButton
import com.auracam.app.ui.theme.ObsidianBlack
import com.auracam.app.ui.theme.PremiumGold
import com.auracam.app.ui.theme.StudioWhite

@Composable
fun CameraScreen(
    cameraRepository: CameraRepository,
    thermalLevel: ThermalThrottlingLevel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by cameraRepository.uiState.collectAsState()
    
    var showProPanel by remember { mutableStateOf(false) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // 1. Android View Binder for CameraX Live Preview Viewfinder
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                cameraRepository.bindViewfinder(previewView)
            }
        )

        // 2. Viewfinder Overlay Controls (Top Section)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash toggler indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clickable {
                            Toast.makeText(context, "Flash configuration managed automatically.", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Text("⚡", color = StudioWhite, fontSize = 16.sp)
                }

                // Pro dashboard toggle indicator
                Button(
                    onClick = { showProPanel = !showProPanel },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showProPanel) PremiumGold else Color.Black.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "PRO OPTIONS",
                        color = if (showProPanel) Color.Black else StudioWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Last photo preview circular item
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, StudioWhite, CircleShape)
                        .clickable {
                            lastCapturedUri?.let { uri ->
                                Toast.makeText(context, "Saved image: ${uri.lastPathSegment}", Toast.LENGTH_LONG).show()
                            } ?: Toast.makeText(context, "No captured photo yet.", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    if (lastCapturedUri != null) {
                        Text("🖼️", fontSize = 16.sp)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.DarkGray, CircleShape)
                        )
                    }
                }
            }
        }

        // 3. Bottom Camera Controls & Shutter Actions
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Transparent)
        ) {
            
            // Lens facing Switcher Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable { cameraRepository.toggleLens() }
                ) {
                    Text("🔄", color = StudioWhite, fontSize = 16.sp)
                }
            }

            // Mode Selector Carousel
            ModeSelector(
                activeMode = uiState.activeMode,
                onModeSelected = { cameraRepository.setCameraMode(it) },
                modifier = Modifier.fillMaxWidth()
            )

            // Primary Shutter Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(bottom = 34.dp, top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShutterButton(
                    isRecording = uiState.isRecordingVideo,
                    onClick = {
                        if (uiState.activeMode == CameraMode.VIDEO) {
                            if (uiState.isRecordingVideo) {
                                cameraRepository.stopVideoRecording()
                            } else {
                                cameraRepository.startVideoRecording { uri ->
                                    lastCapturedUri = uri
                                }
                            }
                        } else {
                            cameraRepository.capturePhoto { uri ->
                                lastCapturedUri = uri
                            }
                        }
                    }
                )
            }

            // Dynamic Slide-Up Pro Dashboard Panel
            AnimatedVisibility(
                visible = showProPanel,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ProControlPanel(
                    zoomRatio = uiState.zoomRatio,
                    minZoom = uiState.minZoom,
                    maxZoom = uiState.maxZoom,
                    onZoomChanged = { cameraRepository.setZoom(it) },
                    thermalLevel = thermalLevel
                )
            }
        }

        // 4. Custom Processing Loader & Progress Glassmorphic overlay
        AnimatedVisibility(
            visible = uiState.isProcessing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .wrapContentSize(),
                    colors = CardDefaults.cardColors(containerColor = CardDefaults.cardColors().containerColor.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = PremiumGold,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.processingMessage,
                            color = StudioWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Helio G99 DSLR Engine Active",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
