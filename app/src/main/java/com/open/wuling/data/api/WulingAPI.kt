package com.open.wuling.data.api

import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.open.wuling.BuildConfig
import com.open.wuling.data.model.CarInfo
import com.open.wuling.data.model.DoorStatus
import com.open.wuling.data.model.VehicleStatus
import com.open.wuling.data.model.WindowStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()

/**
 * 自定义 TypeAdapterFactory：
 * 当数值字段（Int/Long/Double/Float/Short/Byte）遇到空字符串或非法值时，
 * 不再抛异常，而是按 null 处理，走业务层的 ?: 默认值兜底。
 */
private val LENIENT_NUMBER_FACTORY = object : TypeAdapterFactory {
    private val supportedTypes = setOf(
        Int::class.java, Int::class.javaObjectType,
        Long::class.java, Long::class.javaObjectType,
        Double::class.java, Double::class.javaObjectType,
        Float::class.java, Float::class.javaObjectType,
        Short::class.java, Short::class.javaObjectType,
        Byte::class.java, Byte::class.javaObjectType
    )

    override fun <T> create(gson: com.google.gson.Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (!supportedTypes.contains(type.rawType)) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)
            override fun read(reader: JsonReader): T? {
                val peek = reader.peek()
                if (peek == JsonToken.NULL) {
                    reader.nextNull()
                    return null
                }
                if (peek == JsonToken.STRING) {
                    val str = reader.nextString()
                    if (str.isEmpty()) return null
                    return try {
                        delegate.fromJsonTree(gson.toJsonTree(str.toDoubleOrNull() ?: str))
                    } catch (e: Exception) {
                        null
                    }
                }
                return delegate.read(reader)
            }
        }
    }
}

class WulingAPI @Inject constructor() {
    private val TAG = "WulingAPI"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(createLoggingInterceptor())
        .build()

    // 使用 GsonBuilder 注册容错 Factory
    private val gson = GsonBuilder()
        .registerTypeAdapterFactory(LENIENT_NUMBER_FACTORY)
        .create()

    private val requestMutex = Mutex()

    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    private fun generateSignature(accessToken: String, timestamp: String, nonce: String): String {
        val signStr = accessToken + timestamp + nonce + APIConfig.clientId + APIConfig.clientSecret +
                APIConfig.appCode + APIConfig.appVersion + APIConfig.system + APIConfig.systemVersion

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signStr.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun generateRandomLetters(length: Int): String {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length).map { letters.random() }.joinToString("")
    }

    private fun buildCommonHeaders(accessToken: String, timestamp: String, nonce: String): Map<String, String> {
        val signature = generateSignature(accessToken, timestamp, nonce)
        return mapOf(
            "Content-Type" to "application/json; charset=UTF-8",
            "Accept" to "application/json",
            "User-Agent" to "okhttp/4.9.0",
            "channel" to "linglingbang",
            "platformNo" to "Android",
            "appVersionCode" to APIConfig.apiVersionCode,
            "version" to APIConfig.apiVersion,
            "imei" to APIConfig.deviceImei,
            "imsi" to "unknown",
            "deviceModel" to APIConfig.deviceModel,
            "deviceBrand" to APIConfig.deviceBrand,
            "deviceType" to "Android",
            "accessChannel" to "1",
            "sgmwaccesstoken" to accessToken,
            "sgmwtimestamp" to timestamp,
            "sgmwnonce" to nonce,
            "sgmwclientid" to APIConfig.clientId,
            "sgmwclientsecret" to APIConfig.clientSecret,
            "sgmwappcode" to APIConfig.appCode,
            "sgmwappversion" to APIConfig.appVersion,
            "sgmwsystem" to APIConfig.system,
            "sgmwsystemversion" to APIConfig.systemVersion,
            "sgmwsignature" to signature
        )
    }

