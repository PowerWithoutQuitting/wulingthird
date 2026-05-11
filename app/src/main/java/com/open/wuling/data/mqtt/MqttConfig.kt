package com.open.wuling.data.mqtt

/**
 * MQTT 连接配置 — 完全对齐 v2.9.8
 *
 * Topic 格式: {VIN}/prod/sgmw/vehicle/{suffix}
 * 所有 topic 的 QoS 均为 1
 */
data class MqttConfig(
    val broker: String = "",
    val clientId: String = "",
    val username: String = "",
    val password: String = "",
    val vin: String = ""
) {
    val isValid: Boolean
        get() = broker.isNotEmpty() && clientId.isNotEmpty() &&
                username.isNotEmpty() && password.isNotEmpty()

    companion object {
        const val DEFAULT_BROKER = "tcp://parkingdata.sgmwcloud.com.cn:1883"
        const val KEEP_ALIVE = 60       
        const val CONNECTION_TIMEOUT = 15  
        const val MQTT_VERSION = 4      // MQTT 3.1.1
    }
}

/**
 * MQTT Topic 构建 — 
 *
 * topicStatus(vin)    → "{vin}/prod/sgmw/vehicle/app/status"
 * topicControl(vin)   → "{vin}/prod/sgmw/vehicle/control"
 * topicAuthorize(vin) → "{vin}/prod/sgmw/vehicle/car_check_authorize/business"
 * topicParking(vin)   → "{vin}/prod/sgmw/vehicle/car_parking_notify/business"
 */
object MqttTopics {
    private const val SUFFIX_STATUS    = "/prod/sgmw/vehicle/app/status"
    private const val SUFFIX_CONTROL   = "/prod/sgmw/vehicle/control"
    private const val SUFFIX_AUTHORIZE = "/prod/sgmw/vehicle/car_check_authorize/business"
    private const val SUFFIX_PARKING   = "/prod/sgmw/vehicle/car_parking_notify/business"

    fun topicStatus(vin: String)    = "$vin$SUFFIX_STATUS"
    fun topicControl(vin: String)   = "$vin$SUFFIX_CONTROL"
    fun topicAuthorize(vin: String) = "$vin$SUFFIX_AUTHORIZE"
    fun topicParking(vin: String)   = "$vin$SUFFIX_PARKING"

    /** 所有订阅 topic，QoS 全部为 1 */
    fun allTopics(vin: String) = arrayOf(
        topicStatus(vin),
        topicControl(vin),
        topicAuthorize(vin),
        topicParking(vin)
    )

    /** 每个 topic 对应 QoS = 1 */
    fun topicQos() = intArrayOf(1, 1, 1, 1)
}
