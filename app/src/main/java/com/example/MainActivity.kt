package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FilterVintage
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ui.theme.HonorCard
import com.example.ui.theme.HonorDarkBg
import com.example.ui.theme.HonorLevelGreen
import com.example.ui.theme.HonorOrange
import com.example.ui.theme.HonorRecRed
import com.example.ui.theme.HonorSurface
import com.example.ui.theme.HonorSurfaceTranslucent
import com.example.ui.theme.HonorTextPrimary
import com.example.ui.theme.HonorTextSecondary
import com.example.ui.theme.MyApplicationTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val volumeShutterEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                HonorCamApp(volumeShutterEvents = volumeShutterEvents)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeShutterEvents.tryEmit(Unit)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

private enum class AppScreen {
    CAMERA,
    SETTINGS,
    GALLERY
}

private data class ShutterStop(val label: String, val nanoseconds: Long)

private val PHOTO_SHUTTER_STOPS = listOf(
    ShutterStop("1/8000", 125_000L),
    ShutterStop("1/4000", 250_000L),
    ShutterStop("1/2000", 500_000L),
    ShutterStop("1/1000", 1_000_000L),
    ShutterStop("1/500", 2_000_000L),
    ShutterStop("1/250", 4_000_000L),
    ShutterStop("1/125", 8_000_000L),
    ShutterStop("1/60", 16_666_667L),
    ShutterStop("1/30", 33_333_333L),
    ShutterStop("1/15", 66_666_667L),
    ShutterStop("1/8", 125_000_000L),
    ShutterStop("1/4", 250_000_000L),
    ShutterStop("1/2", 500_000_000L),
    ShutterStop("1s", 1_000_000_000L)
)

private val VIDEO_SHUTTER_STOPS = PHOTO_SHUTTER_STOPS.filter { it.nanoseconds <= 16_666_667L }

