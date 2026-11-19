package com.example

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve
import android.location.Location
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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

    var previewView: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var extensionsManager: ExtensionsManager? = null
    private var camera: Camera? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var recordingTimerJob: Job? = null
    private var countdownJob: Job? = null

    // Observable UI state
    var isBinding by mutableStateOf(false)
        private set
    var isCapturingPhoto by mutableStateOf(false)
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
    var boostMax by mutableIntStateOf(100)
        private set
    var effectiveIsoMax by mutableIntStateOf(6400)
        private set
    var minFocusDistanceDiopters by mutableFloatStateOf(10f)
        private set
    var supportsToneMapContrastCurve by mutableStateOf(false)
        private set

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
        bindCamera()
    }

    fun bindCamera() {
        val owner = lifecycleOwner ?: return
        val pv = previewView ?: return
        if (prefs.modePref.value == Mode.SCAN_DOCUMENT) {
            // Document Scanner uses ML Kit activity result launcher
            return
        }

        scope.launch(Dispatchers.Main) {
            isBinding = true
            try {
                val provider = cameraProvider ?: ProcessCameraProvider.getInstance(context).await().also {
                    cameraProvider = it
                }
                val extMgr = extensionsManager ?: ExtensionsManager.getInstanceAsync(context, provider).await().also {
                    extensionsManager = it
                }

                if (isRecording) {
                    stopRecording()
                }
                provider.unbindAll()

                val mode = prefs.modePref.value
                val isVideo = mode.isVideoCapture(prefs.proIsVideoPref.value)
                val baseSelector = resolveBaseCameraSelector(provider, prefs.isFrontCameraPref.value)

                // Check CameraX Extensions for NIGHT, PORTRAIT, or PHOTO+HDR
                val finalSelector = resolveExtensionCameraSelector(extMgr, baseSelector, mode)

                val bindSuccess = tryBindUseCases(
                    provider = provider,
                    owner = owner,
                    pv = pv,
                    selector = finalSelector,
                    mode = mode,
                    isVideo = isVideo,
                    includeAssistAnalyzer = (mode == Mode.VIDEO || mode == Mode.PRO),
                    useHdr10Video = (isVideo && prefs.videoProfilePref.value == VideoProfile.HDR10)
                )

                if (!bindSuccess && finalSelector != baseSelector) {
                    onNotice("${mode.title} extension bind failed; falling back to standard capture.")
                    tryBindUseCases(
                        provider = provider,
                        owner = owner,
                        pv = pv,
                        selector = baseSelector,
                        mode = mode,
                        isVideo = isVideo,
                        includeAssistAnalyzer = (mode == Mode.VIDEO || mode == Mode.PRO),
                        useHdr10Video = false
                    )
                }
            } catch (t: Throwable) {
                onNotice("Camera initialization error: ${t.localizedMessage ?: "Unknown error"}")
            } finally {
                isBinding = false
            }
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
        useHdr10Video: Boolean
    ): Boolean {
        val previewBuilder = Preview.Builder()
            .setTargetRotation(targetRotation)

        if (isVideo) {
            val spec = prefs.videoSpecPref.value
            val fpsRange = if (prefs.autoFpsPref.value) {
                Range(24, spec.fps)
            } else {
                Range(spec.fps, spec.fps)
            }
            val pExt = Camera2Interop.Extender(previewBuilder)
            pExt.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            pExt.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, prefs.lockWbPref.value)
        }

        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(pv.surfaceProvider)
        }

        val ratioOption = if (isVideo) AspectRatioOption.RATIO_16_9 else prefs.aspectRatioPref.value
        val viewPort = ViewPort.Builder(ratioOption.rationalPortrait, targetRotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()

        val useCaseGroupBuilder = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)

        if (isVideo) {
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

            val fpsRange = if (prefs.autoFpsPref.value) {
                Range(24, spec.fps)
            } else {
                Range(spec.fps, spec.fps)
            }
            val vcExt = Camera2Interop.Extender(vcBuilder)
            vcExt.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
            vcExt.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, prefs.lockWbPref.value)

            val builtVc = vcBuilder.build()
            videoCapture = builtVc
            imageCapture = null
            useCaseGroupBuilder.addUseCase(builtVc)
        } else {
            val icBuilder = ImageCapture.Builder()
                .setTargetRotation(targetRotation)
                .setFlashMode(
                    when (prefs.flashPhotoPref.value) {
                        FlashPhotoMode.OFF -> ImageCapture.FLASH_MODE_OFF
                        FlashPhotoMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                        FlashPhotoMode.ON -> ImageCapture.FLASH_MODE_ON
                    }
                )

            if (mode == Mode.HIGH_RES) {
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
        }

        if (includeAssistAnalyzer) {
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetRotation(targetRotation)
                .build()
            analysis.setAnalyzer(
                analysisExecutor,
                AssistAnalyzer(
                    isHistogramEnabled = { prefs.histogramPref.value },
                    isZebraEnabled = { prefs.zebraPref.value },
                    isPeakingEnabled = { prefs.peakingPref.value },
                    onResult = { data ->
                        assistData = data
                    }
                )
            )
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
        } catch (e: Throwable) {
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
                    useHdr10Video = false
                )
            }
            if (includeAssistAnalyzer) {
                onNotice("Pro assist analyzer could not bind simultaneously; retrying without analyzer.")
                return tryBindUseCases(
                    provider = provider,
                    owner = owner,
                    pv = pv,
                    selector = selector,
                    mode = mode,
                    isVideo = isVideo,
                    includeAssistAnalyzer = false,
                    useHdr10Video = false
                )
            }
            false
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun onCameraBound(boundCam: Camera, mode: Mode, isVideo: Boolean) {
        // 1. Inspect Zoom bounds & filter supported pills (0.6, 1, 1.5, 2, 5)
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

        // 2. Inspect Camera2 characteristics for ISO, Boost, Focus distance, Tonemap Curve
        runCatching {
            val c2Info = Camera2CameraInfo.from(boundCam.cameraInfo)
            val sensRange = c2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            sensorIsoMin = sensRange?.lower ?: 100
            sensorIsoMax = sensRange?.upper ?: 3200

            val boostRange = c2Info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE
            )
            boostMax = boostRange?.upper ?: 100

            val calcMax = (sensorIsoMax.toLong() * boostMax.toLong() / 100L).toInt()
            effectiveIsoMax = min(409600, calcMax.coerceAtLeast(sensorIsoMax))
            proIsoValue = proIsoValue.coerceIn(sensorIsoMin, effectiveIsoMax)

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

        // 3. Apply torch if video mode
        if (isVideo) {
            boundCam.cameraControl.enableTorch(prefs.videoTorchPref.value)
        } else {
            boundCam.cameraControl.enableTorch(false)
        }

        // 4. Apply mode-specific Camera2 interop controls (SUPER MACRO, PRO, VIDEO Log/FPS/AWB)
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
            val builder = CaptureRequestOptions.Builder()

            // A. SUPER MACRO mode: AF off, focus distance = LENS_INFO_MINIMUM_FOCUS_DISTANCE
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
                // PRO Focus
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

                // PRO White Balance
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    proWbPreset.awbMode
                )

                // PRO ISO & Shutter
                val maxVideoShutterNs = 1_000_000_000L / 60L // 1/60s cap in video
                val effectiveShutterNs = if (isVideo) {
                    proShutterNs.coerceIn(125_000L, maxVideoShutterNs)
                } else {
                    proShutterNs.coerceIn(125_000L, 1_000_000_000L)
                }

                if (proIsoAuto && proShutterAuto) {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON
                    )
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
                        100
                    )
                } else {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_OFF
                    )
                    val targetIso = if (proIsoAuto) 400.coerceIn(sensorIsoMin, sensorIsoMax) else proIsoValue
                    if (targetIso <= sensorIsoMax) {
                        builder.setCaptureRequestOption(
                            CaptureRequest.SENSOR_SENSITIVITY,
                            targetIso.coerceIn(sensorIsoMin, sensorIsoMax)
                        )
                        builder.setCaptureRequestOption(
                            CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
                            100
                        )
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
                        if (isVideo) (1_000_000_000L / 60L) else (1_000_000_000L / 125L)
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
            } else {
                // Standard auto exposure, focus, WB for non-Pro/non-SuperMacro modes
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    if (isVideo) {
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    } else {
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    }
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_ON
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_MODE,
                    CameraMetadata.CONTROL_AWB_MODE_AUTO
                )
            }

            // B. Video controls: FPS range, AWB Lock, and Log Profile Tonemap Curve
            if (isVideo) {
                val spec = prefs.videoSpecPref.value
                val fpsRange = if (prefs.autoFpsPref.value) {
                    Range(24, spec.fps)
                } else {
                    Range(spec.fps, spec.fps)
                }
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    fpsRange
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AWB_LOCK,
                    prefs.lockWbPref.value
                )

                if (prefs.videoProfilePref.value == VideoProfile.LOG) {
                    if (supportsToneMapContrastCurve) {
                        val curve = buildLogTonemapCurve()
                        builder.setCaptureRequestOption(
                            CaptureRequest.TONEMAP_MODE,
                            CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE
                        )
                        builder.setCaptureRequestOption(
                            CaptureRequest.TONEMAP_CURVE,
                            curve
                        )
                    } else {
                        onNotice("Log tonemap curve (TONEMAP_MODE_CONTRAST_CURVE) is not supported by this camera sensor.")
                    }
                }
            }

            c2Control.setCaptureRequestOptions(builder.build())
        }
    }

    /**
     * Builds the custom flat logarithmic tonemap curve:
     * y = ln(1 + 16 * x) / ln(17)
     */
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
        val mode = prefs.modePref.value
        if (mode == Mode.SCAN_DOCUMENT) {
            onLaunchDocScanner()
            return
        }

        if (countdownRemaining > 0) {
            countdownJob?.cancel()
            countdownRemaining = 0
            return
        }

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
            onNotice("Photo capture not ready.")
            return
        }
        isCapturingPhoto = true

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = "IMG_$stamp"
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
                            onNotice("Photo saved, but URI was null.")
                            refreshGallery()
                            return
                        }

                        val activeFilter = prefs.filterPref.value
                        val isUnderwater = prefs.modePref.value == Mode.UNDERWATER

                        scope.launch {
                            runCatching {
                                Post.processSavedJpeg(
                                    context = context,
                                    uri = savedUri,
                                    filter = activeFilter,
                                    isUnderwater = isUnderwater,
                                    location = loc
                                )
                            }.onFailure {
                                onNotice("Post-filter warning: ${it.localizedMessage}")
                            }

                            refreshGallery()

                            if (prefs.aiEnabledPref.value) {
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
        val vc = videoCapture ?: run {
            onNotice("Video recorder not ready.")
            return
        }
        if (isRecording) return

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "VID_$stamp.mp4"
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
    }

    fun stopRecording() {
        runCatching {
            activeRecording?.stop()
        }
        isRecording = false
        recordingTimerJob?.cancel()
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
        analysisExecutor.shutdown()
    }
}
