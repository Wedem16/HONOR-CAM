package com.example

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve
import android.location.Location
import android.media.CamcorderProfile
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt

class Cam(
    private val context: Context,
    val prefs: Prefs,
    private val scope: CoroutineScope,
    private val onNotice: (String) -> Unit
) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val bindMutex = Mutex()

    var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    var isPreviewAttached by mutableStateOf(false)
        private set

    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var softwareVideoRecorder: SoftwareMp4Recorder? = null
    private var hasHardwareEncoderProfiles = false

    private var recordingTimerJob: Job? = null
    private var countdownJob: Job? = null

    // Observable UI state
    var isBinding by mutableStateOf(false)
        private set
    var isCapturingPhoto by mutableStateOf(false)
        private set
    var shutterFlashToken by mutableIntStateOf(0)
        private set
    var isAiProcessing by mutableStateOf(false)
        private set
    var isRecording by mutableStateOf(false)
        private set
    var recordingDurationSec by mutableLongStateOf(0L)
        private set
    var countdownRemaining by mutableIntStateOf(0)
        private set

    // Zoom state
    var currentZoomRatio by mutableFloatStateOf(1f)
        private set
    var minZoomRatio by mutableFloatStateOf(1f)
        private set
    var maxZoomRatio by mutableFloatStateOf(8f)
        private set
    var supportedZoomPills by mutableStateOf(listOf(1f, 2f))
        private set

    // Sensor capability bounds for PRO mode
    var sensorIsoMin by mutableIntStateOf(100)
        private set
    var sensorIsoMax by mutableIntStateOf(3200)
        private set
    var supportsSensitivityBoost by mutableStateOf(false)
        private set
    var boostMax by mutableIntStateOf(100)
        private set
    var effectiveIsoMax by mutableIntStateOf(6400)
        private set
    var sensorExposureMinNs by mutableLongStateOf(100_000L)
        private set
    var sensorExposureMaxNs by mutableLongStateOf(1_000_000_000L)
        private set
    var minFocusDistanceDiopters by mutableFloatStateOf(10f)
        private set
    var supportsToneMapContrastCurve by mutableStateOf(false)
        private set
    private var availableFpsRanges: Array<Range<Int>> = emptyArray()

    // Live PRO mode controls (applied via Camera2CameraControl without rebinding)
    var proIsoAuto by mutableStateOf(true)
    var proIsoValue by mutableIntStateOf(400)
    var proShutterAuto by mutableStateOf(true)
    var proShutterNs by mutableLongStateOf(1_000_000_000L / 125L) // 1/125s default
    var proWbPreset by mutableStateOf(WbPreset.AUTO)
    var proFocusAuto by mutableStateOf(true)
    var proFocusDistance by mutableFloatStateOf(0f)

    // Pro assists latest snapshot
    var assistData by mutableStateOf(AssistData())
        private set

    // Media gallery state
    var recentMedia by mutableStateOf<List<CapturedMedia>>(emptyList())
        private set
    var latestMedia by mutableStateOf<CapturedMedia?>(null)
        private set

    // Orientation tracking
    var targetRotation by mutableIntStateOf(Surface.ROTATION_0)
        private set

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val newRotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            if (newRotation != targetRotation) {
                targetRotation = newRotation
                imageCapture?.targetRotation = newRotation
                videoCapture?.targetRotation = newRotation
            }
        }
    }

    init {
        orientationListener.enable()
        refreshGallery()
    }

    fun refreshGallery() {
        scope.launch {
            val list = Post.loadAiCamMedia(context)
            recentMedia = list
            latestMedia = list.firstOrNull()
        }
    }

    fun attach(owner: LifecycleOwner, view: PreviewView) {
        lifecycleOwner = owner
        previewView = view
        isPreviewAttached = true
    }

    fun bindCamera() {
        val owner = lifecycleOwner ?: return
        val pv = previewView ?: return

        scope.launch(Dispatchers.Main) {
            bindMutex.withLock {
                isBinding = true
                try {
                    val provider = cameraProvider
                        ?: ProcessCameraProvider.getInstance(context).await().also {
                            cameraProvider = it
                        }

                    if (isRecording) {
                        stopRecording()
                    }
                    provider.unbindAll()

                    val mode = prefs.modePref.value
                    val isVideo = mode.isVideoCapture(prefs.proIsVideoPref.value)
                    val baseSelector = resolveBaseCameraSelector(provider, prefs.isFrontCameraPref.value)

                    // Pre-query sensor capabilities & verify if CameraX EncoderProfiles exist
                    val matchedInfo = runCatching {
                        baseSelector.filter(provider.availableCameraInfos).firstOrNull()
                    }.getOrNull()
                    inspectCameraInfoCapabilities(matchedInfo)
                    hasHardwareEncoderProfiles = hasValidCameraXEncoderProfiles(matchedInfo)

                    // Only initialize ExtensionsManager when an Extension mode is actually requested
                    val needExtensions = !isVideo && (
                        mode == Mode.NIGHT ||
                            mode == Mode.PORTRAIT ||
                            (mode == Mode.PHOTO && prefs.hdrPref.value)
                        )
                    val finalSelector = if (needExtensions) {
                        val extMgr = extensionsManager
                            ?: ExtensionsManager.getInstanceAsync(context, provider).await().also {
                                extensionsManager = it
                            }
                        resolveExtensionCameraSelector(extMgr, baseSelector, mode)
                    } else {
                        baseSelector
                    }

                    val includeAssists = (mode == Mode.VIDEO || mode == Mode.PRO) ||
                        (isVideo && !hasHardwareEncoderProfiles)
                    val wantHdr10 = (isVideo && hasHardwareEncoderProfiles &&
                        prefs.videoProfilePref.value == VideoProfile.HDR10)

                    var bound = tryBindUseCases(
                        provider = provider,
                        owner = owner,
                        pv = pv,
                        selector = finalSelector,
                        mode = mode,
                        isVideo = isVideo,
                        includeAssistAnalyzer = includeAssists,
                        useHdr10Video = wantHdr10,
                        useViewPort = true
                    )

                    if (!bound && finalSelector != baseSelector) {
                        onNotice("${mode.title} extension bind fallback to standard capture.")
                        bound = tryBindUseCases(
                            provider = provider,
                            owner = owner,
                            pv = pv,
                            selector = baseSelector,
                            mode = mode,
                            isVideo = isVideo,
                            includeAssistAnalyzer = includeAssists,
                            useHdr10Video = false,
                            useViewPort = true
                        )
                    }

                    if (!bound) {
                        tryBindUseCases(
                            provider = provider,
                            owner = owner,
                            pv = pv,
                            selector = baseSelector,
                            mode = mode,
                            isVideo = isVideo,
                            includeAssistAnalyzer = (isVideo && !hasHardwareEncoderProfiles),
                            useHdr10Video = false,
                            useViewPort = false
                        )
                    }
                } catch (t: Throwable) {
                    onNotice("Camera bind error: ${t.localizedMessage ?: "Unknown error"}")
                } finally {
                    isBinding = false
                }
            }
        }
    }

    /**
     * Checks whether the camera device has at least one CamcorderProfile resolution that matches
     * its StreamConfigurationMap output sizes before invoking CameraX Recorder/CapabilitiesByQuality.
     * Prevents `E/CapabilitiesByQuality: No supported EncoderProfiles` on virtual/emulator cameras.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun hasValidCameraXEncoderProfiles(info: CameraInfo?): Boolean {
        if (info == null) return false
        return runCatching {
            val c2 = Camera2CameraInfo.from(info)
            val camId = c2.cameraId.toIntOrNull() ?: return false
            val map = c2.getCameraCharacteristic(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            ) ?: return false
            val recorderSizes = map.getOutputSizes(MediaRecorder::class.java)?.toSet().orEmpty()
            val textureSizes = map.getOutputSizes(SurfaceTexture::class.java)?.toSet().orEmpty()
            val validSizes = if (recorderSizes.isNotEmpty()) {
                recorderSizes.intersect(textureSizes.ifEmpty { recorderSizes })
            } else {
                textureSizes
            }
            if (validSizes.isEmpty()) return false

            val candidateQualities = intArrayOf(
                CamcorderProfile.QUALITY_2160P,
                CamcorderProfile.QUALITY_1080P,
                CamcorderProfile.QUALITY_720P,
                CamcorderProfile.QUALITY_480P
            )
            var matched = 0
            for (q in candidateQualities) {
                if (CamcorderProfile.hasProfile(camId, q)) {
                    @Suppress("DEPRECATION")
                    val profile = CamcorderProfile.get(camId, q)
                    if (profile != null &&
                        profile.videoCodec > 0 &&
                        Size(profile.videoFrameWidth, profile.videoFrameHeight) in validSizes
                    ) {
                        matched++
                    }
                }
            }
            // Note: on Android emulators, CamcorderProfile often lacks valid H.264 EncoderProfiles
            // validated by CameraX EncoderProfilesProvider; also verify non-emulator hardware or valid codec.
            val isEmulator = android.os.Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                android.os.Build.MODEL.contains("sdk", ignoreCase = true) ||
                android.os.Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                android.os.Build.HARDWARE.contains("goldfish", ignoreCase = true)
            !isEmulator && matched > 0
        }.getOrDefault(false)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun inspectCameraInfoCapabilities(info: CameraInfo?) {
        if (info == null) return
        runCatching {
            val c2Info = Camera2CameraInfo.from(info)
            availableFpsRanges = c2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
            ) ?: emptyArray()

            val sensRange = c2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
            )
            sensorIsoMin = sensRange?.lower ?: 100
            sensorIsoMax = sensRange?.upper ?: 3200

            val boostRange = c2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE
            )
            supportsSensitivityBoost = boostRange != null && (boostRange.upper > 100)
            boostMax = boostRange?.upper ?: 100

            val calcMax = (sensorIsoMax.toLong() * boostMax.toLong() / 100L).toInt()
            effectiveIsoMax = min(409600, calcMax.coerceAtLeast(sensorIsoMax))
            proIsoValue = proIsoValue.coerceIn(sensorIsoMin, effectiveIsoMax)

            val expRange = c2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
            )
            sensorExposureMinNs = expRange?.lower ?: 100_000L
            sensorExposureMaxNs = expRange?.upper ?: 1_000_000_000L

            val minFocus = c2Info.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
            ) ?: 0f
            minFocusDistanceDiopters = if (minFocus > 0f) minFocus else 10f

            val toneModes = c2Info.getCameraCharacteristic(
                CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES
            ) ?: IntArray(0)
            supportsToneMapContrastCurve =
                toneModes.contains(CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE)
        }
    }

    private fun resolveSupportedFpsRange(targetFps: Int, autoFps: Boolean): Range<Int>? {
        val ranges = availableFpsRanges
        if (ranges.isEmpty()) {
            return null
        }
        if (autoFps) {
            val variableMatch = ranges
                .filter { it.upper == targetFps && it.lower < targetFps }
                .maxByOrNull { it.lower }
            if (variableMatch != null) return variableMatch
        } else {
            val fixedMatch = ranges.firstOrNull { it.lower == targetFps && it.upper == targetFps }
            if (fixedMatch != null) return fixedMatch
        }
        val closestUpper = ranges.minByOrNull { abs(it.upper - targetFps) }?.upper ?: return null
        return if (autoFps) {
            ranges.filter { it.upper == closestUpper }.minByOrNull { it.lower }
        } else {
            ranges.filter { it.upper == closestUpper }.maxByOrNull { it.lower }
        }
    }

    private fun resolveBaseCameraSelector(
        provider: ProcessCameraProvider,
        wantFront: Boolean
    ): CameraSelector {
        val preferred = if (wantFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (runCatching { provider.hasCamera(preferred) }.getOrDefault(false)) {
            return preferred
        }
        val fallback = if (wantFront) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        if (runCatching { provider.hasCamera(fallback) }.getOrDefault(false)) {
            onNotice("Requested lens unavailable; switched to available camera.")
            return fallback
        }
        return CameraSelector.DEFAULT_BACK_CAMERA
    }

    private fun resolveExtensionCameraSelector(
        extMgr: ExtensionsManager,
        baseSelector: CameraSelector,
        mode: Mode
    ): CameraSelector {
        val wantedExt: Pair<Int, String>? = when {
            mode == Mode.NIGHT -> ExtensionMode.NIGHT to "Night"
            mode == Mode.PORTRAIT -> ExtensionMode.BOKEH to "Portrait Bokeh"
            mode == Mode.PHOTO && prefs.hdrPref.value -> ExtensionMode.HDR to "HDR"
            else -> null
        }

        if (wantedExt == null) return baseSelector
        val (extMode, label) = wantedExt
        return try {
            if (extMgr.isExtensionAvailable(baseSelector, extMode)) {
                extMgr.getExtensionEnabledCameraSelector(baseSelector, extMode)
            } else {
                onNotice("$label extension not supported on this lens; using standard capture.")
                baseSelector
            }
        } catch (_: Throwable) {
            onNotice("$label extension unavailable; using standard capture.")
            baseSelector
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun tryBindUseCases(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        pv: PreviewView,
        selector: CameraSelector,
        mode: Mode,
        isVideo: Boolean,
        includeAssistAnalyzer: Boolean,
        useHdr10Video: Boolean,
        useViewPort: Boolean
    ): Boolean {
        val previewBuilder = Preview.Builder()
            .setTargetRotation(targetRotation)

        val validFpsRange = if (isVideo) {
            resolveSupportedFpsRange(prefs.videoSpecPref.value.fps, prefs.autoFpsPref.value)
        } else {
            null
        }

        if (isVideo) {
            val pExt = Camera2Interop.Extender(previewBuilder)
            if (validFpsRange != null) {
                pExt.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, validFpsRange)
            }
            pExt.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, prefs.lockWbPref.value)
        }

        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(pv.surfaceProvider)
        }

        val useCaseGroupBuilder = UseCaseGroup.Builder()
            .addUseCase(preview)

        if (useViewPort) {
            val ratioOption = if (isVideo) AspectRatioOption.RATIO_16_9 else prefs.aspectRatioPref.value
            val viewPort = ViewPort.Builder(ratioOption.rationalPortrait, targetRotation)
                .setScaleType(ViewPort.FILL_CENTER)
                .build()
            useCaseGroupBuilder.setViewPort(viewPort)
        }

        if (isVideo && hasHardwareEncoderProfiles) {
            val spec = prefs.videoSpecPref.value
            val quality = when (spec) {
                VideoSpec.SPEC_720P30 -> Quality.HD
                VideoSpec.SPEC_1080P30, VideoSpec.SPEC_1080P60 -> Quality.FHD
                VideoSpec.SPEC_4K30, VideoSpec.SPEC_4K60 -> Quality.UHD
            }
            val recorderBuilder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        quality,
                        FallbackStrategy.higherQualityOrLowerThan(quality)
                    )
                )
            val bitrateMbps = prefs.videoBitrateMbpsPref.value
            if (bitrateMbps > 0) {
                runCatching {
                    recorderBuilder.setTargetVideoEncodingBitRate(bitrateMbps * 1_000_000)
                }
            }
            val recorder = recorderBuilder.build()
            val vcBuilder = VideoCapture.Builder(recorder)
                .setTargetRotation(targetRotation)

            if (useHdr10Video) {
                vcBuilder.setDynamicRange(DynamicRange.HLG_10_BIT)
            }

            val vcExt = Camera2Interop.Extender(vcBuilder)
            if (validFpsRange != null) {
                vcExt.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, validFpsRange)
            }
            vcExt.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, prefs.lockWbPref.value)

            val builtVc = vcBuilder.build()
            videoCapture = builtVc
            imageCapture = null
            useCaseGroupBuilder.addUseCase(builtVc)
        } else if (!isVideo) {
            val icBuilder = ImageCapture.Builder()
                .setTargetRotation(targetRotation)
                .setFlashMode(
                    when (prefs.flashPhotoPref.value) {
                        FlashPhotoMode.OFF -> ImageCapture.FLASH_MODE_OFF
                        FlashPhotoMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                        FlashPhotoMode.ON -> ImageCapture.FLASH_MODE_ON
                    }
                )

            if (mode == Mode.HIGH_RES && useViewPort) {
                val highResSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    .build()
                icBuilder.setResolutionSelector(highResSelector)
                icBuilder.setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            } else {
                icBuilder.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            }

            val builtIc = icBuilder.build()
            imageCapture = builtIc
            videoCapture = null
            useCaseGroupBuilder.addUseCase(builtIc)
        } else {
            // Video mode on a device/emulator without hardware EncoderProfiles:
            // We record via SoftwareMp4Recorder fed from ImageAnalysis without triggering CapabilitiesByQuality errors.
            videoCapture = null
            imageCapture = null
        }

        if (includeAssistAnalyzer) {
            val assistDelegate = AssistAnalyzer(
                isHistogramEnabled = { prefs.histogramPref.value },
                isZebraEnabled = { prefs.zebraPref.value },
                isPeakingEnabled = { prefs.peakingPref.value },
                onResult = { data ->
                    assistData = data
                }
            )
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetRotation(targetRotation)
                .build()
            analysis.setAnalyzer(
                analysisExecutor
            ) { imageProxy ->
                // If software video recorder is active, feed frame before assist analyzer closes it
                softwareVideoRecorder?.onFrame(imageProxy)
                assistDelegate.analyze(imageProxy)
            }
            useCaseGroupBuilder.addUseCase(analysis)
        } else {
            assistData = AssistData()
        }

        return try {
            provider.unbindAll()
            val boundCam = provider.bindToLifecycle(owner, selector, useCaseGroupBuilder.build())
            camera = boundCam
            onCameraBound(boundCam, mode, isVideo)
            true
        } catch (_: Throwable) {
            if (useHdr10Video) {
                onNotice("HLG 10-bit HDR video unsupported on this device; falling back to Standard SDR.")
                return tryBindUseCases(
                    provider = provider,
                    owner = owner,
                    pv = pv,
                    selector = selector,
                    mode = mode,
                    isVideo = isVideo,
                    includeAssistAnalyzer = includeAssistAnalyzer,
                    useHdr10Video = false,
                    useViewPort = useViewPort
                )
            }
            if (includeAssistAnalyzer && !(isVideo && !hasHardwareEncoderProfiles)) {
                onNotice("Pro assist analyzer could not bind simultaneously; bound without analyzer.")
                return tryBindUseCases(
                    provider = provider,
                    owner = owner,
                    pv = pv,
                    selector = selector,
                    mode = mode,
                    isVideo = isVideo,
                    includeAssistAnalyzer = false,
                    useHdr10Video = false,
                    useViewPort = useViewPort
                )
            }
            false
        }
    }

    private fun onCameraBound(boundCam: Camera, mode: Mode, isVideo: Boolean) {
        boundCam.cameraInfo.zoomState.value?.let { zs ->
            minZoomRatio = zs.minZoomRatio
            maxZoomRatio = zs.maxZoomRatio
            currentZoomRatio = zs.zoomRatio
            val candidates = listOf(0.6f, 1.0f, 1.5f, 2.0f, 5.0f)
            val filtered = candidates.filter { pill ->
                pill == 1.0f || (pill >= zs.minZoomRatio - 0.05f && pill <= zs.maxZoomRatio + 0.05f)
            }
            supportedZoomPills = filtered.ifEmpty { listOf(1.0f) }
        }

        inspectCameraInfoCapabilities(boundCam.cameraInfo)

        if (isVideo) {
            boundCam.cameraControl.enableTorch(prefs.videoTorchPref.value)
        } else {
            boundCam.cameraControl.enableTorch(false)
        }

        applyLiveCamera2Controls()
    }

    /**
     * Applies live Camera2 controls via Camera2CameraControl without rebinding the camera.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun applyLiveCamera2Controls() {
        val boundCam = camera ?: return
        val mode = prefs.modePref.value
        val isVideo = mode.isVideoCapture(prefs.proIsVideoPref.value)

        runCatching {
            val c2Control = Camera2CameraControl.from(boundCam.cameraControl)

            if (!isVideo && mode != Mode.PRO && mode != Mode.SUPER_MACRO) {
                c2Control.clearCaptureRequestOptions()
                return@runCatching
            }

            val builder = CaptureRequestOptions.Builder()

            if (mode == Mode.SUPER_MACRO) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    minFocusDistanceDiopters
                )
            } else if (mode == Mode.PRO) {
                if (proFocusAuto) {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        if (isVideo) {
                            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        } else {
                            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        }
                    )
                } else {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_OFF
                    )
                    builder.setCaptureRequestOption(
                        CaptureRequest.LENS_FOCUS_DISTANCE,
                        proFocusDistance.coerceIn(0f, minFocusDistanceDiopters)
                    )
                }

                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    proWbPreset.awbMode
                )

                val maxVideoShutterNs = 1_000_000_000L / prefs.videoSpecPref.value.fps.coerceAtLeast(24).toLong()
                val upperExpBound = if (isVideo) {
                    min(sensorExposureMaxNs, maxVideoShutterNs)
                } else {
                    min(sensorExposureMaxNs, 1_000_000_000L)
                }
                val lowerExpBound = sensorExposureMinNs.coerceAtMost(upperExpBound)
                val effectiveShutterNs = proShutterNs.coerceIn(lowerExpBound, upperExpBound)

                // Also sync CameraX ExposureCompensationIndex as fallback for HALs with limited manual AE
                runCatching {
                    val ecState = boundCam.cameraInfo.exposureState
                    if (ecState.isExposureCompensationSupported) {
                        if (proIsoAuto && proShutterAuto) {
                            boundCam.cameraControl.setExposureCompensationIndex(0)
                        } else {
                            val effIso = if (proIsoAuto) 400.0 else proIsoValue.toDouble()
                            val effShut = if (proShutterAuto) 8_000_000.0 else effectiveShutterNs.toDouble()
                            val evStops = ln((effIso / 400.0) * (effShut / 8_000_000.0)) / ln(2.0)
                            val step = ecState.exposureCompensationStep.toDouble().takeIf { it > 0.0 } ?: 0.333
                            val targetIdx = (evStops / step).roundToInt()
                                .coerceIn(ecState.exposureCompensationRange.lower, ecState.exposureCompensationRange.upper)
                            boundCam.cameraControl.setExposureCompensationIndex(targetIdx)
                        }
                    }
                }

                if (proIsoAuto && proShutterAuto) {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON
                    )
                    if (supportsSensitivityBoost) {
                        builder.setCaptureRequestOption(
                            CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
                            100
                        )
                    }
                } else {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_OFF
                    )
                    val targetIso = if (proIsoAuto) 400.coerceIn(sensorIsoMin, sensorIsoMax) else proIsoValue
                    if (targetIso <= sensorIsoMax || !supportsSensitivityBoost) {
                        builder.setCaptureRequestOption(
                            CaptureRequest.SENSOR_SENSITIVITY,
                            targetIso.coerceIn(sensorIsoMin, sensorIsoMax)
                        )
                        if (supportsSensitivityBoost) {
                            builder.setCaptureRequestOption(
                                CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
                                100
                            )
                        }
                    } else {
                        builder.setCaptureRequestOption(
                            CaptureRequest.SENSOR_SENSITIVITY,
                            sensorIsoMax
                        )
                        val boost = ((targetIso.toDouble() / sensorIsoMax.toDouble()) * 100.0)
                            .roundToInt()
                            .coerceIn(100, boostMax)
                        builder.setCaptureRequestOption(
                            CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
                            boost
                        )
                    }

                    val shutterToApply = if (proShutterAuto) {
                        val defaultShutter = if (isVideo) (1_000_000_000L / 60L) else (1_000_000_000L / 125L)
                        defaultShutter.coerceIn(lowerExpBound, upperExpBound)
                    } else {
                        effectiveShutterNs
                    }
                    builder.setCaptureRequestOption(
                        CaptureRequest.SENSOR_EXPOSURE_TIME,
                        shutterToApply
                    )
                    if (isVideo) {
                        val fps = prefs.videoSpecPref.value.fps
                        val frameDurationNs = 1_000_000_000L / fps.toLong()
                        builder.setCaptureRequestOption(
                            CaptureRequest.SENSOR_FRAME_DURATION,
                            frameDurationNs
                        )
                    }
                }
            }

            if (isVideo) {
                val spec = prefs.videoSpecPref.value
                val validFpsRange = resolveSupportedFpsRange(spec.fps, prefs.autoFpsPref.value)
                if (validFpsRange != null && (mode != Mode.PRO || (proIsoAuto && proShutterAuto))) {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        validFpsRange
                    )
                }
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_LOCK,
                    prefs.lockWbPref.value
                )

                val profile = prefs.videoProfilePref.value
                if (profile.isLogProfile) {
                    if (supportsToneMapContrastCurve) {
                        val curve = if (profile == VideoProfile.APPLE_LOG) {
                            buildAppleLogTonemapCurve()
                        } else {
                            buildLogTonemapCurve()
                        }
                        builder.setCaptureRequestOption(
                            CaptureRequest.TONEMAP_MODE,
                            CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
                        )
                        builder.setCaptureRequestOption(
                            CaptureRequest.TONEMAP_CURVE,
                            curve
                        )
                    }
                }
            }

            c2Control.setCaptureRequestOptions(builder.build())
        }
    }

    /**
     * Builds the authentic Apple Log piecewise logarithmic transfer curve:
     * For R >= 0.01: P = gamma * log2(R + beta) + delta
     * For R <  0.01: P = c * (R - R0)^2
     */
    private fun buildAppleLogTonemapCurve(): TonemapCurve {
        val numPoints = 33
        val points = FloatArray(numPoints * 2)
        val r0 = -0.05641088
        val rt = 0.01
        val c = 47.28711236
        val beta = 0.00964052
        val gamma = 0.08550479
        val delta = 0.69336945
        val ln2 = ln(2.0)

        for (i in 0 until numPoints) {
            val r = i.toDouble() / (numPoints - 1).toDouble()
            val p = if (r >= rt) {
                gamma * (ln(r + beta) / ln2) + delta
            } else {
                val diff = r - r0
                c * diff * diff
            }
            points[i * 2] = r.toFloat()
            points[i * 2 + 1] = p.toFloat().coerceIn(0f, 1f)
        }
        return TonemapCurve(points, points.copyOf(), points.copyOf())
    }

    private fun buildLogTonemapCurve(): TonemapCurve {
        val numPoints = 33
        val points = FloatArray(numPoints * 2)
        val denom = ln(17.0)
        for (i in 0 until numPoints) {
            val x = i.toFloat() / (numPoints - 1).toFloat()
            val y = (ln(1.0 + 16.0 * x) / denom).toFloat().coerceIn(0f, 1f)
            points[i * 2] = x
            points[i * 2 + 1] = y
        }
        return TonemapCurve(points, points.copyOf(), points.copyOf())
    }

    fun setZoom(ratio: Float) {
        val c = camera ?: return
        val clamped = ratio.coerceIn(minZoomRatio, maxZoomRatio)
        currentZoomRatio = clamped
        c.cameraControl.setZoomRatio(clamped)
    }

    fun onPinchZoom(zoomChange: Float) {
        setZoom(currentZoomRatio * zoomChange)
    }

    fun tapToFocus(x: Float, y: Float) {
        val pv = previewView ?: return
        val c = camera ?: return
        if (prefs.modePref.value == Mode.SUPER_MACRO) return
        if (prefs.modePref.value == Mode.PRO && !proFocusAuto) return
        val point = pv.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        c.cameraControl.startFocusAndMetering(action)
    }

    fun toggleFlashOrTorch() {
        val isVideo = prefs.modePref.value.isVideoCapture(prefs.proIsVideoPref.value)
        if (isVideo) {
            val next = !prefs.videoTorchPref.value
            prefs.videoTorchPref.value = next
            camera?.cameraControl?.enableTorch(next)
        } else {
            val next = prefs.flashPhotoPref.value.next()
            prefs.flashPhotoPref.value = next
            imageCapture?.flashMode = when (next) {
                FlashPhotoMode.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashPhotoMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashPhotoMode.ON -> ImageCapture.FLASH_MODE_ON
            }
        }
    }

    fun setMuteAudio(muted: Boolean) {
        prefs.muteAudioPref.value = muted
        runCatching {
            activeRecording?.mute(muted)
        }
    }

    fun flipCamera() {
        if (isRecording) return
        prefs.isFrontCameraPref.value = !prefs.isFrontCameraPref.value
        bindCamera()
    }

    fun triggerShutter(onLaunchDocScanner: () -> Unit) {
        if (countdownRemaining > 0) {
            countdownJob?.cancel()
            countdownRemaining = 0
            return
        }

        val mode = prefs.modePref.value
        val isVideo = mode.isVideoCapture(prefs.proIsVideoPref.value)
        if (isVideo) {
            if (isRecording) {
                stopRecording()
            } else {
                startTimerThen { startRecording() }
            }
        } else {
            if (!isCapturingPhoto) {
                startTimerThen { takePhoto() }
            }
        }
    }

    private fun startTimerThen(action: () -> Unit) {
        val sec = prefs.timerSecPref.value
        if (sec <= 0) {
            action()
            return
        }
        countdownJob?.cancel()
        countdownJob = scope.launch(Dispatchers.Main) {
            for (remaining in sec downTo 1) {
                countdownRemaining = remaining
                delay(1000L)
            }
            countdownRemaining = 0
            action()
        }
    }

    private fun takePhoto() {
        val ic = imageCapture ?: run {
            onNotice("Camera still initializing, please wait...")
            bindCamera()
            return
        }
        isCapturingPhoto = true
        shutterFlashToken++

        val currentMode = prefs.modePref.value
        val isDocScanCapture = (currentMode == Mode.SCAN_DOCUMENT)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = if (isDocScanCapture) "SCAN_$stamp" else "IMG_$stamp"
        val fileName = "$baseName.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/AiCam")
        }

        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = prefs.isFrontCameraPref.value && prefs.mirrorFrontPref.value
        }

        scope.launch(Dispatchers.Main) {
            val loc = if (prefs.locationTagPref.value) fetchLastLocation() else null
            if (loc != null) {
                metadata.location = loc
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).setMetadata(metadata).build()

            ic.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedUri = outputFileResults.savedUri
                        isCapturingPhoto = false
                        if (savedUri == null) {
                            onNotice("Photo saved.")
                            refreshGallery()
                            return
                        }

                        val activeFilter = prefs.filterPref.value
                        val isUnderwater = (currentMode == Mode.UNDERWATER)

                        scope.launch {
                            if (isDocScanCapture) {
                                Post.convertPhotoToDocumentPdf(context, savedUri).onSuccess {
                                    onNotice("Document scanned: PDF saved to Documents/AiCam & JPEG to DCIM/AiCam")
                                }.onFailure {
                                    onNotice("Document JPEG saved to DCIM/AiCam")
                                }
                            } else {
                                runCatching {
                                    Post.processSavedJpeg(
                                        context = context,
                                        uri = savedUri,
                                        filter = activeFilter,
                                        isUnderwater = isUnderwater,
                                        location = loc
                                    )
                                }.onFailure {
                                    onNotice("Filter warning: ${it.localizedMessage}")
                                }
                            }

                            refreshGallery()

                            if (prefs.aiEnabledPref.value && !isDocScanCapture) {
                                isAiProcessing = true
                                onNotice("AI Camera: enhancing photo with ${prefs.aiModelPref.value}...")
                                val aiResult = Post.enhancePhotoWithOpenAi(
                                    context = context,
                                    sourceUri = savedUri,
                                    baseName = baseName,
                                    apiKey = prefs.aiApiKeyPref.value,
                                    model = prefs.aiModelPref.value,
                                    prompt = prefs.aiPromptPref.value,
                                    keepOriginal = prefs.aiKeepOriginalPref.value,
                                    location = loc
                                )
                                isAiProcessing = false
                                aiResult.onSuccess {
                                    onNotice("AI enhanced photo saved (${baseName}_AI.jpg)")
                                    refreshGallery()
                                }.onFailure { err ->
                                    onNotice("AI Enhance failed: ${err.localizedMessage}")
                                }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        isCapturingPhoto = false
                        onNotice("Photo capture failed: ${exception.localizedMessage}")
                    }
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        if (isRecording) return

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "VID_$stamp.mp4"

        // Case A: Physical device with validated CameraX EncoderProfiles
        val vc = videoCapture
        if (vc != null) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AiCam")
            }

            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            var pending = vc.output.prepareRecording(context, mediaStoreOutput)
            val hasAudioPerm = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (hasAudioPerm) {
                pending = pending.withAudioEnabled()
            }

            isRecording = true
            recordingDurationSec = 0L

            activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        runCatching { activeRecording?.mute(prefs.muteAudioPref.value) }
                        recordingTimerJob?.cancel()
                        recordingTimerJob = scope.launch(Dispatchers.Main) {
                            while (isRecording) {
                                delay(1000L)
                                recordingDurationSec++
                            }
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        recordingTimerJob?.cancel()
                        activeRecording = null
                        if (event.hasError()) {
                            onNotice("Video error (${event.error}): ${event.cause?.localizedMessage ?: ""}")
                        } else {
                            onNotice("Video saved to Movies/AiCam")
                        }
                        refreshGallery()
                    }
                }
            }
            return
        }

        // Case B: Emulator / virtual camera without hardware EncoderProfiles — use SoftwareMp4Recorder
        val tempFile = File(context.cacheDir, fileName)
        val bitrateBps = prefs.videoBitrateMbpsPref.value.let { mbps ->
            if (mbps > 0) mbps * 1_000_000 else 4_000_000
        }
        val fps = prefs.videoSpecPref.value.fps.coerceIn(15, 60)
        softwareVideoRecorder = SoftwareMp4Recorder(
            outputFile = tempFile,
            fps = fps,
            bitrate = bitrateBps
        )
        isRecording = true
        recordingDurationSec = 0L
        recordingTimerJob?.cancel()
        recordingTimerJob = scope.launch(Dispatchers.Main) {
            while (isRecording) {
                delay(1000L)
                recordingDurationSec++
            }
        }
    }

    fun stopRecording() {
        val swRec = softwareVideoRecorder
        softwareVideoRecorder = null
        runCatching {
            activeRecording?.stop()
        }
        isRecording = false
        recordingTimerJob?.cancel()

        if (swRec != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        swRec.finish()
                        val outFile = swRec.outputFile
                        if (outFile.exists() && outFile.length() > 0L) {
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, outFile.name)
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                put(
                                    MediaStore.Video.Media.RELATIVE_PATH,
                                    "${Environment.DIRECTORY_MOVIES}/AiCam"
                                )
                                put(MediaStore.Video.Media.IS_PENDING, 1)
                            }
                            val resolver = context.contentResolver
                            val uri = resolver.insert(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                values
                            )
                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { out ->
                                    outFile.inputStream().use { it.copyTo(out) }
                                }
                                val done = ContentValues().apply {
                                    put(MediaStore.Video.Media.IS_PENDING, 0)
                                }
                                resolver.update(uri, done, null, null)
                            }
                            outFile.delete()
                            uri != null
                        } else {
                            outFile.delete()
                            false
                        }
                    }.getOrDefault(false)
                }
                if (saved) {
                    onNotice("Video saved to Movies/AiCam")
                } else {
                    onNotice("Video recording stopped.")
                }
                refreshGallery()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLastLocation(): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return null

        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            awaitLocationTask(client.lastLocation)
                ?: awaitLocationTask(
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                )
        }.getOrNull()
    }

    private suspend fun awaitLocationTask(task: com.google.android.gms.tasks.Task<Location>): Location? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc) {}
            }.addOnFailureListener {
                if (cont.isActive) cont.resume(null) {}
            }.addOnCanceledListener {
                if (cont.isActive) cont.resume(null) {}
            }
        }

    fun release() {
        orientationListener.disable()
        countdownJob?.cancel()
        recordingTimerJob?.cancel()
        runCatching { activeRecording?.stop() }
        runCatching { softwareVideoRecorder?.finish() }
        analysisExecutor.shutdown()
    }
}