@Composable
fun HonorCamApp(volumeShutterEvents: MutableSharedFlow<Unit>) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefs = remember { Prefs(context) }
    val showNotice: (String) -> Unit = remember {
        { msg ->
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    val cam = remember {
        Cam(
            context = context,
            prefs = prefs,
            scope = scope,
            onNotice = showNotice
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cam.release()
        }
    }

    var currentScreen by remember { mutableStateOf(AppScreen.CAMERA) }
    var showQuickPanel by remember { mutableStateOf(false) }
    var showFiltersBar by remember { mutableStateOf(false) }
    var showMoreGrid by remember { mutableStateOf(false) }

    // Runtime permissions
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val corePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPerm = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasAudioPerm = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            cam.bindCamera()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        prefs.locationTagPref.value = granted
        if (!granted) {
            showNotice("Location permission denied; Location tag disabled.")
        }
    }

    // ML Kit Document Scanner launcher
    val docScannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            if (scanResult != null) {
                scope.launch {
                    var savedCount = 0
                    scanResult.pages?.forEachIndexed { idx, page ->
                        Post.saveScannedJpegToDcim(context, page.imageUri, idx).onSuccess {
                            savedCount++
                        }
                    }
                    scanResult.pdf?.uri?.let { pdfUri ->
                        Post.saveScannedPdfToDocuments(context, pdfUri).onSuccess {
                            showNotice("Document saved to Documents/AiCam & $savedCount JPEG page(s) to DCIM/AiCam")
                        }.onFailure { err ->
                            showNotice("Failed saving PDF: ${err.localizedMessage}")
                        }
                    } ?: run {
                        if (savedCount > 0) {
                            showNotice("Saved $savedCount scanned page(s) to DCIM/AiCam")
                        }
                    }
                    cam.refreshGallery()
                }
            }
        } else {
            showNotice("Document scan cancelled.")
        }
    }

    val launchDocumentScanner: () -> Unit = {
        if (activity == null) {
            showNotice("Activity unavailable for Document Scanner.")
        } else {
            runCatching {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                val scanner = GmsDocumentScanning.getClient(options)
                scanner.getStartScanIntent(activity)
                    .addOnSuccessListener { intentSender ->
                        docScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    }
                    .addOnFailureListener { e ->
                        showNotice("ML Kit Document Scanner unavailable: ${e.localizedMessage}")
                    }
            }.onFailure { e ->
                showNotice("Document Scanner failed to start: ${e.localizedMessage}")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm || !hasAudioPerm) {
            corePermissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    // Hardware volume up/down shutter trigger
    LaunchedEffect(currentScreen) {
        volumeShutterEvents.collect {
            if (currentScreen == AppScreen.CAMERA && hasCameraPerm) {
                cam.triggerShutter(onLaunchDocScanner = launchDocumentScanner)
            }
        }
    }

    // Rebind camera whenever structural capture settings change
    val activeMode = prefs.modePref.value
    val proIsVideo = prefs.proIsVideoPref.value
    val aspectRatio = prefs.aspectRatioPref.value
    val photoHdr = prefs.hdrPref.value
    val videoSpec = prefs.videoSpecPref.value
    val videoProfile = prefs.videoProfilePref.value
    val videoBitrate = prefs.videoBitrateMbpsPref.value
    val autoFps = prefs.autoFpsPref.value
    val lockWb = prefs.lockWbPref.value

    LaunchedEffect(
        hasCameraPerm,
        activeMode,
        proIsVideo,
        aspectRatio,
        photoHdr,
        videoSpec,
        videoProfile,
        videoBitrate,
        autoFps,
        lockWb
    ) {
        if (hasCameraPerm && activeMode != Mode.SCAN_DOCUMENT) {
            cam.bindCamera()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HonorDarkBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 140.dp)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(HonorDarkBg)
        ) {
            when (currentScreen) {
                AppScreen.CAMERA -> {
                    CameraMainScreen(
                        cam = cam,
                        prefs = prefs,
                        hasCameraPerm = hasCameraPerm,
                        lifecycleOwner = lifecycleOwner,
                        showQuickPanel = showQuickPanel,
                        onToggleQuickPanel = { showQuickPanel = !showQuickPanel },
                        showFiltersBar = showFiltersBar,
                        onToggleFiltersBar = { showFiltersBar = !showFiltersBar },
                        showMoreGrid = showMoreGrid,
                        onSetShowMoreGrid = { showMoreGrid = it },
                        onOpenSettings = {
                            showQuickPanel = false
                            currentScreen = AppScreen.SETTINGS
                        },
                        onOpenGallery = {
                            cam.refreshGallery()
                            currentScreen = AppScreen.GALLERY
                        },
                        onRequestCameraPermission = {
                            corePermissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        onLaunchDocumentScanner = launchDocumentScanner
                    )
                }

                AppScreen.SETTINGS -> {
                    BackHandler { currentScreen = AppScreen.CAMERA }
                    SettingsScreen(
                        prefs = prefs,
                        cam = cam,
                        onBack = { currentScreen = AppScreen.CAMERA },
                        onRequestLocationPermission = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        onShowNotice = showNotice
                    )
                }

                AppScreen.GALLERY -> {
                    BackHandler { currentScreen = AppScreen.CAMERA }
                    MediaGalleryScreen(
                        mediaList = cam.recentMedia,
                        onBack = { currentScreen = AppScreen.CAMERA },
                        onDeleteMedia = { item ->
                            runCatching {
                                context.contentResolver.delete(item.uri, null, null)
                                showNotice("Deleted ${item.displayName}")
                                cam.refreshGallery()
                            }.onFailure {
                                showNotice("Could not delete file: ${it.localizedMessage}")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraMainScreen(
    cam: Cam,
    prefs: Prefs,
    hasCameraPerm: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    showQuickPanel: Boolean,
    onToggleQuickPanel: () -> Unit,
    showFiltersBar: Boolean,
    onToggleFiltersBar: () -> Unit,
    showMoreGrid: Boolean,
    onSetShowMoreGrid: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onLaunchDocumentScanner: () -> Unit
) {
    val context = LocalContext.current
    val mode = prefs.modePref.value
    val isVideo = mode.isVideoCapture(prefs.proIsVideoPref.value)

    // Accelerometer roll angle for the level indicator
    var rollDegrees by remember { mutableFloatStateOf(0f) }
    val levelEnabled = prefs.levelPref.value

    DisposableEffect(levelEnabled) {
        if (!levelEnabled) {
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val v = event?.values ?: return
                if (v.size >= 2) {
                    val ax = v[0]
                    val ay = v[1]
                    val angle = Math.toDegrees(atan2(ax.toDouble(), ay.toDouble())).toFloat()
                    rollDegrees = rollDegrees * 0.8f + angle * 0.2f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // Tap-to-focus ring coordinates
    var focusRingOffset by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(focusRingOffset) {
        if (focusRingOffset != null) {
            delay(1400L)
            focusRingOffset = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(HonorDarkBg)
    ) {
        val screenWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. HONOR TOP BAR
            HonorTopBar(
                prefs = prefs,
                cam = cam,
                isVideo = isVideo,
                showFiltersBar = showFiltersBar,
                showQuickPanel = showQuickPanel,
                onToggleFiltersBar = onToggleFiltersBar,
                onToggleQuickPanel = onToggleQuickPanel
            )

            // 2. VIEWFINDER AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (!hasCameraPerm) {
                    PermissionRequiredCard(onRequest = onRequestCameraPermission)
                } else if (mode == Mode.SCAN_DOCUMENT) {
                    DocumentScannerCard(onLaunch = onLaunchDocumentScanner)
                } else {
                    val activeRatio =
                        if (isVideo) AspectRatioOption.RATIO_16_9 else prefs.aspectRatioPref.value

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(activeRatio.widthToHeightPortrait)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .pointerInput(mode) {
                                detectTransformGestures { _, _, zoomChange, _ ->
                                    if (zoomChange != 1f) {
                                        cam.onPinchZoom(zoomChange)
                                    }
                                }
                            }
                            .pointerInput(mode) {
                                detectTapGestures { offset ->
                                    if (showQuickPanel) {
                                        onToggleQuickPanel()
                                    } else if (showMoreGrid) {
                                        onSetShowMoreGrid(false)
                                    } else {
                                        focusRingOffset = offset
                                        cam.tapToFocus(offset.x, offset.y)
                                    }
                                }
                            }
                    ) {
                        val previewFilter = prefs.filterPref.value
                        val isUnderwater = mode == Mode.UNDERWATER
                        val useLogPreviewAssist =
                            isVideo &&
                                prefs.videoProfilePref.value == VideoProfile.LOG &&
                                prefs.logPreviewAssistPref.value

                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("camera_preview_view"),
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    cam.attach(lifecycleOwner, this)
                                }
                            },
                            update = { pv ->
                                applyPreviewTextureColorMatrix(
                                    previewView = pv,
                                    filter = if (!isVideo) previewFilter else PhotoFilter.ORIGINAL,
                                    isUnderwater = isUnderwater,
                                    isLogPreviewAssist = useLogPreviewAssist
                                )
                            }
                        )

                        // Pro / Video Luma Assist Overlay Bitmap (Zebra & Focus Peaking)
                        val assistOverlay = cam.assistData.overlayBitmap
                        if ((mode == Mode.VIDEO || mode == Mode.PRO) &&
                            (prefs.zebraPref.value || prefs.peakingPref.value) &&
                            assistOverlay != null
                        ) {
                            Image(
                                bitmap = assistOverlay.asImageBitmap(),
                                contentDescription = "Pro luma assist overlay",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Rule-of-thirds Gridlines
                        if (prefs.gridlinesPref.value) {
                            RuleOfThirdsGridOverlay()
                        }

                        // Accelerometer Level Line
                        if (prefs.levelPref.value) {
                            LevelIndicatorOverlay(rollDegrees = rollDegrees)
                        }

                        // Tap-to-focus Ring
                        focusRingOffset?.let { pt ->
                            TapToFocusRing(offset = pt)
                        }

                        // 64-bin Live Histogram (Video / Pro)
                        if ((mode == Mode.VIDEO || mode == Mode.PRO) &&
                            prefs.histogramPref.value &&
                            cam.assistData.histogram.isNotEmpty()
                        ) {
                            HistogramOverlayCard(
                                bins = cam.assistData.histogram,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )
                        }

                        // Active mode / profile badges at top-end of viewfinder
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isVideo && prefs.videoProfilePref.value != VideoProfile.STANDARD) {
                                ModeInfoBadge(text = prefs.videoProfilePref.value.label.uppercase())
                            }
                            if (prefs.aiEnabledPref.value && !isVideo) {
                                ModeInfoBadge(text = "AI ENHANCE")
                            }
                            if (mode == Mode.SUPER_MACRO) {
                                ModeInfoBadge(text = "MACRO LOCKED")
                            }
                            if (mode == Mode.HIGH_RES) {
                                ModeInfoBadge(text = "MAX RES")
                            }
                        }

                        // Recording timer pill
                        if (cam.isRecording) {
                            RecordingTimerPill(
                                seconds = cam.recordingDurationSec,
                                isMuted = prefs.muteAudioPref.value,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp)
                            )
                        }

                        // Countdown timer number
                        if (cam.countdownRemaining > 0) {
                            Text(
                                text = cam.countdownRemaining.toString(),
                                color = HonorOrange,
                                fontSize = 88.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // AI Enhancing spinner banner
                        if (cam.isAiProcessing) {
                            Surface(
                                color = HonorSurfaceTranslucent,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = HonorOrange,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "AI Enhancing Photo...",
                                        color = HonorTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Bottom of viewfinder: Zoom pills + optional Filter Strip + Pro HUD
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Zoom pills (0.6, 1, 1.5, 2, 5 — only supported)
                            ZoomPillsRow(
                                pills = cam.supportedZoomPills,
                                currentZoom = cam.currentZoomRatio,
                                onSelectZoom = { cam.setZoom(it) }
                            )

                            // Photo Filters Strip
                            AnimatedVisibility(
                                visible = showFiltersBar && !isVideo,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                PhotoFilterSelectorBar(
                                    selected = prefs.filterPref.value,
                                    onSelect = { prefs.filterPref.value = it }
                                )
                            }

                            // PRO Mode Live Controls HUD
                            if (mode == Mode.PRO) {
                                ProControlsHud(
                                    cam = cam,
                                    prefs = prefs,
                                    isVideo = isVideo
                                )
                            }
                        }
                    }
                }

                // Rounded Translucent Quick-Settings Panel
                androidx.compose.animation.AnimatedVisibility(
                    visible = showQuickPanel,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    QuickSettingsPanel(
                        prefs = prefs,
                        cam = cam,
                        isVideo = isVideo,
                        isProOrVideo = (mode == Mode.PRO || mode == Mode.VIDEO),
                        onOpenSettings = onOpenSettings
                    )
                }

                // MORE Modes Overlay Grid
                androidx.compose.animation.AnimatedVisibility(
                    visible = showMoreGrid,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    MoreModesGridModal(
                        currentMode = mode,
                        onSelectSubMode = { subMode ->
                            prefs.lastMoreSubModePref.value = subMode
                            prefs.modePref.value = subMode
                            onSetShowMoreGrid(false)
                            if (subMode == Mode.SCAN_DOCUMENT) {
                                onLaunchDocumentScanner()
                            }
                        },
                        onDismiss = { onSetShowMoreGrid(false) }
                    )
                }
            }

            // 3. BOTTOM CONTROLS: Mode strip + Shutter row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HonorDarkBg)
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HonorModeStrip(
                    currentMode = mode,
                    showMoreGrid = showMoreGrid,
                    onSelectMode = { chosen ->
                        if (chosen == Mode.MORE) {
                            onSetShowMoreGrid(!showMoreGrid)
                        } else {
                            onSetShowMoreGrid(false)
                            prefs.modePref.value = chosen
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                HonorBottomShutterRow(
                    latestMedia = cam.latestMedia,
                    isVideo = isVideo,
                    isRecording = cam.isRecording,
                    isCapturingPhoto = cam.isCapturingPhoto,
                    onOpenGallery = onOpenGallery,
                    onShutterClick = {
                        cam.triggerShutter(onLaunchDocScanner = onLaunchDocumentScanner)
                    },
                    onFlipCamera = { cam.flipCamera() }
                )
            }
        }

        // 4. OPTIONAL DRAGGABLE FLOATING SHUTTER
        if (prefs.floatingShutterPref.value && hasCameraPerm) {
            val btnSizePx = with(LocalDensity.current) { 58.dp.toPx() }
            val maxX = (screenWidthPx - btnSizePx).coerceAtLeast(1f)
            val maxY = (screenHeightPx - btnSizePx).coerceAtLeast(1f)

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (prefs.floatingShutterXPref.value.coerceIn(0.02f, 0.95f) * maxX).roundToInt(),
                            y = (prefs.floatingShutterYPref.value.coerceIn(0.08f, 0.85f) * maxY).roundToInt()
                        )
                    }
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(2.5.dp, HonorOrange, CircleShape)
                    .pointerInput(maxX, maxY) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curX = prefs.floatingShutterXPref.value * maxX
                            val curY = prefs.floatingShutterYPref.value * maxY
                            prefs.floatingShutterXPref.value =
                                ((curX + dragAmount.x) / maxX).coerceIn(0.02f, 0.95f)
                            prefs.floatingShutterYPref.value =
                                ((curY + dragAmount.y) / maxY).coerceIn(0.08f, 0.85f)
                        }
                    }
                    .clickable {
                        cam.triggerShutter(onLaunchDocScanner = onLaunchDocumentScanner)
                    }
                    .testTag("floating_shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isVideo) HonorRecRed else Color.White)
                )
            }
        }
    }
}

/**
 * Applies a ColorMatrix to the underlying TextureView inside PreviewView only,
 * supporting both photo filters/underwater preview and Video Log Preview Assist.
 */
private fun applyPreviewTextureColorMatrix(
    previewView: PreviewView,
    filter: PhotoFilter,
    isUnderwater: Boolean,
    isLogPreviewAssist: Boolean
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val textureView = findTextureView(previewView) ?: previewView
    val needEffect =
        filter != PhotoFilter.ORIGINAL || isUnderwater || isLogPreviewAssist
    if (!needEffect) {
        textureView.setRenderEffect(null)
        return
    }
    val matrix = ColorMatrix()
    if (isLogPreviewAssist) {
        matrix.postConcat(Post.logPreviewAssistMatrix())
    }
    if (isUnderwater) {
        matrix.postConcat(Post.underwaterRedBoostMatrix())
    }
    if (filter != PhotoFilter.ORIGINAL) {
        matrix.postConcat(Post.filterColorMatrix(filter))
    }
    textureView.setRenderEffect(
        RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix))
    )
}

private fun findTextureView(view: View): TextureView? {
    if (view is TextureView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findTextureView(view.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

@Composable
private fun HonorTopBar(
    prefs: Prefs,
    cam: Cam,
    isVideo: Boolean,
    showFiltersBar: Boolean,
    showQuickPanel: Boolean,
    onToggleFiltersBar: () -> Unit,
    onToggleQuickPanel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Flash button (Off/Auto/On for photo, Off/Torch for video)
        val flashActive = if (isVideo) {
            prefs.videoTorchPref.value
        } else {
            prefs.flashPhotoPref.value != FlashPhotoMode.OFF
        }
        val flashIcon = if (isVideo) {
            if (prefs.videoTorchPref.value) Icons.Default.FlashOn else Icons.Default.FlashOff
        } else {
            when (prefs.flashPhotoPref.value) {
                FlashPhotoMode.OFF -> Icons.Default.FlashOff
                FlashPhotoMode.AUTO -> Icons.Default.FlashAuto
                FlashPhotoMode.ON -> Icons.Default.FlashOn
            }
        }
        IconButton(
            onClick = { cam.toggleFlashOrTorch() },
            modifier = Modifier.testTag("top_flash_button")
        ) {
            Icon(
                imageVector = flashIcon,
                contentDescription = "Flash mode",
                tint = if (flashActive) HonorOrange else HonorTextPrimary
            )
        }

        // 2. Filters button
        val filterActive = prefs.filterPref.value != PhotoFilter.ORIGINAL || showFiltersBar
        IconButton(
            onClick = onToggleFiltersBar,
            modifier = Modifier.testTag("top_filters_button")
        ) {
            Icon(
                imageVector = Icons.Default.FilterVintage,
                contentDescription = "Filters",
                tint = if (filterActive && !isVideo) HonorOrange else HonorTextPrimary
            )
        }

        // 3. HDR toggle (photo) OR "1080 · 60" button that cycles video specs
        if (isVideo) {
            Surface(
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (!cam.isRecording) {
                            prefs.videoSpecPref.value = prefs.videoSpecPref.value.next()
                        }
                    }
                    .testTag("top_video_spec_button")
            ) {
                Text(
                    text = prefs.videoSpecPref.value.topBarBadge,
                    color = HonorOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            val hdrOn = prefs.hdrPref.value
            Surface(
                color = if (hdrOn) HonorOrange.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { prefs.hdrPref.value = !hdrOn }
                    .testTag("top_hdr_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HdrOn,
                        contentDescription = "HDR",
                        tint = if (hdrOn) HonorOrange else HonorTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (hdrOn) "ON" else "OFF",
                        color = if (hdrOn) HonorOrange else HonorTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. "More" quick-settings panel toggle button
        IconButton(
            onClick = onToggleQuickPanel,
            modifier = Modifier.testTag("top_more_quick_panel_button")
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "Quick settings panel",
                tint = if (showQuickPanel) HonorOrange else HonorTextPrimary
            )
        }
    }
}

@Composable
private fun QuickSettingsPanel(
    prefs: Prefs,
    cam: Cam,
    isVideo: Boolean,
    isProOrVideo: Boolean,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = HonorSurfaceTranslucent,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .testTag("quick_settings_panel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Timer, Ratio, Specifications (video), Audio (mute)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val timerVal = prefs.timerSecPref.value
                QuickPanelCircleButton(
                    icon = Icons.Default.Timer,
                    label = if (timerVal == 0) "Timer" else "${timerVal}s",
                    active = timerVal > 0,
                    onClick = { prefs.cycleTimer() }
                )

                QuickPanelCircleButton(
                    icon = Icons.Default.AspectRatio,
                    label = "Ratio ${prefs.aspectRatioPref.value.label}",
                    active = prefs.aspectRatioPref.value != AspectRatioOption.RATIO_4_3,
                    onClick = {
                        prefs.aspectRatioPref.value = prefs.aspectRatioPref.value.next()
                    }
                )

                QuickPanelCircleButton(
                    icon = Icons.Default.VideoSettings,
                    label = prefs.videoSpecPref.value.topBarBadge,
                    active = isVideo,
                    onClick = {
                        if (!cam.isRecording) {
                            prefs.videoSpecPref.value = prefs.videoSpecPref.value.next()
                        }
                    }
                )

                val muted = prefs.muteAudioPref.value
                QuickPanelCircleButton(
                    icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    label = if (muted) "Muted" else "Audio",
                    active = muted,
                    onClick = { cam.setMuteAudio(!muted) }
                )
            }

            // Row 2: Gridlines, Level, Settings gear (plus Histogram/Zebra/Peaking in Pro/Video)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickPanelCircleButton(
                    icon = Icons.Default.GridOn,
                    label = "Gridlines",
                    active = prefs.gridlinesPref.value,
                    onClick = { prefs.gridlinesPref.value = !prefs.gridlinesPref.value }
                )

                QuickPanelCircleButton(
                    icon = Icons.Default.Straighten,
                    label = "Level",
                    active = prefs.levelPref.value,
                    onClick = { prefs.levelPref.value = !prefs.levelPref.value }
                )

                if (isProOrVideo) {
                    QuickPanelCircleButton(
                        icon = Icons.Default.BarChart,
                        label = "Histogram",
                        active = prefs.histogramPref.value,
                        onClick = { prefs.histogramPref.value = !prefs.histogramPref.value }
                    )

                    QuickPanelCircleButton(
                        icon = Icons.Default.Texture,
                        label = "Zebra",
                        active = prefs.zebraPref.value,
                        onClick = { prefs.zebraPref.value = !prefs.zebraPref.value }
                    )
                } else {
                    QuickPanelCircleButton(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        active = false,
                        onClick = onOpenSettings
                    )
                }
            }

            if (isProOrVideo) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickPanelCircleButton(
                        icon = Icons.Default.CenterFocusStrong,
                        label = "Peaking",
                        active = prefs.peakingPref.value,
                        onClick = { prefs.peakingPref.value = !prefs.peakingPref.value }
                    )

                    QuickPanelCircleButton(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        active = false,
                        onClick = onOpenSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPanelCircleButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (active) HonorOrange else Color.White.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.Black else HonorTextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (active) HonorOrange else HonorTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RuleOfThirdsGridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val gridColor = Color.White.copy(alpha = 0.32f)
        val stroke = 1.dp.toPx()

        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke)
        drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), stroke)
        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), stroke)
        drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), stroke)
    }
}

