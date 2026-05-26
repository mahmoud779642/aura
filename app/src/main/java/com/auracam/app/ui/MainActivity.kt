package com.auracam.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auracam.app.data.camera.CameraXProvider
import com.auracam.app.data.repository.MediaRepositoryImpl
import com.auracam.app.data.thermal.ThermalRepositoryImpl
import com.auracam.app.domain.repository.CameraRepository
import com.auracam.app.domain.repository.ThermalRepository
import com.auracam.app.domain.repository.ThermalThrottlingLevel
import com.auracam.app.processing.ImageProcessor
import com.auracam.app.processing.ai.PortraitDepthEstimator
import com.auracam.app.ui.screen.CameraScreen
import com.auracam.app.ui.theme.AuraCamTheme
import com.auracam.app.ui.theme.ObsidianBlack
import com.auracam.app.ui.theme.PremiumGold
import com.auracam.app.ui.theme.StudioWhite

class MainActivity : ComponentActivity() {

    private lateinit var thermalRepository: ThermalRepository
    private lateinit var depthEstimator: PortraitDepthEstimator
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var cameraRepository: CameraRepository

    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private var hasPermissionsGranted by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        
        if (cameraGranted && audioGranted) {
            hasPermissionsGranted = true
            initializeCameraSystem()
        } else {
            hasPermissionsGranted = false
            Toast.makeText(this, "Camera and Audio permissions are required to operate.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissionsGranted) {
            initializeCameraSystem()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }

        setContent {
            AuraCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBlack
                ) {
                    if (hasPermissionsGranted) {
                        val thermalLevel by thermalRepository.throttlingLevel.collectAsState()
                        CameraScreen(
                            cameraRepository = cameraRepository,
                            thermalLevel = thermalLevel
                        )
                    } else {
                        PermissionRationaleScreen {
                            requestPermissionLauncher.launch(requiredPermissions)
                        }
                    }
                }
            }
        }
    }

    private fun initializeCameraSystem() {
        // Assemble decoupled hardware modules according to SOLID dependency rules
        thermalRepository = ThermalRepositoryImpl(applicationContext)
        val mediaRepository = MediaRepositoryImpl(applicationContext)
        depthEstimator = PortraitDepthEstimator(applicationContext)
        imageProcessor = ImageProcessor(depthEstimator)
        
        cameraRepository = CameraXProvider(
            context = applicationContext,
            lifecycleOwner = this,
            thermalRepository = thermalRepository,
            mediaRepository = mediaRepository,
            imageProcessor = imageProcessor
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraRepository.isInitialized) cameraRepository.shutdown()
        if (::thermalRepository.isInitialized) thermalRepository.unregister()
        if (::depthEstimator.isInitialized) depthEstimator.shutdown()
    }
}

@Composable
fun PermissionRationaleScreen(onGrantRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📸",
            fontSize = 48.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "CAMERA PERMISSIONS REQUIRED",
            color = StudioWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "AuraCam requires hardware Camera, Audio, and Storage permissions to capture, stack, and tone-map professional photographs directly onto your device.",
            color = Color.LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGrantRequest,
            colors = ButtonDefaults.buttonColors(containerColor = PremiumGold)
        ) {
            Text(
                text = "GRANT PERMISSIONS",
                color = Color.Black,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