/**
 * Fallback H.264/AVC MP4 encoder using MediaCodec + MediaMuxer for virtual/emulator cameras
 * that lack CameraX-validated EncoderProfiles (`No supported EncoderProfiles`).
 */
private class SoftwareMp4Recorder(
    val outputFile: File,
    private val fps: Int,
    private val bitrate: Int
) {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameCount = 0L
    private val closed = AtomicBoolean(false)
    private var encWidth = 0
    private var encHeight = 0

    @Synchronized
    fun onFrame(image: ImageProxy) {
        if (closed.get()) return
        runCatching {
            val w = (image.width / 2) * 2
            val h = (image.height / 2) * 2
            if (w <= 0 || h <= 0) return

            if (codec == null) {
                encWidth = w
                encHeight = h
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(
                        MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    )
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                enc.start()
                codec = enc
                muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val enc = codec ?: return
            if (image.width != encWidth || image.height != encHeight) return

            val inIdx = enc.dequeueInputBuffer(10_000L)
            if (inIdx >= 0) {
                val inBuf = enc.getInputBuffer(inIdx)
                if (inBuf != null) {
                    inBuf.clear()
                    val ySize = encWidth * encHeight
                    val uvSize = ySize / 4
                    val totalBytes = ySize + uvSize * 2
                    if (inBuf.capacity() >= totalBytes) {
                        val yPlane = image.planes[0]
                        val uPlane = image.planes[1]
                        val vPlane = image.planes[2]
                        copyPlane(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, encWidth, encHeight, inBuf)
                        copyPlane(uPlane.buffer, uPlane.rowStride, uPlane.pixelStride, encWidth / 2, encHeight / 2, inBuf)
                        copyPlane(vPlane.buffer, vPlane.rowStride, vPlane.pixelStride, encWidth / 2, encHeight / 2, inBuf)
                        val ptsUs = (frameCount * 1_000_000L) / fps.toLong()
                        frameCount++
                        enc.queueInputBuffer(inIdx, 0, totalBytes, ptsUs, 0)
                    } else {
                        enc.queueInputBuffer(inIdx, 0, 0, 0L, 0)
                    }
                }
            }
            drainEncoder(endOfStream = false)
        }
    }

    private fun copyPlane(
        src: java.nio.ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        dst: java.nio.ByteBuffer
    ) {
        src.rewind()
        val limit = src.limit()
        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (col in 0 until width) {
                val idx = rowStart + col * pixelStride
                val b = if (idx in 0 until limit) src.get(idx) else 0
                dst.put(b)
            }
        }
        src.rewind()
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val enc = codec ?: return
        val mux = muxer ?: return
        val info = MediaCodec.BufferInfo()

        if (endOfStream) {
            val inIdx = enc.dequeueInputBuffer(10_000L)
            if (inIdx >= 0) {
                val ptsUs = (frameCount * 1_000_000L) / fps.toLong()
                enc.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }

        var loops = 0
        while (loops < 20) {
            loops++
            val outIdx = enc.dequeueOutputBuffer(info, 10_000L)
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    trackIndex = mux.addTrack(enc.outputFormat)
                    mux.start()
                    muxerStarted = true
                }
            } else if (outIdx >= 0) {
                val encodedData = enc.getOutputBuffer(outIdx)
                if (encodedData != null && info.size > 0 && muxerStarted &&
                    (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                ) {
                    encodedData.position(info.offset)
                    encodedData.limit(info.offset + info.size)
                    mux.writeSampleData(trackIndex, encodedData, info)
                }
                enc.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    @Synchronized
    fun finish() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { drainEncoder(endOfStream = true) }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching {
            if (muxerStarted) {
                muxer?.stop()
            }
        }
        runCatching { muxer?.release() }
        codec = null
        muxer = null
    }
}