@Composable
private fun LevelIndicatorOverlay(rollDegrees: Float) {
    val isLevel = abs(rollDegrees) <= 1.8f || abs(abs(rollDegrees) - 90f) <= 1.8f || abs(abs(rollDegrees) - 180f) <= 1.8f
    val lineColor = if (isLevel) HonorLevelGreen else Color.White.copy(alpha = 0.75f)
    val animatedAngle by animateFloatAsState(targetValue = rollDegrees, label = "level_angle")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfLen = 58.dp.toPx()
        val gap = 14.dp.toPx()
        val rad = Math.toRadians(animatedAngle.toDouble())
        val cosA = kotlin.math.cos(rad).toFloat()
        val sinA = kotlin.math.sin(rad).toFloat()

        // Horizontal reference ticks
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(center.x - halfLen - 16.dp.toPx(), center.y),
            end = Offset(center.x - halfLen - 4.dp.toPx(), center.y),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(center.x + halfLen + 4.dp.toPx(), center.y),
            end = Offset(center.x + halfLen + 16.dp.toPx(), center.y),
            strokeWidth = 1.5.dp.toPx()
        )

        // Tilting level bar (left & right segments)
        drawLine(
            color = lineColor,
            start = Offset(center.x - halfLen * cosA, center.y - halfLen * sinA),
            end = Offset(center.x - gap * cosA, center.y - gap * sinA),
            strokeWidth = if (isLevel) 2.8.dp.toPx() else 1.8.dp.toPx()
        )
        drawLine(
            color = lineColor,
            start = Offset(center.x + gap * cosA, center.y + gap * sinA),
            end = Offset(center.x + halfLen * cosA, center.y + halfLen * sinA),
            strokeWidth = if (isLevel) 2.8.dp.toPx() else 1.8.dp.toPx()
        )
        drawCircle(
            color = lineColor,
            radius = 3.dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun TapToFocusRing(offset: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = 34.dp.toPx()
        drawCircle(
            color = HonorOrange,
            radius = radius,
            center = offset,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = HonorOrange.copy(alpha = 0.5f),
            radius = 4.dp.toPx(),
            center = offset
        )
    }
}

