package dev.fabik.bluetoothhid.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.fabik.bluetoothhid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val description: String,
    val apkUrl: String
)

object UpdateManager {
    private const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/Umesh-Temburwar/Mauli-Automobiles-/main/version.json"
    private const val GITHUB_RELEASES_API =
        "https://api.github.com/repos/Umesh-Temburwar/Mauli-Automobiles-/releases/latest"

    /**
     * Checks if a newer version is available.
     * Returns AppUpdate if a newer versionCode or versionName exists, null otherwise.
     */
    suspend fun checkForUpdate(currentVersionCode: Int = BuildConfig.VERSION_CODE): AppUpdate? =
        withContext(Dispatchers.IO) {
            try {
                // 1. First check the fast raw version.json (no rate limits)
                val updateFromRaw = checkRawVersionJson(currentVersionCode)
                if (updateFromRaw != null) return@withContext updateFromRaw

                // 2. Fallback to GitHub Releases API
                return@withContext checkGitHubReleases(currentVersionCode)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun checkRawVersionJson(currentVersionCode: Int): AppUpdate? {
        val conn = (URL(VERSION_JSON_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            requestMethod = "GET"
            useCaches = false
        }

        try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val remoteCode = json.optInt("versionCode", 0)
                val remoteName = json.optString("versionName", "")
                val title = json.optString("title", "Mauli Automobiles Update")
                val desc = json.optString("description", "नया अपडेट उपलब्ध है।")
                val apkUrl = json.optString("apkUrl", "")

                if (remoteCode > currentVersionCode && apkUrl.isNotBlank()) {
                    return AppUpdate(
                        versionCode = remoteCode,
                        versionName = remoteName,
                        title = title,
                        description = desc,
                        apkUrl = apkUrl
                    )
                }
            }
        } finally {
            conn.disconnect()
        }
        return null
    }

    private fun checkGitHubReleases(currentVersionCode: Int): AppUpdate? {
        val conn = (URL(GITHUB_RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Mauli-Automobiles-App")
            useCaches = false
        }

        try {
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "").removePrefix("v").trim()
                val body = json.optString("body", "नया अपडेट उपलब्ध है।")

                var apkDownloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (tagName.isNotBlank() && apkDownloadUrl.isNotBlank() && tagName != BuildConfig.VERSION_NAME) {
                    return AppUpdate(
                        versionCode = currentVersionCode + 1,
                        versionName = tagName,
                        title = "Mauli Automobiles v$tagName",
                        description = body,
                        apkUrl = apkDownloadUrl
                    )
                }
            }
        } finally {
            conn.disconnect()
        }
        return null
    }

    /**
     * Downloads the APK file to cache and reports progress (0.0 to 1.0).
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var currentUrl = apkUrl
        var redirects = 0
        val maxRedirects = 6

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            try {
                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    currentUrl = conn.getHeaderField("Location") ?: return@withContext null
                    redirects++
                    continue
                }

                if (status == HttpURLConnection.HTTP_OK) {
                    val fileLength = conn.contentLength
                    val outputFile = File(context.cacheDir, "mauli_update.apk")
                    if (outputFile.exists()) outputFile.delete()

                    conn.inputStream.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(8192)
                            var totalBytesRead = 0L
                            var bytesRead: Int

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (fileLength > 0) {
                                    val progress = totalBytesRead.toFloat() / fileLength
                                    withContext(Dispatchers.Main) {
                                        onProgress(progress.coerceIn(0f, 1f))
                                    }
                                }
                            }
                        }
                    }
                    return@withContext outputFile
                }
            } finally {
                conn.disconnect()
            }
            break
        }
        return@withContext null
    }

    /**
     * Prompts Android package installer to install the APK directly over the old app.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "अपडेट करने के लिए 'Allow from this source' चालू करें",
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                "इन्स्टॉलेशन एरर: ${e.localizedMessage ?: "फ़ाइल नहीं खुल सकी"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/**
 * Beautiful Material 3 In-App Update Dialog.
 */
@Composable
fun UpdateDialog(
    update: AppUpdate,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
        },
        icon = {
            Icon(
                if (isDownloading) Icons.Default.CloudDownload else Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "नया अपडेट उपलब्ध है! 🚀",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "वर्जन: ${update.versionName} (वर्तमान: ${BuildConfig.VERSION_NAME})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = update.description,
                    style = MaterialTheme.typography.bodyMedium
                )

                AnimatedVisibility(visible = isDownloading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "डाउनलोड हो रहा है: ${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "कृपया प्रतीक्षा करें, डाउनलोड होने के बाद अपने-आप अपडेट खुलेगा।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isDownloading) {
                        isDownloading = true
                        errorMessage = null
                        scope.launch {
                            val file = UpdateManager.downloadApk(
                                context,
                                update.apkUrl
                            ) { progress ->
                                downloadProgress = progress
                            }
                            isDownloading = false
                            if (file != null && file.exists()) {
                                UpdateManager.installApk(context, file)
                                onDismiss()
                            } else {
                                errorMessage = "डाउनलोड विफल रहा। कृपया इंटरनेट कनेक्शन जाँचें।"
                            }
                        }
                    }
                },
                enabled = !isDownloading,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isDownloading) "डाउनलोड हो रहा है..." else "अभी अपडेट करें")
            }
        },
        dismissButton = {
            if (!isDownloading) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("बाद में")
                }
            }
        }
    )
}
