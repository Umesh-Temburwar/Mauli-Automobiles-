package dev.fabik.bluetoothhid.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object MauliImageLoader {

    // 1. In-Memory LRU Cache (Up to 1/8th of available runtime memory)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(4096, 32768)
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Loads a product image with 2-tier caching:
     * 1. Memory Cache
     * 2. Local Disk storage (offline thumbnail or imported image pack)
     * 3. Disk Cache
     * 4. Network fetch (when online)
     */
    suspend fun loadImage(
        context: Context,
        url: String?,
        localPath: String? = null
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = localPath?.takeIf { it.isNotBlank() } ?: url?.takeIf { it.isNotBlank() } ?: return@withContext null

        // A. Check Memory Cache
        memoryCache.get(key)?.let { return@withContext it }

        // B. Check Local Offline Assets / FilesDir
        if (!localPath.isNullOrBlank()) {
            val localFile1 = File(context.filesDir, localPath)
            if (localFile1.exists()) {
                BitmapFactory.decodeFile(localFile1.absolutePath)?.let {
                    memoryCache.put(key, it)
                    return@withContext it
                }
            }
            val packFile = File(context.filesDir, "image_pack/${File(localPath).name}")
            if (packFile.exists()) {
                BitmapFactory.decodeFile(packFile.absolutePath)?.let {
                    memoryCache.put(key, it)
                    return@withContext it
                }
            }
        }

        // C. Check Local Disk Cache Directory
        val cacheDir = File(context.cacheDir, "product_images").apply { mkdirs() }
        val diskCacheFile = File(cacheDir, key.hashCode().toString() + ".cache")
        if (diskCacheFile.exists()) {
            BitmapFactory.decodeFile(diskCacheFile.absolutePath)?.let {
                memoryCache.put(key, it)
                return@withContext it
            }
        }

        // D. Network Download (if valid HTTP/HTTPS URL)
        if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "MauliBarcodeScanner/2.0")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        if (bitmap != null) {
                            // Save to disk cache
                            try {
                                FileOutputStream(diskCacheFile).use { out ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                                }
                            } catch (_: Exception) {}

                            memoryCache.put(key, bitmap)
                            return@withContext bitmap
                        }
                    }
                }
            } catch (e: Exception) {
                // Network unreachable or timeout -> will fall back to technical placeholder
            }
        }

        null
    }

    /**
     * Generates a clean technical OEM placeholder bitmap when an image is unavailable.
     */
    fun createOemPlaceholder(
        brand: String,
        partNumber: String,
        category: String,
        width: Int = 300,
        height: Int = 300
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val isHero = brand.contains("hero", ignoreCase = true)
        val headerColor = if (isHero) Color.rgb(220, 38, 38) else Color.rgb(37, 99, 235)

        // Background
        canvas.drawColor(Color.rgb(248, 250, 252))

        val borderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(6f, 6f, width - 6f, height - 6f), 12f, 12f, borderPaint)

        // Header bar
        val headerPaint = Paint().apply {
            color = headerColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(6f, 6f, width - 6f, 64f), 12f, 12f, headerPaint)
        canvas.drawRect(RectF(6f, 40f, width - 6f, 64f), headerPaint)

        val brandPaint = Paint().apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(brand.uppercase(), 18f, 32f, brandPaint)

        val genuinePaint = Paint().apply {
            color = Color.rgb(254, 226, 226)
            textSize = 9.5f
            isAntiAlias = true
        }
        canvas.drawText("GENUINE AUTOMOBILE SPARES", 18f, 48f, genuinePaint)

        // Category Tag
        val catBoxPaint = Paint().apply {
            color = Color.rgb(224, 231, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(18f, 82f, 150f, 106f), 6f, 6f, catBoxPaint)

        val catTextPaint = Paint().apply {
            color = Color.rgb(30, 58, 138)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(category.take(18).uppercase(), 26f, 98f, catTextPaint)

        // OEM Part Number
        val pnLabelPaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 10f
            isAntiAlias = true
        }
        canvas.drawText("OEM PART NUMBER:", 18f, 136f, pnLabelPaint)

        val pnValuePaint = Paint().apply {
            color = Color.rgb(194, 65, 12)
            textSize = 13.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(partNumber, 18f, 156f, pnValuePaint)

        // Quality Verified Stamp
        val stampBox = Paint().apply {
            color = Color.rgb(241, 245, 249)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(18f, 210f, width - 18f, 280f), 8f, 8f, stampBox)

        val stampText = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("MAULI AUTOMOBILES", (width / 2).toFloat(), 242f, stampText)
        val subStamp = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("VERIFIED PRODUCT RECORD", (width / 2).toFloat(), 262f, subStamp)

        return bitmap
    }

    /**
     * Calculates the current disk cache size in bytes.
     */
    fun getImageCacheSize(context: Context): Long {
        val cacheDir = File(context.cacheDir, "product_images")
        if (!cacheDir.exists()) return 0L
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Clears disk cache and memory cache.
     */
    fun clearImageCache(context: Context) {
        memoryCache.evictAll()
        val cacheDir = File(context.cacheDir, "product_images")
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Imports an offline ZIP image package into app storage.
     */
    suspend fun importOfflineImageZip(context: Context, zipStream: InputStream): Int = withContext(Dispatchers.IO) {
        var count = 0
        val targetDir = File(context.filesDir, "image_pack").apply { mkdirs() }
        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            val buffer = ByteArray(8192)
            while (entry != null) {
                if (!entry.isDirectory && (entry.name.endsWith(".png") || entry.name.endsWith(".jpg") || entry.name.endsWith(".webp"))) {
                    val outFile = File(targetDir, File(entry.name).name)
                    FileOutputStream(outFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                    count++
                }
                entry = zis.nextEntry
            }
        }
        count
    }
}

/**
 * High-performance Composable for displaying product images with 2-tier caching
 * and automatic OEM fallback placeholders.
 */
@Composable
fun ProductImageView(
    imageUrl: String?,
    localPath: String? = null,
    productName: String = "",
    partNumber: String = "",
    brand: String = "Hero",
    category: String = "Spare Part",
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var bitmap by remember(imageUrl, localPath) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUrl, localPath) { mutableStateOf(true) }

    LaunchedEffect(imageUrl, localPath) {
        isLoading = true
        val loaded = MauliImageLoader.loadImage(context, imageUrl, localPath)
        if (loaded != null) {
            bitmap = loaded
        } else {
            // Create technical OEM placeholder
            bitmap = MauliImageLoader.createOemPlaceholder(
                brand = brand,
                partNumber = partNumber.ifBlank { "OEM-PART" },
                category = category
            )
        }
        isLoading = false
    }

    val boxModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = boxModifier.clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = productName,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Gray,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
