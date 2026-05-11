package com.open.wuling.data.mqtt

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.open.wuling.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mqttStore by preferencesDataStore(name = "mqtt_prefs")

private val KEY_MQTT_ENABLED = booleanPreferencesKey("mqtt_enabled")
private val KEY_MQTT_BROKER = stringPreferencesKey("mqtt_broker")
private val KEY_MQTT_CLIENT_ID = stringPreferencesKey("mqtt_client_id")
private val KEY_MQTT_USERNAME = stringPreferencesKey("mqtt_username")
private val KEY_MQTT_PASSWORD = stringPreferencesKey("mqtt_password")
private val KEY_MQTT_VIN = stringPreferencesKey("mqtt_vin")

@Singleton
class VehicleMqttManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "MqttManager"
        private const val MAX_RECONNECT_DELAY = 60_000L
        private const val BASE_RECONNECT_DELAY = 2_000L
        private const val MAX_RETRIES = 10

        // ── Protobuf 解析 ──────────────────────────────────
        fun parseProtobuf(bytes: ByteArray): Map<Int, String> {
            val result = LinkedHashMap<Int, String>()
            var pos = 0
            while (pos < bytes.size) {
                var tag = 0L; var shift = 0
                while (pos < bytes.size) {
                    val b = bytes[pos++]; tag = tag or ((b.toLong() and 0x7f) shl shift)
                    shift += 7; if ((b.toInt() and 0x80) == 0) break
                }
                val fn = (tag ushr 3).toInt()
                when ((tag and 7).toInt()) {
                    0 -> {
                        var v = 0L; var s = 0
                        while (pos < bytes.size) {
                            val b = bytes[pos++]
                            v = v or (((b.toInt() and 0xff).toLong() and 0x7f) shl s)
                            s += 7; if ((b.toInt() and 0x80) == 0) break
                        }
                        result[fn] = v.toString()
                    }
                    2 -> {
                        var len = 0; var ls = 0
                        while (pos < bytes.size) {
                            val b = bytes[pos++]; len = len or ((b.toInt() and 0x7f) shl ls)
                            ls += 7; if ((b.toInt() and 0x80) == 0) break
                        }
                        result[fn] = if (pos + len <= bytes.size) {
                            val chunk = bytes.copyOfRange(pos, pos + len); pos += len
                            try { String(chunk, Charsets.UTF_8) } catch (_: Exception) { "" }
                        } else { pos = bytes.size; "" }
                    }
                    else -> { result[fn] = "?"; break }
                }
            }
            return result
        }

        private fun safeUtf8(b: ByteArray) = try { String(b, Charsets.UTF_8) } catch (_: Exception) { "" }
    }

    // ── DataStore ───────────────────────────────────────────
    private val ds = appContext.mqttStore.data
    private fun <T> prefFlow(key: Preferences.Key<T>, default: T): Flow<T> =
        ds.map { it[key] ?: default }

    private suspend fun <T> prefGet(key: Preferences.Key<T>): T? =
        ds.map { it[key] }.first()

    val enabledFlow: Flow<Boolean> = prefFlow(KEY_MQTT_ENABLED, false)

    private suspend fun editStore(block: suspend (MutablePreferences) -> Unit) {
        appContext.mqttStore.edit(block)
    }

    // ── 凭据读写 ────────────────────────────────────────────
    suspend fun isEnabled(): Boolean = prefGet(KEY_MQTT_ENABLED) ?: false

    suspend fun isConfigured(): Boolean {
        val c = prefGet(KEY_MQTT_CLIENT_ID) ?: ""
        val u = prefGet(KEY_MQTT_USERNAME) ?: ""
        val p = prefGet(KEY_MQTT_PASSWORD) ?: ""
        return c.isNotEmpty() && u.isNotEmpty() && p.isNotEmpty()
    }

    suspend fun getCurrentCredentials(): MqttConfig {
        return MqttConfig(
            broker = prefGet(KEY_MQTT_BROKER) ?: "",
            clientId = prefGet(KEY_MQTT_CLIENT_ID) ?: "",
            username = prefGet(KEY_MQTT_USERNAME) ?: "",
            password = prefGet(KEY_MQTT_PASSWORD) ?: "",
            vin = prefGet(KEY_MQTT_VIN) ?: ""
        )
    }

    suspend fun saveAndEnable(
        broker: String, clientId: String, username: String, password: String, vin: String
    ) {
        editStore { prefs ->
            prefs[KEY_MQTT_BROKER] = broker
            prefs[KEY_MQTT_CLIENT_ID] = clientId
            prefs[KEY_MQTT_USERNAME] = username
            prefs[KEY_MQTT_PASSWORD] = password
            prefs[KEY_MQTT_VIN] = vin
            prefs[KEY_MQTT_ENABLED] = true
        }
    }

    // ── 日志 ────────────────────────────────────────────────
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private fun addLog(msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val line = "[$ts MQTT] $msg"
        Log.d(Companion.TAG, msg)
        AppLogger.d(Companion.TAG, msg)
        _logs.value = (_logs.value + line).takeLast(200)
    }

    // ── 内部状态 ───────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: MqttClient? = null
    private var currentVin: String = ""
    private var currentClientId: String = ""
    private var currentUsername: String = ""
    private var currentPassword: String = ""
    private val shouldReconnect = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private var connectingTimestamp: Long = 0
    private var retryCount = 0

    private val _connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    val isConnected: Boolean get() = client?.isConnected == true

    @Volatile var connectedVin: String = ""; private set

    var onVehicleStatusProto: ((Map<Int, String>) -> Unit)? = null
    var onAuthorizationNotify: ((Boolean, ByteArray) -> Unit)? = null
    var onParkingNotify: ((ByteArray) -> Unit)? = null
    var onConnected: (() -> Unit)? = null

    // ── connectAsync ───────────────────────────────────────
    fun connectAsync(clientId: String, username: String, password: String, vin: String) {
        if (isConnecting.get()) { addLog("已有连接进行中，跳过"); return }
        currentVin = vin; currentClientId = clientId
        currentUsername = username; currentPassword = password
        shouldReconnect.set(true)
        scope.launch { doConnect(clientId, username, password, vin) }
    }

    fun connect() {
        scope.launch {
            val config = getCurrentCredentials()
            if (!config.isValid) {
                addLog("✗ 凭据不完整，跳过连接")
                _connectionState.value = MqttConnectionState.DISCONNECTED
                return@launch
            }
            val vin = config.vin.ifEmpty { currentVin }
            connectAsync(config.clientId, config.username, config.password, vin)
        }
    }

    // ── doConnect ──────────────────────────────────────────
    private suspend fun doConnect(
        cid: String, uname: String, pwd: String, vin: String
    ) = withContext(Dispatchers.IO) {
        val savedReconnect = shouldReconnect.get()
        val localVin = vin.ifEmpty { currentVin }
        addLog("========== MQTT 连接开始 ==========")
        addLog("  ClientId: $cid  VIN: $localVin")

        disconnect()
        shouldReconnect.set(savedReconnect)
        // 锁超时检查：如果上次连接卡住超过 60 秒，强制释放
        if (isConnecting.get() && System.currentTimeMillis() - connectingTimestamp > 60_000L) {
            addLog("isConnecting 锁超时，强制释放")
            isConnecting.set(false)
        }
        isConnecting.set(true)
        connectingTimestamp = System.currentTimeMillis()
        currentVin = localVin; currentClientId = cid
        currentUsername = uname; currentPassword = pwd

        try {
            val broker = getCurrentCredentials().broker.ifEmpty { MqttConfig.DEFAULT_BROKER }
            val mqttClient = MqttClient(broker, cid, MemoryPersistence())
            client = mqttClient

            val options = MqttConnectOptions().apply {
                userName = uname
                password = pwd.toCharArray()
                isCleanSession = true
                connectionTimeout = MqttConfig.CONNECTION_TIMEOUT
                keepAliveInterval = MqttConfig.KEEP_ALIVE
                mqttVersion = MqttConfig.MQTT_VERSION
                isAutomaticReconnect = false
            }

            mqttClient.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    addLog("✗ 连接断开: ${cause?.message ?: "?"}")
                    _connectionState.value = MqttConnectionState.DISCONNECTED
                    if (shouldReconnect.get()) { scope.launch { scheduleReconnect() } }
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.payload ?: return
                    addLog("← 收到: topic=$topic len=${payload.size}")
                    when {
                        topic?.endsWith("/app/status") == true -> {
                            val map = parseProtobuf(payload)
                            val sorted = map.entries.sortedBy { it.key }.joinToString(" ") { "${it.key}=${it.value}" }
                            addLog("  protobuf[${map.size}]: $sorted")
                            onVehicleStatusProto?.invoke(map)
                        }
                        topic?.contains("car_check_authorize") == true -> {
                            val map = parseProtobuf(payload)
                            addLog("  auth protobuf: ${map.size} fields")
                            onAuthorizationNotify?.invoke(map.isNotEmpty(), payload)
                        }
                        topic?.contains("car_parking_notify") == true -> {
                            val map = parseProtobuf(payload)
                            addLog("  parking protobuf: ${map.size} fields")
                            onParkingNotify?.invoke(payload)
                        }
                        else -> addLog("  未知 topic")
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            addLog("  connect() ...")
            mqttClient.connect(options)
            if (mqttClient.isConnected) {
                retryCount = 0
                subscribeTopics(localVin)
                connectedVin = localVin
                _connectionState.value = MqttConnectionState.CONNECTED
                addLog("✓ MQTT 连接成功")
                onConnected?.invoke()
            } else {
                addLog("✗ isConnected=false")
                _connectionState.value = MqttConnectionState.FAILED
                if (shouldReconnect.get()) { scheduleReconnect() }
            }
        } catch (e: MqttException) {
            addLog("✗ MQTT (reason=${e.reasonCode}): ${e.message}")
            _connectionState.value = MqttConnectionState.FAILED
            if (shouldReconnect.get()) { scheduleReconnect() }
        } catch (e: Exception) {
            addLog("✗ 异常: ${e.message}")
            _connectionState.value = MqttConnectionState.FAILED
            if (shouldReconnect.get()) { scheduleReconnect() }
        } finally { isConnecting.set(false) }
        @Suppress("UNUSED_EXPRESSION")
        Unit
    }

    private fun subscribeTopics(vin: String) {
        val c = client ?: run { addLog("✗ subscribeTopics: null"); return }
        try {
            val topics = MqttTopics.allTopics(vin)
            val qos = MqttTopics.topicQos()
            c.subscribe(topics, qos)
            addLog("✓ 订阅完成 (${topics.size})")
        } catch (e: Exception) { addLog("✗ 订阅: ${e.message}") }
    }

    private suspend fun scheduleReconnect() {
        if (retryCount >= MAX_RETRIES) {
            addLog("✗ 重连已达上限")
            _connectionState.value = MqttConnectionState.FAILED
            return
        }
        _connectionState.value = MqttConnectionState.RECONNECTING
        val d = (BASE_RECONNECT_DELAY * (1 shl retryCount.coerceAtMost(5)))
            .coerceAtMost(MAX_RECONNECT_DELAY)
        retryCount++
        addLog("重连: ${d}ms 第$retryCount 次")
        delay(d)
        if (!shouldReconnect.get() || client?.isConnected == true) return
        doConnect(currentClientId, currentUsername, currentPassword, currentVin)
    }

    fun publish(topic: String, payload: String) {
        if (!isConnected) { addLog("✗ 发布: 未连接"); return }
        scope.launch(Dispatchers.IO) {
            try {
                client?.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 1 })
                addLog("→ 发布: $topic")
            } catch (e: Exception) { addLog("✗ 发布: ${e.message}") }
        }
    }

    fun disconnect() {
        shouldReconnect.set(false)
        scope.launch(Dispatchers.IO) {
            isConnecting.set(false)
            try { client?.disconnect() } catch (_: Exception) {}
            try { client?.close() } catch (_: Exception) {}
            client = null
            _connectionState.value = MqttConnectionState.DISCONNECTED
            addLog("MQTT 已断开")
        }
    }

    fun clearLogs() { _logs.value = emptyList() }
}
