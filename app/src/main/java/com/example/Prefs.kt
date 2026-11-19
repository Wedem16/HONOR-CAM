package com.example

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraMetadata
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.reflect.KProperty

enum class Mode(
    val title: String,
    val isMoreSubMode: Boolean = false
) {
    NIGHT("NIGHT"),
    PORTRAIT("PORTRAIT"),
    PHOTO("PHOTO"),
    VIDEO("VIDEO"),
    PRO("PRO"),
    MORE("MORE"),
    UNDERWATER("UNDERWATER", isMoreSubMode = true),
    SUPER_MACRO("SUPER MACRO", isMoreSubMode = true),
    HIGH_RES("HIGH-RES", isMoreSubMode = true),
    SCAN_DOCUMENT("SCAN DOCUMENT", isMoreSubMode = true);

    fun isVideoCapture(proIsVideo: Boolean): Boolean {
        return this == VIDEO || (this == PRO && proIsVideo)
    }

    companion object {
        val mainStripModes = listOf(NIGHT, PORTRAIT, PHOTO, VIDEO, PRO, MORE)
        val moreSubModes = listOf(UNDERWATER, SUPER_MACRO, HIGH_RES, SCAN_DOCUMENT)
    }
}

enum class AspectRatioOption(
    val label: String,
    val widthToHeightPortrait: Float,
    val rationalPortrait: Rational
) {
    RATIO_4_3("4:3", 3f / 4f, Rational(3, 4)),
    RATIO_16_9("16:9", 9f / 16f, Rational(9, 16)),
    RATIO_1_1("1:1", 1f, Rational(1, 1));

    fun next(): AspectRatioOption {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

enum class VideoSpec(
    val label: String,
    val topBarBadge: String,
    val resTag: String,
    val width: Int,
    val height: Int,
    val fps: Int
) {
    SPEC_720P30("720p · 30fps", "720P · 30", "720p", 1280, 720, 30),
    SPEC_1080P30("1080p · 30fps", "1080 · 30", "1080p", 1920, 1080, 30),
    SPEC_1080P60("1080p · 60fps", "1080 · 60", "1080p", 1920, 1080, 60),
    SPEC_4K30("4K · 30fps", "4K · 30", "4K", 3840, 2160, 30),
    SPEC_4K60("4K · 60fps", "4K · 60", "4K", 3840, 2160, 60);

    fun next(): VideoSpec {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

enum class VideoProfile(
    val label: String,
    val shortBadge: String,
    val subtitle: String
) {
    STANDARD("Standard", "STD", "Standard 8-bit SDR rec.709"),
    HDR10("HDR (HLG 10-bit)", "HDR10", "10-bit HLG dynamic range (falls back to SDR)"),
    APPLE_LOG("Apple Log", "APPLE LOG", "Apple Log cinema transfer curve & flat color profile"),
    LOG("Log (Flat)", "LOG", "Custom contrast curve y = ln(1+16x)/ln(17)");

    val isLogProfile: Boolean
        get() = this == APPLE_LOG || this == LOG

    fun next(): VideoProfile {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

enum class PhotoFilter(
    val label: String
) {
    ORIGINAL("Original"),
    APPLE_LOG("Apple Log"),
    VIVID("Vivid"),
    MONO("Mono"),
    WARM("Warm"),
    COOL("Cool"),
    SEPIA("Sepia")
}

enum class FlashPhotoMode(
    val label: String
) {
    OFF("Off"),
    AUTO("Auto"),
    ON("On");

    fun next(): FlashPhotoMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

enum class WbPreset(
    val label: String,
    val awbMode: Int
) {
    AUTO("Auto", CameraMetadata.CONTROL_AWB_MODE_AUTO),
    INCANDESCENT("Incandescent", CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("Fluorescent", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT),
    DAYLIGHT("Daylight", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("Cloudy", CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT)
}

class Pref<T>(
    private val sp: SharedPreferences,
    val key: String,
    val default: T,
    private val reader: (SharedPreferences, String, T) -> T,
    private val writer: (SharedPreferences.Editor, String, T) -> Unit
) {
    private var state by mutableStateOf(reader(sp, key, default))

    var value: T
        get() = state
        set(newVal) {
            state = newVal
            val editor = sp.edit()
            writer(editor, key, newVal)
            editor.apply()
        }

    fun reset() {
        value = default
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    operator fun setValue(thisRef: Any?, property: KProperty<*>, newVal: T) {
        value = newVal
    }
}

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("honor_cam_prefs", Context.MODE_PRIVATE)

    private val allPrefs = mutableListOf<Pref<*>>()

    private fun <T> register(pref: Pref<T>): Pref<T> {
        allPrefs.add(pref)
        return pref
    }

    private fun boolPref(key: String, default: Boolean): Pref<Boolean> = register(
        Pref(
            sp = sp,
            key = key,
            default = default,
            reader = { prefs, k, d -> prefs.getBoolean(k, d) },
            writer = { ed, k, v -> ed.putBoolean(k, v) }
        )
    )

    private fun intPref(key: String, default: Int): Pref<Int> = register(
        Pref(
            sp = sp,
            key = key,
            default = default,
            reader = { prefs, k, d -> prefs.getInt(k, d) },
            writer = { ed, k, v -> ed.putInt(k, v) }
        )
    )

    private fun floatPref(key: String, default: Float): Pref<Float> = register(
        Pref(
            sp = sp,
            key = key,
            default = default,
            reader = { prefs, k, d -> prefs.getFloat(k, d) },
            writer = { ed, k, v -> ed.putFloat(k, v) }
        )
    )

    private fun stringPref(key: String, default: String): Pref<String> = register(
        Pref(
            sp = sp,
            key = key,
            default = default,
            reader = { prefs, k, d -> prefs.getString(k, d) ?: d },
            writer = { ed, k, v -> ed.putString(k, v) }
        )
    )

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): Pref<E> = register(
        Pref(
            sp = sp,
            key = key,
            default = default,
            reader = { prefs, k, d ->
                val raw = prefs.getString(k, d.name) ?: d.name
                runCatching { enumValueOf<E>(raw) }.getOrDefault(d)
            },
            writer = { ed, k, v -> ed.putString(k, v.name) }
        )
    )

    // Mode & Camera state
    val modePref = enumPref("mode", Mode.PHOTO)
    val lastMoreSubModePref = enumPref("last_more_sub_mode", Mode.UNDERWATER)
    val proIsVideoPref = boolPref("pro_is_video", false)
    val isFrontCameraPref = boolPref("is_front_camera", false)

    // PHOTO settings
    val aspectRatioPref = enumPref("aspect_ratio", AspectRatioOption.RATIO_4_3)
    val hdrPref = boolPref("photo_hdr", false)
    val filterPref = enumPref("photo_filter", PhotoFilter.ORIGINAL)
    val flashPhotoPref = enumPref("flash_photo", FlashPhotoMode.OFF)
    val videoTorchPref = boolPref("video_torch", false)

    // AI CAMERA settings
    private val defaultEnvKey: String = runCatching {
        val k = BuildConfig.OPENAI_API_KEY
        if (k.isNotBlank() && k != "YOUR_OPENAI_API_KEY") k else ""
    }.getOrDefault("")

    val aiEnabledPref = boolPref("ai_enabled", false)
    val aiKeepOriginalPref = boolPref("ai_keep_original", true)
    val aiApiKeyPref = stringPref("ai_api_key", defaultEnvKey)
    val aiModelPref = stringPref("ai_model", "gpt-image-1")
    val aiPromptPref = stringPref(
        "ai_prompt",
        DEFAULT_AI_PROMPT
    )

    // VIDEO settings
    val videoSpecPref = enumPref("video_spec", VideoSpec.SPEC_1080P60)
    val videoProfilePref = enumPref("video_profile", VideoProfile.STANDARD)
    val videoBitrateMbpsPref = intPref("video_bitrate_mbps", 0) // 0 = Auto, 0..200 Mbps
    val autoFpsPref = boolPref("video_auto_fps", false)
    val lockWbPref = boolPref("video_lock_wb", false)

    // PRO VIDEO ASSISTS settings
    val histogramPref = boolPref("assist_histogram", false)
    val zebraPref = boolPref("assist_zebra", false)
    val peakingPref = boolPref("assist_peaking", false)
    val logPreviewAssistPref = boolPref("assist_log_preview", false)

    // OTHER settings
    val timerSecPref = intPref("timer_sec", 0) // 0, 3, 5, 10
    val gridlinesPref = boolPref("gridlines", true)
    val levelPref = boolPref("level_line", false)
    val muteAudioPref = boolPref("mute_audio", false)
    val mirrorFrontPref = boolPref("mirror_front", true)
    val floatingShutterPref = boolPref("floating_shutter", false)
    val floatingShutterXPref = floatPref("floating_shutter_x", 0.8f)
    val floatingShutterYPref = floatPref("floating_shutter_y", 0.65f)
    val locationTagPref = boolPref("location_tag", false)

    fun cycleTimer() {
        timerSecPref.value = when (timerSecPref.value) {
            0 -> 3
            3 -> 5
            5 -> 10
            else -> 0
        }
    }

    fun restoreDefaults() {
        allPrefs.forEach { it.reset() }
    }

    companion object {
        const val DEFAULT_AI_PROMPT =
            "Enhance this photo: improve lighting, colour and sharpness. Do not change my face, features, body proportions, or the original background."
    }
}
