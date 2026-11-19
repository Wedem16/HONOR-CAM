package com.example

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class CapturedMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val isPdf: Boolean,
    val dateAddedSec: Long
)

object Post {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun filterColorMatrix(filter: PhotoFilter): ColorMatrix {
        return when (filter) {
            PhotoFilter.ORIGINAL -> ColorMatrix()
            PhotoFilter.APPLE_LOG -> {
                // Apple Log flat cinema color profile: lifted shadows, compressed highlights, 0.58 saturation
                val cm = ColorMatrix()
                cm.setSaturation(0.58f)
                val logLift = ColorMatrix(
                    floatArrayOf(
                        0.68f, 0f, 0f, 0f, 38f,
                        0f, 0.68f, 0f, 0f, 38f,
                        0f, 0f, 0.68f, 0f, 38f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(logLift)
                cm
            }
            PhotoFilter.VIVID -> {
                val cm = ColorMatrix()
                cm.setSaturation(1.45f)
                val contrast = ColorMatrix(
                    floatArrayOf(
                        1.12f, 0f, 0f, 0f, -12f,
                        0f, 1.12f, 0f, 0f, -12f,
                        0f, 0f, 1.12f, 0f, -12f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrast)
                cm
            }
            PhotoFilter.MONO -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val contrast = ColorMatrix(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, -10f,
                        0f, 1.1f, 0f, 0f, -10f,
                        0f, 0f, 1.1f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrast)
                cm
            }
            PhotoFilter.WARM -> ColorMatrix(
                floatArrayOf(
                    1.14f, 0f, 0f, 0f, 10f,
                    0f, 1.03f, 0f, 0f, 4f,
                    0f, 0f, 0.88f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilter.COOL -> ColorMatrix(
                floatArrayOf(
                    0.88f, 0f, 0f, 0f, -8f,
                    0f, 1.02f, 0f, 0f, 2f,
                    0f, 0f, 1.16f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            PhotoFilter.SEPIA -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val sepia = ColorMatrix(
                    floatArrayOf(
                        1.18f, 0f, 0f, 0f, 18f,
                        0f, 0.98f, 0f, 0f, 6f,
                        0f, 0f, 0.76f, 0f, -16f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(sepia)
                cm
            }
        }
    }

    /**
     * Underwater red-boost ColorMatrix compensating for water's cyan/blue absorption.
     */
    fun underwaterRedBoostMatrix(): ColorMatrix {
        return ColorMatrix(
            floatArrayOf(
                1.42f, 0.12f, 0.0f, 0f, 16f,
                0.0f, 1.02f, 0.0f, 0f, 0f,
                0.0f, -0.06f, 0.86f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    /**
     * Document scanner high-contrast clarity ColorMatrix for crisp text & paper.
     */
    fun documentScanEnhanceMatrix(): ColorMatrix {
        return ColorMatrix(
            floatArrayOf(
                1.32f, 0f, 0f, 0f, -24f,
                0f, 1.32f, 0f, 0f, -24f,
                0f, 0f, 1.32f, 0f, -24f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    /**
     * Log preview assist contrast + saturation ColorMatrix applied only to the preview TextureView.
     */
    fun logPreviewAssistMatrix(): ColorMatrix {
        val cm = ColorMatrix()
        cm.setSaturation(1.32f)
        val contrast = ColorMatrix(
            floatArrayOf(
                1.38f, 0f, 0f, 0f, -42f,
                0f, 1.38f, 0f, 0f, -42f,
                0f, 0f, 1.38f, 0f, -42f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrast)
        return cm
    }

    /**
     * Reads an image from [uri], respects its EXIF orientation, applies [filter] and/or
     * [isUnderwater] red-boost ColorMatrix, writes back to [uri], and preserves EXIF tags
     * (including optional [location]).
     */
    suspend fun processSavedJpeg(
        context: Context,
        uri: Uri,
        filter: PhotoFilter,
        isUnderwater: Boolean,
        location: Location?
    ) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val needFilter = filter != PhotoFilter.ORIGINAL || isUnderwater

        val tempFile = File.createTempFile("exif_src_", ".jpg", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext

            val srcExif = ExifInterface(tempFile.absolutePath)
            val orientation = srcExif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            if (needFilter || orientation != ExifInterface.ORIENTATION_NORMAL) {
                val rawBitmap = BitmapFactory.decodeFile(tempFile.absolutePath) ?: return@withContext
                val orientedBitmap = applyExifOrientation(rawBitmap, orientation)
                if (orientedBitmap !== rawBitmap) {
                    rawBitmap.recycle()
                }

                val finalBitmap = if (needFilter) {
                    val combinedMatrix = ColorMatrix()
                    if (isUnderwater) {
                        combinedMatrix.postConcat(underwaterRedBoostMatrix())
                    }
                    if (filter != PhotoFilter.ORIGINAL) {
                        combinedMatrix.postConcat(filterColorMatrix(filter))
                    }
                    applyColorMatrix(orientedBitmap, combinedMatrix).also {
                        if (it !== orientedBitmap) orientedBitmap.recycle()
                    }
                } else {
                    orientedBitmap
                }

                FileOutputStream(tempFile).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                finalBitmap.recycle()
            }

            val dstExif = ExifInterface(tempFile.absolutePath)
            dstExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            if (location != null) {
                dstExif.setGpsInfo(location)
            }
            dstExif.saveAttributes()

            resolver.openOutputStream(uri, "wt")?.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Converts a captured document JPEG in DCIM/AiCam into a high-contrast scanned document
     * and generates a matching PDF in Documents/AiCam.
     */
    suspend fun convertPhotoToDocumentPdf(
        context: Context,
        jpegUri: Uri
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val tempFile = File.createTempFile("doc_scan_", ".jpg", context.cacheDir)
            try {
                resolver.openInputStream(jpegUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("Could not open captured document image")

                val exif = ExifInterface(tempFile.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val raw = BitmapFactory.decodeFile(tempFile.absolutePath)
                    ?: error("Could not decode captured document image")
                val oriented = applyExifOrientation(raw, orientation)
                if (oriented !== raw) raw.recycle()

                val enhanced = applyColorMatrix(oriented, documentScanEnhanceMatrix())
                if (enhanced !== oriented) oriented.recycle()

                // Write enhanced JPEG back to DCIM/AiCam
                resolver.openOutputStream(jpegUri, "wt")?.use { out ->
                    enhanced.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }

                // Generate PDF in Documents/AiCam
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val pdfName = "SCAN_${stamp}.pdf"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, pdfName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOCUMENTS}/AiCam"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val pdfUri = resolver.insert(collection, values)
                    ?: error("Failed to create PDF in Documents/AiCam")

                val pdfDoc = PdfDocument()
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        enhanced.width,
                        enhanced.height,
                        1
                    ).create()
                    val page = pdfDoc.startPage(pageInfo)
                    page.canvas.drawBitmap(enhanced, 0f, 0f, null)
                    pdfDoc.finishPage(page)

                    resolver.openOutputStream(pdfUri)?.use { out ->
                        pdfDoc.writeTo(out)
                    }
                } finally {
                    pdfDoc.close()
                    enhanced.recycle()
                }

                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(pdfUri, done, null, null)
                pdfUri
            } finally {
                tempFile.delete()
            }
        }
    }

    fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun applyColorMatrix(source: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    fun downscaleBitmap(source: Bitmap, maxDimension: Int = 2048): Bitmap {
        val w = source.width
        val h = source.height
        val longest = max(w, h)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest.toFloat()
        val newW = (w * scale).roundToInt().coerceAtLeast(1)
        val newH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    /**
     * Calls https://api.openai.com/v1/images/edits with multipart/form-data:
     * - model (default gpt-image-1)
     * - prompt
     * - output_format=jpeg
     * - image downscaled to 2048 px
     * Saves the result as <originalName>_AI.jpg in DCIM/AiCam and deletes original if !keepOriginal.
     */
    suspend fun enhancePhotoWithOpenAi(
        context: Context,
        sourceUri: Uri,
        baseName: String,
        apiKey: String,
        model: String,
        prompt: String,
        keepOriginal: Boolean,
        location: Location?
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = apiKey.trim()
            require(cleanKey.isNotEmpty()) { "OpenAI API key is empty. Set it in Settings → AI Camera." }

            val resolver = context.contentResolver
            val decoded = resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: error("Unable to decode captured photo for AI enhancement.")

            val scaled = downscaleBitmap(decoded, 2048)
            val jpegBytes = ByteArrayOutputStream().use { bos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                bos.toByteArray()
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model.trim().ifEmpty { "gpt-image-1" })
                .addFormDataPart("prompt", prompt.trim().ifEmpty { Prefs.DEFAULT_AI_PROMPT })
                .addFormDataPart("output_format", "jpeg")
                .addFormDataPart(
                    "image",
                    "${baseName}.jpg",
                    jpegBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/images/edits")
                .header("Authorization", "Bearer $cleanKey")
                .post(multipartBody)
                .build()

            val responseBytes: ByteArray = httpClient.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    val errMsg = runCatching {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    error("OpenAI HTTP ${resp.code}: ${errMsg ?: bodyStr.take(180)}")
                }
                val json = JSONObject(bodyStr)
                val firstItem = json.optJSONArray("data")?.optJSONObject(0)
                    ?: error("OpenAI response missing data[0]")
                val b64 = firstItem.optString("b64_json", "")
                if (b64.isNotEmpty()) {
                    Base64.decode(b64, Base64.DEFAULT)
                } else {
                    val imgUrl = firstItem.optString("url", "")
                    require(imgUrl.isNotEmpty()) { "OpenAI response contained neither b64_json nor url" }
                    val dlReq = Request.Builder().url(imgUrl).get().build()
                    httpClient.newCall(dlReq).execute().use { dlResp ->
                        if (!dlResp.isSuccessful) error("Failed to download AI image (${dlResp.code})")
                        dlResp.body?.bytes() ?: error("Empty AI image body")
                    }
                }
            }

            val aiFileName = "${baseName}_AI.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, aiFileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/AiCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val aiUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Failed to create MediaStore entry for $aiFileName")

            resolver.openOutputStream(aiUri)?.use { out ->
                out.write(responseBytes)
            }

            if (location != null) {
                val tmp = File.createTempFile("ai_exif_", ".jpg", context.cacheDir)
                try {
                    tmp.writeBytes(responseBytes)
                    val exif = ExifInterface(tmp.absolutePath)
                    exif.setGpsInfo(location)
                    exif.saveAttributes()
                    resolver.openOutputStream(aiUri, "wt")?.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    }
                } finally {
                    tmp.delete()
                }
            }

            val doneValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(aiUri, doneValues, null, null)

            if (!keepOriginal) {
                runCatching { resolver.delete(sourceUri, null, null) }
            }

            aiUri
        }
    }

    /**
     * Copies a scanned PDF Uri from ML Kit Document Scanner to Documents/AiCam via MediaStore.
     */
    suspend fun saveScannedPdfToDocuments(
        context: Context,
        sourcePdfUri: Uri
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "SCAN_${stamp}.pdf"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOCUMENTS}/AiCam"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val destUri = resolver.insert(collection, values)
                ?: error("Unable to insert PDF into Documents/AiCam")

            resolver.openInputStream(sourcePdfUri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read scanned PDF source")

            val done = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(destUri, done, null, null)
            destUri
        }
    }

    /**
     * Copies a scanned JPEG page Uri from ML Kit Document Scanner to DCIM/AiCam via MediaStore.
     */
    suspend fun saveScannedJpegToDcim(
        context: Context,
        sourceJpegUri: Uri,
        pageIndex: Int
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "SCAN_${stamp}_P${pageIndex + 1}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/AiCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to insert JPEG into DCIM/AiCam")

            resolver.openInputStream(sourceJpegUri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to read scanned JPEG page")

            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(destUri, done, null, null)
            destUri
        }
    }

    /**
     * Loads recent photos (DCIM/AiCam), videos (Movies/AiCam), and scanned PDFs (Documents/AiCam).
     */
    suspend fun loadAiCamMedia(context: Context, limit: Int = 50): List<CapturedMedia> =
        withContext(Dispatchers.IO) {
            val resolver: ContentResolver = context.contentResolver
            val results = mutableListOf<CapturedMedia>()

            // 1. Photos in DCIM/AiCam
            runCatching {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.RELATIVE_PATH
                )
                val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%AiCam%")
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "IMG.jpg"
                        val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                        val date = cursor.getLong(dateCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        results.add(
                            CapturedMedia(
                                uri = contentUri,
                                displayName = name,
                                mimeType = mime,
                                isVideo = false,
                                isPdf = false,
                                dateAddedSec = date
                            )
                        )
                        count++
                    }
                }
            }

            // 2. Videos in Movies/AiCam
            runCatching {
                val projection = arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.MIME_TYPE,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.RELATIVE_PATH
                )
                val selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%AiCam%")
                val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "VID.mp4"
                        val mime = cursor.getString(mimeCol) ?: "video/mp4"
                        val date = cursor.getLong(dateCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        results.add(
                            CapturedMedia(
                                uri = contentUri,
                                displayName = name,
                                mimeType = mime,
                                isVideo = true,
                                isPdf = false,
                                dateAddedSec = date
                            )
                        )
                        count++
                    }
                }
            }

            // 3. Scanned PDFs in Documents/AiCam
            runCatching {
                val filesUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Files.FileColumns.RELATIVE_PATH
                )
                val selection =
                    "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
                val selectionArgs = arrayOf("%AiCam%", "application/pdf")
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                resolver.query(
                    filesUri,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val mimeCol =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val dateCol =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    var count = 0
                    while (cursor.moveToNext() && count < limit) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "SCAN.pdf"
                        val mime = cursor.getString(mimeCol) ?: "application/pdf"
                        val date = cursor.getLong(dateCol)
                        val contentUri = ContentUris.withAppendedId(filesUri, id)
                        results.add(
                            CapturedMedia(
                                uri = contentUri,
                                displayName = name,
                                mimeType = mime,
                                isVideo = false,
                                isPdf = true,
                                dateAddedSec = date
                            )
                        )
                        count++
                    }
                }
            }

            results.sortedByDescending { it.dateAddedSec }.take(limit)
        }
}
