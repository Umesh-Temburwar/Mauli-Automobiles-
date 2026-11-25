package dev.fabik.bluetoothhid.tally

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.UUID

enum class WifiStatus {
    DISCONNECTED,
    SEARCHING,
    CONNECTED,
    FAILED
}

/**
 * Manages wireless Wi-Fi communication directly with TallyPrime on the PC.
 * Eliminates the need for Bluetooth pairing, Bluetooth drivers, or manual daily setup.
 *
 * Features:
 * - Auto-discovery via UDP broadcast on port 8888 (handles router reboots & dynamic IP changes).
 * - Automatic retry with re-discovery if the PC's IP address changes.
 * - UUID-based duplicate scan protection.
 * - Verified delivery acknowledgement directly from Tally receiver.
 */
class WifiTallyManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WifiTallyManager"
        private const val PREFS_NAME = "wifi_tally_prefs"
        private const val KEY_WIFI_MODE = "key_wifi_mode"
        private const val KEY_SERVER_IP = "key_server_ip"
        private const val KEY_SERVER_PORT = "key_server_port"
        private const val DEFAULT_IP = "192.168.1.104"
        private const val DEFAULT_PORT = 8080
        private const val UDP_DISCOVERY_PORT = 8888

        @Volatile
        private var instance: WifiTallyManager? = null

        fun getInstance(context: Context): WifiTallyManager {
            return instance ?: synchronized(this) {
                instance ?: WifiTallyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isWifiMode = MutableStateFlow(prefs.getBoolean(KEY_WIFI_MODE, true))
    val isWifiMode = _isWifiMode.asStateFlow()

    private val _serverIp = MutableStateFlow(prefs.getString(KEY_SERVER_IP, DEFAULT_IP) ?: DEFAULT_IP)
    val serverIp = _serverIp.asStateFlow()

    private val _serverPort = MutableStateFlow(prefs.getInt(KEY_SERVER_PORT, DEFAULT_PORT))
    val serverPort = _serverPort.asStateFlow()

    private val _wifiStatus = MutableStateFlow(WifiStatus.DISCONNECTED)
    val wifiStatus = _wifiStatus.asStateFlow()

    private val _pcHostname = MutableStateFlow<String?>(null)
    val pcHostname = _pcHostname.asStateFlow()

    private val _lastMessage = MutableStateFlow("Tap 'Auto-Detect' to connect to PC over Wi-Fi")
    val lastMessage = _lastMessage.asStateFlow()

    init {
        // Automatically check connection with the saved IP on startup
        CoroutineScope(Dispatchers.IO).launch {
            if (_isWifiMode.value) {
                discoverOrPingPc()
            }
        }
    }

    fun setWifiMode(enabled: Boolean) {
        _isWifiMode.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_MODE, enabled).apply()
        if (enabled && _wifiStatus.value != WifiStatus.CONNECTED) {
            CoroutineScope(Dispatchers.IO).launch {
                discoverOrPingPc()
            }
        }
    }

    fun setServerIp(ip: String) {
        val trimmed = ip.trim()
        _serverIp.value = trimmed
        prefs.edit().putString(KEY_SERVER_IP, trimmed).apply()
        CoroutineScope(Dispatchers.IO).launch {
            pingServer(trimmed)
        }
    }

    /**
     * Executes a fast, synchronous UDP discovery broadcast on port 8888.
     * Updates _serverIp and returns true if PC responded.
     */
    private fun runUdpDiscoverySync(): Boolean {
        var multicastLock: WifiManager.MulticastLock? = null
        var socket: DatagramSocket? = null
        return try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("MauliTallyDiscovery")
            multicastLock?.acquire()

            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 2000

            val sendData = "MAULI_TALLY_DISCOVER".toByteArray()
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddr, UDP_DISCOVERY_PORT)

            socket.send(sendPacket)

            val recvBuf = ByteArray(1024)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)

            val respJson = String(recvPacket.data, 0, recvPacket.length)
            val json = JSONObject(respJson)

            val discoveredIp = recvPacket.address.hostAddress ?: json.optString("ip", _serverIp.value)
            val hostname = json.optString("hostname", "Mauli PC")
            val port = json.optInt("port", DEFAULT_PORT)

            _serverIp.value = discoveredIp
            _serverPort.value = port
            _pcHostname.value = hostname
            _wifiStatus.value = WifiStatus.CONNECTED
            _lastMessage.value = "Connected to $hostname ($discoveredIp)"

            prefs.edit().putString(KEY_SERVER_IP, discoveredIp).putInt(KEY_SERVER_PORT, port).apply()
            true
        } catch (e: Exception) {
            Log.d(TAG, "UDP broadcast discovery failed: ${e.message}")
            false
        } finally {
            try { socket?.close() } catch (ignored: Exception) {}
            try { multicastLock?.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Auto-discovers PC via UDP broadcast, or falls back to pinging current/default IP.
     */
    fun discoverPc(scope: CoroutineScope, onResult: ((Boolean, String) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            _wifiStatus.value = WifiStatus.SEARCHING
            _lastMessage.value = "Searching for PC on Wi-Fi network..."

            var found = runUdpDiscoverySync()

            // Fallback: Ping saved IP directly
            if (!found) {
                found = pingServerInternal(_serverIp.value)
            }

            withContext(Dispatchers.Main) {
                if (found) {
                    onResult?.invoke(true, _lastMessage.value)
                } else {
                    _wifiStatus.value = WifiStatus.FAILED
                    _lastMessage.value = "PC not found! Ensure MauliTallyReceiver.exe is running on PC."
                    onResult?.invoke(false, _lastMessage.value)
                }
            }
        }
    }

    private suspend fun discoverOrPingPc() {
        _wifiStatus.value = WifiStatus.SEARCHING
        val directSuccess = pingServerInternal(_serverIp.value)
        if (!directSuccess) {
            // Run discovery if direct ping failed
            discoverPc(CoroutineScope(Dispatchers.IO))
        }
    }

    fun pingServer(ip: String, onResult: ((Boolean, String) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            _wifiStatus.value = WifiStatus.SEARCHING
            val success = pingServerInternal(ip)
            withContext(Dispatchers.Main) {
                onResult?.invoke(success, _lastMessage.value)
            }
        }
    }

    private fun pingServerInternal(ip: String): Boolean {
        return try {
            val url = URL("http://$ip:${_serverPort.value}/api/tally/ping")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 1800
            conn.readTimeout = 1800
            conn.requestMethod = "GET"

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()

                val json = JSONObject(resp)
                val pcName = json.optString("pc_name", "Tally PC")
                _pcHostname.value = pcName
                _wifiStatus.value = WifiStatus.CONNECTED
                _lastMessage.value = "Connected to $pcName ($ip)"
                true
            } else {
                _wifiStatus.value = WifiStatus.FAILED
                _lastMessage.value = "PC returned HTTP $responseCode"
                false
            }
        } catch (e: Exception) {
            _wifiStatus.value = WifiStatus.FAILED
            _lastMessage.value = "Cannot reach $ip. Is PC receiver running?"
            false
        }
    }

    /**
     * Internal helper to send the barcode payload over HTTP.
     */
    private fun sendHttpScan(
        ip: String,
        barcode: String,
        profile: TallyProfile?,
        scanId: String
    ): Pair<Boolean, String> {
        return try {
            val port = _serverPort.value
            val url = URL("http://$ip:$port/api/tally/scan")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 3500
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true

            val payload = JSONObject()
            payload.put("barcode", barcode)
            payload.put("scan_id", scanId)
            payload.put("profile", profile?.name ?: "Purchase Entry")

            if (profile != null && profile.steps.isNotEmpty()) {
                val stepsArray = JSONArray()
                for (step in profile.steps) {
                    val stepObj = JSONObject()
                    stepObj.put("type", step.type.name)
                    stepObj.put("key", step.key)
                    stepObj.put("delayMs", step.delayMs)
                    val modArray = JSONArray()
                    for (m in step.modifiers) modArray.put(m)
                    stepObj.put("modifiers", modArray)
                    stepObj.put("ctrl", step.modifiers.contains("CTRL"))
                    stepObj.put("alt", step.modifiers.contains("ALT"))
                    stepObj.put("shift", step.modifiers.contains("SHIFT"))
                    stepObj.put("textPayload", step.textPayload)
                    stepObj.put("isEnabled", step.isEnabled)
                    stepsArray.put(stepObj)
                }
                payload.put("steps", stepsArray)
            }

            val writer = OutputStreamWriter(conn.outputStream, "UTF-8")
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val resp = reader.readText()
                reader.close()

                _wifiStatus.value = WifiStatus.CONNECTED
                val successMsg = "Delivered into TallyPrime: $barcode"
                _lastMessage.value = successMsg
                Pair(true, successMsg)
            } else {
                val errMsg = "PC Server error ($responseCode)"
                _lastMessage.value = errMsg
                Pair(false, errMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "Wi-Fi Send Failed: ${e.message ?: "Connection error"}"
            _wifiStatus.value = WifiStatus.FAILED
            _lastMessage.value = errorMsg
            Pair(false, errorMsg)
        }
    }

    /**
     * Sends barcode and current Tally sequence steps to the PC over Wi-Fi.
     * Automatically handles dynamic IP changes (recovers via UDP broadcast on failure).
     */
    suspend fun sendBarcode(
        barcode: String,
        profile: TallyProfile?,
        onResult: ((Boolean, String) -> Unit)? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!_isWifiMode.value) {
            val msg = "Wi-Fi mode is disabled"
            withContext(Dispatchers.Main) { onResult?.invoke(false, msg) }
            return@withContext Pair(false, msg)
        }

        val scanId = UUID.randomUUID().toString()

        // 1. Attempt send with current IP
        val firstAttempt = sendHttpScan(_serverIp.value, barcode, profile, scanId)
        if (firstAttempt.first) {
            withContext(Dispatchers.Main) { onResult?.invoke(true, firstAttempt.second) }
            return@withContext firstAttempt
        }

        // 2. If first attempt failed (e.g. router rebooted or PC changed dynamic IP):
        // Automatically run instant UDP discovery and retry once!
        Log.w(TAG, "First send attempt to ${_serverIp.value} failed. Auto-recovering via UDP discovery...")
        val rediscovered = runUdpDiscoverySync()
        if (rediscovered) {
            Log.i(TAG, "PC re-discovered at new IP: ${_serverIp.value}. Retrying barcode send...")
            val retryAttempt = sendHttpScan(_serverIp.value, barcode, profile, scanId)
            withContext(Dispatchers.Main) {
                onResult?.invoke(retryAttempt.first, retryAttempt.second)
            }
            return@withContext retryAttempt
        }

        // Neither attempt succeeded
        withContext(Dispatchers.Main) { onResult?.invoke(false, firstAttempt.second) }
        firstAttempt
    }

    /**
     * Sends a single raw key action directly into Tally over Wi-Fi.
     * Used by the Tally Wireless Keyboard remote screen.
     */
    suspend fun sendDirectKeySync(step: TallyStep): Boolean = withContext(Dispatchers.IO) {
        if (!_isWifiMode.value) return@withContext false
        val dummyProfile = TallyProfile(
            id = "keyboard_direct",
            name = "KeyboardDirect",
            steps = listOf(step)
        )
        val result = sendBarcode("", dummyProfile)
        result.first
    }

    /**
     * Sends custom text or barcode string directly into Tally over Wi-Fi.
     */
    suspend fun sendTextSync(text: String, withEnter: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!_isWifiMode.value) return@withContext false
        val steps = mutableListOf(
            TallyStep(type = TallyActionType.TYPE_TEXT, textPayload = text, delayMs = 30L)
        )
        if (withEnter) {
            steps.add(TallyStep(type = TallyActionType.PRESS_ENTER, delayMs = 30L))
        }
        val dummyProfile = TallyProfile(
            id = "keyboard_text",
            name = "KeyboardText",
            steps = steps
        )
        val result = sendBarcode(text, dummyProfile)
        result.first
    }
}