@Composable
private fun HistogramOverlayCard(
    bins: IntArray,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .size(width = 132.dp, height = 68.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            if (bins.isEmpty()) return@Canvas
            val maxBin = (bins.maxOrNull() ?: 1).coerceAtLeast(1)
            val barW = size.width / bins.size.toFloat()
            for (i in bins.indices) {
                val norm = (bins[i].toFloat() / maxBin.toFloat()).coerceIn(0f, 1f)
                val barH = norm * size.height
                val barColor = if (i >= 60) HonorOrange else Color.White.copy(alpha = 0.85f)
                drawRect(
                    color = barColor,
                    topLeft = Offset(i * barW, size.height - barH),
                    size = Size(barW.coerceAtLeast(1f), barH)
                )
            }
        }
    }
}

@Composable
private fun ModeInfoBadge(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, HonorOrange.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            color = HonorOrange,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun RecordingTimerPill(
    seconds: Long,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val mins = seconds / 60
    val secs = seconds % 60
    val formatted = String.format(Locale.US, "%02d:%02d", mins, secs)

    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(HonorRecRed)
            )
            Text(
                text = formatted,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (isMuted) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Muted",
                    tint = HonorOrange,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ZoomPillsRow(
    pills: List<Float>,
    currentZoom: Float,
    onSelectZoom: (Float) -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.52f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pills.forEach { pill ->
                val isSelected = abs(currentZoom - pill) < 0.15f
                val label = if (pill % 1f == 0f) {
                    if (isSelected) "${pill.toInt()}x" else "${pill.toInt()}"
                } else {
                    if (isSelected) "${pill}x" else "$pill"
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) HonorOrange else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable { onSelectZoom(pill) }
                        .testTag("zoom_pill_$pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoFilterSelectorBar(
    selected: PhotoFilter,
    onSelect: (PhotoFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(PhotoFilter.entries) { filter ->
            val isSelected = filter == selected
            Surface(
                color = if (isSelected) HonorOrange else Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(filter) }
            ) {
                Text(
                    text = filter.label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun ProControlsHud(
    cam: Cam,
    prefs: Prefs,
    isVideo: Boolean
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0=ISO, 1=S, 2=WB, 3=AF

    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active parameter slider/chips
            when (activeTab) {
                0 -> {
                    // Log-scale ISO slider from sensorIsoMin up to effectiveIsoMax (cap 409600)
                    val minIso = cam.sensorIsoMin.coerceAtLeast(25)
                    val maxIso = cam.effectiveIsoMax.coerceAtLeast(minIso + 100)
                    val lnMin = ln(minIso.toDouble())
                    val lnMax = ln(maxIso.toDouble())
                    val sliderFraction = ((ln(cam.proIsoValue.coerceIn(minIso, maxIso).toDouble()) - lnMin) /
                        (lnMax - lnMin)).toFloat().coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProAutoChip(
                            isAuto = cam.proIsoAuto,
                            onToggle = {
                                cam.proIsoAuto = !cam.proIsoAuto
                                cam.applyLiveCamera2Controls()
                            }
                        )
                        Slider(
                            value = sliderFraction,
                            onValueChange = { frac ->
                                cam.proIsoAuto = false
                                val rawIso = exp(lnMin + frac.toDouble() * (lnMax - lnMin)).roundToInt()
                                cam.proIsoValue = rawIso.coerceIn(minIso, maxIso)
                                cam.applyLiveCamera2Controls()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = HonorOrange,
                                activeTrackColor = HonorOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (cam.proIsoAuto) "AUTO" else "ISO ${cam.proIsoValue}",
                            color = HonorOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(74.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                1 -> {
                    // Shutter slider: 1/8000 to 1s for photo; capped at 1/60 in video
                    val stops = if (isVideo) VIDEO_SHUTTER_STOPS else PHOTO_SHUTTER_STOPS
                    val curIdx = stops.indexOfFirst { it.nanoseconds >= cam.proShutterNs }
                        .let { if (it == -1) stops.lastIndex else it }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProAutoChip(
                            isAuto = cam.proShutterAuto,
                            onToggle = {
                                cam.proShutterAuto = !cam.proShutterAuto
                                cam.applyLiveCamera2Controls()
                            }
                        )
                        Slider(
                            value = curIdx.toFloat(),
                            valueRange = 0f..(stops.lastIndex.coerceAtLeast(1)).toFloat(),
                            steps = (stops.size - 2).coerceAtLeast(0),
                            onValueChange = { v ->
                                val idx = v.roundToInt().coerceIn(0, stops.lastIndex)
                                cam.proShutterAuto = false
                                cam.proShutterNs = stops[idx].nanoseconds
                                cam.applyLiveCamera2Controls()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = HonorOrange,
                                activeTrackColor = HonorOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (cam.proShutterAuto) "AUTO" else stops[curIdx].label,
                            color = HonorOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                2 -> {
                    // White Balance presets (Auto, Incandescent, Fluorescent, Daylight, Cloudy)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WbPreset.entries.forEach { preset ->
                            val selected = cam.proWbPreset == preset
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    cam.proWbPreset = preset
                                    cam.applyLiveCamera2Controls()
                                },
                                label = {
                                    Text(
                                        text = preset.label,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HonorOrange,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }

                3 -> {
                    // Manual Focus (LENS_FOCUS_DISTANCE)
                    val maxDiopters = cam.minFocusDistanceDiopters.coerceAtLeast(1f)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProAutoChip(
                            isAuto = cam.proFocusAuto,
                            onToggle = {
                                cam.proFocusAuto = !cam.proFocusAuto
                                cam.applyLiveCamera2Controls()
                            }
                        )
                        Slider(
                            value = (cam.proFocusDistance / maxDiopters).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                cam.proFocusAuto = false
                                cam.proFocusDistance = (frac * maxDiopters).coerceIn(0f, maxDiopters)
                                cam.applyLiveCamera2Controls()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = HonorOrange,
                                activeTrackColor = HonorOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (cam.proFocusAuto) {
                                "AF"
                            } else {
                                String.format(Locale.US, "%.1f dpt", cam.proFocusDistance)
                            },
                            color = HonorOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(64.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // Bottom row of Pro HUD: ISO / S / WB / AF tabs + PRO Photo/Video switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val shutterLabel = if (cam.proShutterAuto) {
                    "AUTO"
                } else {
                    val stops = if (isVideo) VIDEO_SHUTTER_STOPS else PHOTO_SHUTTER_STOPS
                    val idx = stops.indexOfFirst { it.nanoseconds >= cam.proShutterNs }
                        .let { if (it == -1) stops.lastIndex else it }
                    stops[idx].label
                }

                ProParamButton(
                    title = "ISO",
                    value = if (cam.proIsoAuto) "AUTO" else cam.proIsoValue.toString(),
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 }
                )
                ProParamButton(
                    title = "S",
                    value = shutterLabel,
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 }
                )
                ProParamButton(
                    title = "WB",
                    value = cam.proWbPreset.label,
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 }
                )
                ProParamButton(
                    title = "FOCUS",
                    value = if (cam.proFocusAuto) "AF" else "MF",
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 }
                )

                // PRO Photo / Video switch
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (!cam.isRecording) {
                                prefs.proIsVideoPref.value = !prefs.proIsVideoPref.value
                            }
                        }
                        .testTag("pro_photo_video_switch")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "PHOTO",
                            color = if (!isVideo) HonorOrange else HonorTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "/",
                            color = HonorTextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "VIDEO",
                            color = if (isVideo) HonorOrange else HonorTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProAutoChip(
    isAuto: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = if (isAuto) HonorOrange else Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
    ) {
        Text(
            text = "AUTO",
            color = if (isAuto) Color.Black else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ProParamButton(
    title: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            color = if (selected) HonorOrange else HonorTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = if (selected) HonorOrange else HonorTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun MoreModesGridModal(
    currentMode: Mode,
    onSelectSubMode: (Mode) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = HonorSurfaceTranslucent,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .testTag("more_modes_grid")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MORE MODES",
                    color = HonorTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close MORE grid",
                        tint = HonorTextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MoreModeTile(
                    icon = Icons.Default.Water,
                    title = Mode.UNDERWATER.title,
                    selected = currentMode == Mode.UNDERWATER,
                    onClick = { onSelectSubMode(Mode.UNDERWATER) }
                )
                MoreModeTile(
                    icon = Icons.Default.FilterVintage,
                    title = Mode.SUPER_MACRO.title,
                    selected = currentMode == Mode.SUPER_MACRO,
                    onClick = { onSelectSubMode(Mode.SUPER_MACRO) }
                )
                MoreModeTile(
                    icon = Icons.Default.HighQuality,
                    title = Mode.HIGH_RES.title,
                    selected = currentMode == Mode.HIGH_RES,
                    onClick = { onSelectSubMode(Mode.HIGH_RES) }
                )
                MoreModeTile(
                    icon = Icons.Default.DocumentScanner,
                    title = Mode.SCAN_DOCUMENT.title,
                    selected = currentMode == Mode.SCAN_DOCUMENT,
                    onClick = { onSelectSubMode(Mode.SCAN_DOCUMENT) }
                )
            }
        }
    }
}

@Composable
private fun MoreModeTile(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) HonorOrange else Color.White.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) Color.Black else HonorTextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = if (selected) HonorOrange else HonorTextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun HonorModeStrip(
    currentMode: Mode,
    showMoreGrid: Boolean,
    onSelectMode: (Mode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Mode.mainStripModes.forEach { stripMode ->
            val isMoreSlot = stripMode == Mode.MORE
            val isSelected = if (isMoreSlot) {
                currentMode.isMoreSubMode || showMoreGrid
            } else {
                currentMode == stripMode && !showMoreGrid
            }
            val displayLabel = if (isMoreSlot && currentMode.isMoreSubMode) {
                currentMode.title
            } else {
                stripMode.title
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectMode(stripMode) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("mode_tab_${stripMode.name}")
            ) {
                Text(
                    text = displayLabel,
                    color = if (isSelected) HonorOrange else HonorTextPrimary.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) HonorOrange else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun HonorBottomShutterRow(
    latestMedia: CapturedMedia?,
    isVideo: Boolean,
    isRecording: Boolean,
    isCapturingPhoto: Boolean,
    onOpenGallery: () -> Unit,
    onShutterClick: () -> Unit,
    onFlipCamera: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Gallery thumbnail (latest media, opens viewer)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(HonorCard)
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable(onClick = onOpenGallery)
                .testTag("gallery_thumbnail_button"),
            contentAlignment = Alignment.Center
        ) {
            if (latestMedia != null) {
                AsyncImage(
                    model = latestMedia.uri,
                    contentDescription = "Open latest media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Open gallery",
                    tint = HonorTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. Shutter button (white for photo; red, and a square while recording, for video)
        Box(
            modifier = Modifier
                .size(78.dp)
                .clip(CircleShape)
                .border(4.dp, Color.White, CircleShape)
                .clickable(enabled = !isCapturingPhoto, onClick = onShutterClick)
                .testTag("main_shutter_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isVideo) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(HonorRecRed)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(HonorRecRed)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCapturingPhoto) Color.White.copy(alpha = 0.5f) else Color.White
                        )
                )
            }
        }

        // 3. Front/back flip (disabled while recording)
        IconButton(
            onClick = onFlipCamera,
            enabled = !isRecording,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(HonorCard.copy(alpha = if (isRecording) 0.35f else 0.9f))
                .testTag("flip_camera_button")
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Flip camera",
                tint = if (isRecording) HonorTextSecondary.copy(alpha = 0.4f) else HonorTextPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun PermissionRequiredCard(onRequest: () -> Unit) {
    Surface(
        color = HonorCard,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = HonorOrange,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Camera & Microphone Access",
                color = HonorTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Honor Cam requires Camera and Microphone permissions to capture photos and record videos.",
                color = HonorTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HonorOrange,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("grant_permissions_button")
            ) {
                Text(text = "Grant Permissions", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DocumentScannerCard(onLaunch: () -> Unit) {
    Surface(
        color = HonorCard,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DocumentScanner,
                contentDescription = null,
                tint = HonorOrange,
                modifier = Modifier.size(54.dp)
            )
            Text(
                text = "ML Kit Document Scanner",
                color = HonorTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Scan single or multi-page documents with automatic edge crop. JPEGs save to DCIM/AiCam and PDFs save to Documents/AiCam.",
                color = HonorTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HonorOrange,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("start_doc_scan_button")
            ) {
                Text(text = "Start Document Scan", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// SETTINGS SCREEN (HONOR Dark List with Switches)
// ============================================================================

@Composable
private fun SettingsScreen(
    prefs: Prefs,
    cam: Cam,
    onBack: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onShowNotice: (String) -> Unit
) {
    val context = LocalContext.current
    var showApiKey by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HonorDarkBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HonorTextPrimary
                )
            }
            Text(
                text = "Camera settings",
                color = HonorTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. PHOTO
            item {
                SettingsSectionCard(title = "PHOTO") {
                    SettingsChoiceRow(
                        title = "Aspect ratio",
                        subtitle = prefs.aspectRatioPref.value.label,
                        options = AspectRatioOption.entries.map { it.label },
                        selectedIndex = prefs.aspectRatioPref.value.ordinal,
                        onSelectIndex = { prefs.aspectRatioPref.value = AspectRatioOption.entries[it] }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "HDR",
                        subtitle = "High dynamic range photo extension",
                        checked = prefs.hdrPref.value,
                        onCheckedChange = { prefs.hdrPref.value = it }
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        title = "Filter",
                        subtitle = prefs.filterPref.value.label,
                        options = PhotoFilter.entries.map { it.label },
                        selectedIndex = prefs.filterPref.value.ordinal,
                        onSelectIndex = { prefs.filterPref.value = PhotoFilter.entries[it] }
                    )
                }
            }

            // 2. AI CAMERA
            item {
                SettingsSectionCard(title = "AI CAMERA") {
                    SettingsSwitchRow(
                        title = "AI camera",
                        subtitle = "Enhance photos via OpenAI /v1/images/edits (2048px JPEG)",
                        checked = prefs.aiEnabledPref.value,
                        onCheckedChange = { prefs.aiEnabledPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Keep original photo with AI Enhance",
                        subtitle = "If off, deletes the original photo after saving _AI.jpg",
                        checked = prefs.aiKeepOriginalPref.value,
                        onCheckedChange = { prefs.aiKeepOriginalPref.value = it }
                    )
                    SettingsDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = prefs.aiApiKeyPref.value,
                            onValueChange = { prefs.aiApiKeyPref.value = it },
                            label = { Text("OpenAI API Key") },
                            placeholder = { Text("sk-...") },
                            singleLine = true,
                            visualTransformation = if (showApiKey) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = "Toggle API key visibility"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_api_key_input")
                        )

                        OutlinedTextField(
                            value = prefs.aiModelPref.value,
                            onValueChange = { prefs.aiModelPref.value = it },
                            label = { Text("Model") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_ai_model_input")
                        )

                        OutlinedTextField(
                            value = prefs.aiPromptPref.value,
                            onValueChange = { prefs.aiPromptPref.value = it },
                            label = { Text("Enhancement Prompt") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_ai_prompt_input")
                        )
                    }
                }
            }

            // 3. VIDEO
            item {
                SettingsSectionCard(title = "VIDEO") {
                    SettingsChoiceRow(
                        title = "Specifications",
                        subtitle = prefs.videoSpecPref.value.label,
                        options = VideoSpec.entries.map { it.label },
                        selectedIndex = prefs.videoSpecPref.value.ordinal,
                        onSelectIndex = { prefs.videoSpecPref.value = VideoSpec.entries[it] }
                    )
                    SettingsDivider()
                    SettingsChoiceRow(
                        title = "Video profile",
                        subtitle = "${prefs.videoProfilePref.value.label} — ${prefs.videoProfilePref.value.subtitle}",
                        options = VideoProfile.entries.map { it.label },
                        selectedIndex = prefs.videoProfilePref.value.ordinal,
                        onSelectIndex = { prefs.videoProfilePref.value = VideoProfile.entries[it] }
                    )
                    SettingsDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        val bitrate = prefs.videoBitrateMbpsPref.value
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bitrate",
                                color = HonorTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (bitrate == 0) "Auto" else "$bitrate Mbps",
                                color = HonorOrange,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = bitrate.toFloat(),
                            onValueChange = { prefs.videoBitrateMbpsPref.value = it.roundToInt() },
                            valueRange = 0f..200f,
                            colors = SliderDefaults.colors(
                                thumbColor = HonorOrange,
                                activeTrackColor = HonorOrange
                            )
                        )
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Auto FPS",
                        subtitle = "Dynamic 24..fps target range in low light",
                        checked = prefs.autoFpsPref.value,
                        onCheckedChange = {
                            prefs.autoFpsPref.value = it
                            cam.applyLiveCamera2Controls()
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Lock white balance",
                        subtitle = "Enable CONTROL_AWB_LOCK during video",
                        checked = prefs.lockWbPref.value,
                        onCheckedChange = {
                            prefs.lockWbPref.value = it
                            cam.applyLiveCamera2Controls()
                        }
                    )
                }
            }

            // 4. PRO VIDEO ASSISTS
            item {
                SettingsSectionCard(title = "PRO VIDEO ASSISTS") {
                    SettingsSwitchRow(
                        title = "Histogram",
                        subtitle = "Live 64-bin luma distribution in Pro & Video modes",
                        checked = prefs.histogramPref.value,
                        onCheckedChange = { prefs.histogramPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Zebra",
                        subtitle = "Highlight overexposed regions (luma >= 240)",
                        checked = prefs.zebraPref.value,
                        onCheckedChange = { prefs.zebraPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Focus peaking",
                        subtitle = "Highlight sharp in-focus edges from luma gradient",
                        checked = prefs.peakingPref.value,
                        onCheckedChange = { prefs.peakingPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Log preview assist",
                        subtitle = "Apply contrast/saturation REC.709 view assist on Log preview",
                        checked = prefs.logPreviewAssistPref.value,
                        onCheckedChange = { prefs.logPreviewAssistPref.value = it }
                    )
                }
            }

            // 5. OTHER
            item {
                val timerChoices = listOf(0, 3, 5, 10)
                val timerLabels = listOf("Off", "3s", "5s", "10s")
                SettingsSectionCard(title = "OTHER") {
                    SettingsChoiceRow(
                        title = "Timer",
                        subtitle = if (prefs.timerSecPref.value == 0) "Off" else "${prefs.timerSecPref.value}s",
                        options = timerLabels,
                        selectedIndex = timerChoices.indexOf(prefs.timerSecPref.value).coerceAtLeast(0),
                        onSelectIndex = { prefs.timerSecPref.value = timerChoices[it] }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Gridlines",
                        subtitle = "Show rule-of-thirds composition grid",
                        checked = prefs.gridlinesPref.value,
                        onCheckedChange = { prefs.gridlinesPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Level",
                        subtitle = "Show accelerometer horizontal level guide",
                        checked = prefs.levelPref.value,
                        onCheckedChange = { prefs.levelPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Mute",
                        subtitle = "Record video without microphone audio",
                        checked = prefs.muteAudioPref.value,
                        onCheckedChange = { cam.setMuteAudio(it) }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Mirror reflection",
                        subtitle = "Flip front camera photos horizontally",
                        checked = prefs.mirrorFrontPref.value,
                        onCheckedChange = { prefs.mirrorFrontPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Floating shutter",
                        subtitle = "Show draggable extra shutter button on viewfinder",
                        checked = prefs.floatingShutterPref.value,
                        onCheckedChange = { prefs.floatingShutterPref.value = it }
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        title = "Location tag",
                        subtitle = "Save GPS coordinates in photo EXIF metadata",
                        checked = prefs.locationTagPref.value,
                        onCheckedChange = { enable ->
                            if (enable) {
                                val hasFine = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasFine || hasCoarse) {
                                    prefs.locationTagPref.value = true
                                } else {
                                    onRequestLocationPermission()
                                }
                            } else {
                                prefs.locationTagPref.value = false
                            }
                        }
                    )
                }
            }

            // 6. RESTORE DEFAULTS BUTTON
            item {
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HonorOrange),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("restore_defaults_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Restore defaults",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor = HonorCard,
            title = { Text("Restore default settings?", color = HonorTextPrimary) },
            text = {
                Text(
                    "All camera, video, Pro assist, and AI settings will be reset to their factory default values.",
                    color = HonorTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.restoreDefaults()
                        cam.proIsoAuto = true
                        cam.proShutterAuto = true
                        cam.proWbPreset = WbPreset.AUTO
                        cam.proFocusAuto = true
                        cam.applyLiveCamera2Controls()
                        showRestoreConfirm = false
                        onShowNotice("All settings restored to defaults.")
                    }
                ) {
                    Text("Restore", color = HonorOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancel", color = HonorTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = HonorOrange,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
        Surface(
            color = HonorCard,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = HonorTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = HonorTextSecondary,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = HonorOrange
            )
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = HonorTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = HonorOrange,
                fontSize = 12.sp
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = HonorCard,
            title = { Text(title, color = HonorTextPrimary) },
            text = {
                Column {
                    options.forEachIndexed { idx, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSelectIndex(idx)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (idx == selectedIndex) HonorOrange else HonorTextPrimary,
                                fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close", color = HonorOrange)
                }
            }
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.07f))
}

// ============================================================================
// BUILT-IN MEDIA VIEWER (Latest DCIM/AiCam & Movies/AiCam captures)
// ============================================================================

@Composable
private fun MediaGalleryScreen(
    mediaList: List<CapturedMedia>,
    onBack: () -> Unit,
    onDeleteMedia: (CapturedMedia) -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember(mediaList.size) { mutableIntStateOf(0) }
    val currentItem = mediaList.getOrNull(currentIndex.coerceIn(0, (mediaList.size - 1).coerceAtLeast(0)))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HonorDarkBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = HonorTextPrimary
                )
            }
            Text(
                text = currentItem?.displayName ?: "AiCam Gallery",
                color = HonorTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (currentItem != null) {
                IconButton(
                    onClick = {
                        runCatching {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = currentItem.mimeType
                                putExtra(Intent.EXTRA_STREAM, currentItem.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share media"))
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = HonorTextPrimary
                    )
                }
                IconButton(onClick = { onDeleteMedia(currentItem) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = HonorRecRed
                    )
                }
            }
        }

        // Main preview area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (currentItem == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = HonorTextSecondary,
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "No photos or videos in AiCam yet",
                        color = HonorTextSecondary,
                        fontSize = 15.sp
                    )
                }
            } else if (currentItem.isVideo) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val mc = MediaController(ctx)
                            mc.setAnchorView(this)
                            setMediaController(mc)
                        }
                    },
                    update = { vv ->
                        vv.setVideoURI(currentItem.uri)
                        vv.start()
                    }
                )
            } else {
                AsyncImage(
                    model = currentItem.uri,
                    contentDescription = currentItem.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom thumbnail strip
        if (mediaList.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HonorSurface)
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mediaList.size) { idx ->
                    val item = mediaList[idx]
                    val isSelected = idx == currentIndex
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) HonorOrange else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { currentIndex = idx }
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