    private suspend fun <T> executeWithRetry(
        maxRetries: Int = 2,
        baseDelayMs: Long = 1000,
        maxDelayMs: Long = 5000,
        block: suspend () -> Result<T>
    ): Result<T> {
        var lastError: Throwable? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) {
                Log.w(TAG, "请求失败，第 ${attempt} 次重试...")
            }
            val result = block()
            if (result.isSuccess) return result
            lastError = result.exceptionOrNull()
            if (attempt < maxRetries) {
                val delayMs = minOf(baseDelayMs * (1 shl attempt), maxDelayMs)
                Log.d(TAG, "等待 ${delayMs}ms 后重试...")
                delay(delayMs)
            }
        }
        Log.e(TAG, "请求失败，已重试 ${maxRetries} 次")
        return Result.failure(lastError ?: APIError("请求失败"))
    }

    suspend fun queryDefaultCarStatus(): Result<CarStatusResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                val timestamp = System.currentTimeMillis().toString()
                val nonce = generateRandomLetters(10)
                val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                val url = "${APIConfig.baseURL}/userCarRelation/queryDefaultCarStatus"
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body?.string()

                val result = if (body != null) {
                    try {
                        val carStatusResponse = gson.fromJson(body, CarStatusResponse::class.java)
                        
                        if (carStatusResponse.isSuccess) {
                            if (carStatusResponse.data != null) {
                                Result.success(carStatusResponse)
                            } else {
                                Result.failure(APIError("API返回数据为空"))
                            }
                        } else {
                            val errorMsg = carStatusResponse.errorMessage ?: carStatusResponse.message ?: "请求失败"
                            val errorCode = carStatusResponse.errorCode ?: "unknown"
                            
                            when (errorCode) {
                                "500009" -> Result.failure(APIError("登录已失效，请重新配置 Token"))
                                else -> Result.failure(APIError("$errorMsg (错误码: $errorCode)"))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析错误，响应体: $body", e)
                        Result.failure(APIError("解析错误: " + e.message))
                    }
                } else {
                    Result.failure(APIError("网络错误：响应体为空"))
                }
                result
            }
        }
    }

    suspend fun queryTirePressure(vin: String): Result<TirePressureResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/info/tire/pressure")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val tireResponse = gson.fromJson(body, TirePressureResponse::class.java)
                        Result.success(tireResponse)
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun sendCommand(command: String, params: Map<String, Any> = emptyMap()): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val allParams = params.toMutableMap()
                    allParams["command"] = command
                    val jsonBody = gson.toJson(allParams)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/remote/control")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val cmdResponse = gson.fromJson(body, CommandResponse::class.java)
                        Result.success(cmdResponse)
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun controlDoorLock(vin: String, status: Int): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin, "status" to status)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/doorLock")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, CommandResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun controlAC(params: Map<String, Any>): Result<CommandResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/acc")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, CommandResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun checkCarStatus(vin: String): Result<CheckStatusResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/check/all")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, CheckStatusResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun authorizeIgnition(vin: String): Result<AuthorizeResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/ignition/authorize")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, AuthorizeResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun searchCar(vin: String): Result<SearchCarResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/searchCar")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, SearchCarResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun controlWindow(vin: String, status: Int): Result<WindowControlResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin, "status" to status)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/window")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, WindowControlResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun fetchYesterdayMileage(vin: String): Result<APIResponse<YesterdayMileageData>> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/yesterday/mileage")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        val type = object : com.google.gson.reflect.TypeToken<APIResponse<YesterdayMileageData>>() {}.type
                        Result.success(gson.fromJson<APIResponse<YesterdayMileageData>>(body, type))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }

    suspend fun queryBleKey(vin: String, userId: String): Result<BleKeyResponse> = withContext(Dispatchers.IO) {
        if (!APIConfig.isConfigured) {
            return@withContext Result.failure(APIError("请先配置 Access Token"))
        }

        executeWithRetry {
            requestMutex.withLock {
                try {
                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = generateRandomLetters(10)
                    val headers = buildCommonHeaders(APIConfig.accessToken, timestamp, nonce)

                    val params = mapOf("vin" to vin, "userId" to userId)
                    val jsonBody = gson.toJson(params)

                    val requestBuilder = Request.Builder()
                        .url("${APIConfig.baseURL}/car/control/ble/key/query")
                        .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val body = response.body?.string()

                    if (body != null) {
                        Result.success(gson.fromJson(body, BleKeyResponse::class.java))
                    } else {
                        Result.failure(APIError("网络错误"))
                    }
                } catch (e: Exception) {
                    Result.failure(APIError(e.message ?: "网络错误"))
                }
            }
        }
    }
}

