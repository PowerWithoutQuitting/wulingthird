package com.open.wuling.data.mqtt

/** MQTT 连接状态 — 对齐 */
enum class MqttConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}
