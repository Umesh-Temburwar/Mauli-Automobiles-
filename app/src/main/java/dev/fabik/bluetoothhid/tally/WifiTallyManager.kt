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

enum class WifiStatus {
    DISCONNECTED,
    SEARCHING,
    CONNECTED,
    FAILED
}

/**
 * Manages wireless WiFi communication directly with TallyPrime on the PC.
 * Eliminates the need for Bluetooth pairing or Bluetooth HID drivers.
 * Supports auto-discovery via UDP broadcast on port 8888 and HTTP communication on port 8080.
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

    private val _lastMessage = MutableStateFlow("Tap 'Auto-Detect' to connect to PC over WiFi")
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
     * Auto-discovers PC via UDP broadcast, or falls back to pinging current/default IP.
     */
    fun discoverPc(scope: CoroutineScope, onResult: ((Boolean, String) -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            _wifiStatus.value = WifiStatus.SEARCHING
            _lastMessage.value = "Searching for PC on WiFi network..."

            var found = false
            var errorDetail = ""

            // 1. Try UDP Broadcast Discovery
            try {
                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val multicastLock = wifiManager?.createMulticastLock("MauliTallyDiscovery")
                multicastLock?.acquire()

                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 2500

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
                found = true

                socket.close()
                multicastLock?.release()
            } catch (e: Exception) {
                Log.d(TAG, "UDP discovery did not get reply: ${e.message}, falling back to direct ping...")
                errorDetail = e.message ?: "UDP timeout"
            }

            // 2. Fallback: Ping saved IP directly
            if (!found) {
                val pingSuccess = pingServerInternal(_serverIp.value)
                if (pingSuccess) {
                    found = true
                }
            }

            withContext(Dispatchers.Main) {
                if (found) {
                    onResult?.invoke(true, _lastMessage.value)
                } else {
                    _wifiStatus.value = WifiStatus.FAILED
                    _lastMessage.value = "PC not found! Ensure START_TALLY_WIFI_SERVER.bat is running on PC."
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
            _lastMessage.value = "Cannot reach $ip. Is PC server running?"
            false
        }
    }

    /**
     * Sends barcode and current Tally sequence steps to the PC over WiFi.
     * The PC types the barcode and shortcuts directly into the active TallyPrime window.
     */
    suspend fun sendBarcode(
        barcode: String,
        profile: TallyProfile?,
        onResult: ((Boolean, String) -> Unit)? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!_isWifiMode.value) {
            val msg = "WiFi mode is disabled"
            withContext(Dispatchers.Main) { onResult?.invoke(false, msg) }
            return@withContext Pair(false, msg)
        }

        val ip = _serverIp.value
        val port = _serverPort.value
        val url = URL("http://$ip:$port/api/tally/scan")

        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 4000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true

            val payload = JSONObject()
            payload.put("barcode", barcode)
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
                val successMsg = "Tally entered: $barcode (${profile?.name ?: "Direct"})"
                _lastMessage.value = successMsg
                withContext(Dispatchers.Main) { onResult?.invoke(true, successMsg) }
                Pair(true, successMsg)
            } else {
                val errMsg = "Server error ($responseCode)"
                _lastMessage.value = errMsg
                withContext(Dispatchers.Main) { onResult?.invoke(false, errMsg) }
                Pair(false, errMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "WiFi Send Failed: ${e.message ?: "Connection error"}"
            _wifiStatus.value = WifiStatus.FAILED
            _lastMessage.value = errorMsg
            withContext(Dispatchers.Main) { onResult?.invoke(false, errorMsg) }
            Pair(false, errorMsg)
        }
    }
}