// Extension to convert API response to VehicleStatus
fun CarStatusApi.toVehicleStatus(
    checkEnginePow: Int? = null,
    checkEngineTemp: Int? = null,
    checkAbsio: Int? = null,
    checkPwrStrIo: Int? = null
): VehicleStatus {
    val isVehicleLocked = doorLockStatus == 0
    
    val result = VehicleStatus(
        batteryLevel = batterySoc ?: 0,
        range = leftMileage ?: 0,
        electricRange = leftMileage ?: 0,
        oilRange = oilLeftMileage ?: 0,
        leftFuel = leftFuel?.toIntOrNull() ?: 0,
        isLocked = isVehicleLocked,
        isClimateOn = acStatus != 0 && acStatus != null,
        climateMode = when (acStatus) {
            0 -> "off"
            1 -> "cool"
            2 -> "heat"
            else -> "off"
        },
        climateTemperature = interiorTemperature ?: 24,
        mileage = mileage ?: 0,
        isCharging = vecChrgingSts == 1,
        interiorTemperature = interiorTemperature ?: 25,
        exteriorTemperature = accCntTemp ?.toInt() ?: 20,
        gearStatus = autoGearStatus ?: "10",

        tirePressureFL = tirePressureFl?.toDoubleOrNull()?.div(100) ?: 0.0,
        tirePressureFR = tirePressureFr?.toDoubleOrNull()?.div(100) ?: 0.0,
        tirePressureRL = tirePressureRl?.toDoubleOrNull()?.div(100) ?: 0.0,
        tirePressureRR = tirePressureRr?.toDoubleOrNull()?.div(100) ?: 0.0,
        tireTemperature = 0,

        batteryHealth = batSOH ?: batHealth ?: 95,
        batteryTempMin = batMinTemp ?: 20,
        batteryTempMax = batMaxTemp ?: 28,
        batAvgTemp = batAvgTemp ?: 0,
        lowBatVol = lowBatVol ?: 0.0,
        batteryStatus = batteryStatus ?: "0",
        leftBatteryPower = leftBatteryPower ?: 0.0,
        voltage = voltage ?: 0.0,
        current = current ?: 0.0,
        chargePower = chargePower?.toDoubleOrNull() ?: 0.0,

        doors = DoorStatus(
            frontLeft = (door1OpenStatus ?: 0) == 1,
            frontRight = (door2OpenStatus ?: 0) == 1,
            rearLeft = (door3OpenStatus ?: 0) == 1,
            rearRight = (door4OpenStatus ?: 0) == 1,
            trunk = (tailDoorOpenStatus ?: 0) == 1,
            frontLeftLocked = (door1LockStatus ?: doorLockStatus ?: 0) == 0,
            frontRightLocked = (door2LockStatus ?: doorLockStatus ?: 0) == 0,
            rearLeftLocked = (door3LockStatus ?: doorLockStatus ?: 0) == 0,
            rearRightLocked = (door4LockStatus ?: doorLockStatus ?: 0) == 0,
            trunkLocked = (doorLockStatus ?: 0) == 0
        ),

        windows = WindowStatus(
            frontLeft = (window1Status ?: 0) == 1,
            frontRight = (window2Status ?: 0) == 1,
            rearLeft = (window3Status ?: 0) == 1,
            rearRight = (window4Status ?: 0) == 1
        ),

        window1OpenDegree = window1OpenDegree ?: 0,
        window2OpenDegree = window2OpenDegree ?: 0,
        window3OpenDegree = window3OpenDegree ?: 0,
        window4OpenDegree = window4OpenDegree ?: 0,

        frontFogLight = frontFogLight == "1",
        leftTurnLight = leftTurnLight == "1",
        positionLight = positionLight == "1",
        rightTurnLight = rightTurnLight == "1",
        dipHeadLight = dipHeadLight == "1",
        lowBeamLight = lowBeamLight == "1",

        keyStatus = keyStatus ?: "0",
        autoGearStatus = autoGearStatus ?: "10",

        tmActTemp = tmActTemp ?: 0,
        invActTemp = invActTemp ?: 0,
        obcOtpCur = obcOtpCur ?: 0.0,

        vecChrgStsIndOn = vecChrgStsIndOn == 1,
        vecChargeSts = vecChargeSts ?: 0,
        chargingTimeRemaining = leftChargeTime,
        chargingRaw = charging ?: "0",

        yesterMileage = yesterMileage ?: 0,
        avgFuel = avgFuel ?: 0.0,
        hybridMileageKm = hybridMileage?.toIntOrNull(),

        steeringWheelAngle = strWhAng ?: "0",
        brakePedalPosition = brakPedalPos ?: "0",
        accPosition = accActPos ?: "0",
        averageSpeed = vehSpdAvgDrvn ?: "",

        sentinelModeStatus = sentinelModeStatus == "1",
        limitFeedback = limitFeedback ?: "-1",

        seat1HotStatus = seat1HotStatus ?: "",
        seat2HotStatus = seat2HotStatus ?: "",
        seat3HotStatus = seat3HotStatus ?: "",
        seat4HotStatus = seat4HotStatus ?: "",
        seat1WindStatus = seat1WindStatus ?: "",
        seat2WindStatus = seat2WindStatus ?: "",
        seat3WindStatus = seat3WindStatus ?: "",
        seat4WindStatus = seat4WindStatus ?: "",

        intelligentCarSwitch = intelligentCarSwitch ?: 0,
        collectTime = collectTime ?: "",

        enginePowStatus = checkEnginePow ?: 1,
        engineTempStatus = checkEngineTemp ?: 1,
        absStatus = checkAbsio ?: 0,
        powerSteeringStatus = checkPwrStrIo ?: 0
    )
    return result
}

fun CarInfoApi.toCarInfo(): CarInfo {
    return CarInfo(
        carInfoId = carInfoId ?: 0,
        userId = userId ?: "",
        vin = vin ?: "",
        carName = carName ?: "",
        colorCode = colorCode ?: "",
        colorName = colorName ?: "",
        vsn = vsn ?: "",
        carPlate = carPlate ?: "",
        carTypeName = carTypeName ?: "",
        model = model ?: "",
        level = level ?: "",
        engineType = engineType ?: 0,
        image = image ?: "",
        providerCode = providerCode ?: "",
        carYear = carYear ?: "",
        seriesCode = seriesCode ?: "",
        powerType = powerType ?: "",
        purchaseDate = purchaseDate ?: 0,
        purchaseUserName = purchaseUserName ?: "",
        purchaseShopNum = purchaseShopNum ?: "",
        carOwnerDay = carOwnerDay ?: 0,
        bindCarUserMobile = bindCarUserMobile ?: "",
        finishBind = finishBind ?: false,
        shakeLock = shakeLock ?: 0,
        bluetoothKeyConnectMark = bluetoothKeyConnectMark ?: "",
        supportAutoAir = supportAutoAir ?: 0,
        supportChargeRemain = supportChargeRemain ?: 0,
        supportChargePower = supportChargePower ?: 0,
        supportAvgFuel = supportAvgFuel ?: 0,
        supportHybridMileage = supportHybridMileage ?: 0,
        supportMqtt = supportMqtt ?: 0,
        controlView = controlView ?: 0,
        bleType = bleType ?: 0,
        physicsEngine = physicsEngine ?: 0
    )
}
