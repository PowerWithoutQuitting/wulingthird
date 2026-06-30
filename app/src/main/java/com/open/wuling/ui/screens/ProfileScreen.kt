package com.open.wuling.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.open.wuling.BuildConfig
import com.open.wuling.MainViewModel
import com.open.wuling.data.api.APIConfig
import com.open.wuling.data.mqtt.MqttConnectionState
import com.open.wuling.util.AppLogger
import com.open.wuling.ui.theme.PrimaryGreen
import com.open.wuling.ui.theme.PrimaryOrange
import com.open.wuling.ui.theme.PrimaryRed
import com.open.wuling.ui.theme.LocalCardAlpha

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val user by viewModel.vehicleManager.user.collectAsState()
    val selectedVehicle by viewModel.vehicleManager.selectedVehicle.collectAsState()
    val scrollState = rememberScrollState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    var showMqttDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var mqttBrokerInput by remember { mutableStateOf("") }
    var mqttClientIdInput by remember { mutableStateOf("") }
    var mqttUsernameInput by remember { mutableStateOf("") }
    var mqttPasswordInput by remember { mutableStateOf("") }
    var mqttAutoLoading by remember { mutableStateOf(false) }
    var mqttAutoError by remember { mutableStateOf<String?>(null) }
    var showManualMqtt by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // MQTT 连接状态
    val mqttState by viewModel.vehicleManager.mqttManager.connectionState.collectAsState()

    // 主题模式
    val themeMode by viewModel.themePreferences.themeModeFlow.collectAsState(initial = 0)
    val useCustomColors by viewModel.themePreferences.useCustomColorsFlow.collectAsState(initial = false)
    val themeModeText = when (themeMode) {
        1 -> "浅色模式"
        2 -> "深色模式"
        else -> "跟随系统"
    }

    // 从车辆信息中获取显示数据
    val carInfo = selectedVehicle?.carInfo
    val vehicleName = carInfo?.carTypeName?.ifEmpty { carInfo.carName } 
                    ?: selectedVehicle?.displayName 
                    ?: "未绑定车辆"
    val vehicleImage = carInfo?.image?.ifEmpty { null }
    val bindPhone = carInfo?.bindCarUserMobile?.ifEmpty { null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Profile Header - 车辆信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 车辆图片头像
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!vehicleImage.isNullOrEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(model = vehicleImage),
                            contentDescription = "车辆图片",
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // 车辆型号作为用户名
                    Text(
                        text = vehicleName.ifEmpty { "未绑定车辆" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // 绑定手机号
                    if (!bindPhone.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📱 $bindPhone",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 显示 VIN
                    selectedVehicle?.vin?.takeIf { it.isNotEmpty() }?.let { vin ->
                        Text(
                            text = "VIN: ${vin.takeLast(6)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = { /* Edit profile */ }) {
                    Text("编辑", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Vehicle Section
        Text(
            text = "我的车辆",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        user.vehicles.forEach { vehicle ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vehicle.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = vehicle.model,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Settings Section
        Text(
            text = "设置",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                // API Token
                SettingsItem(
                    icon = Icons.Filled.Key,
                    title = "API Token",
                    subtitle = if (APIConfig.isConfigured) "已配置" else "未配置",
                    iconColor = MaterialTheme.colorScheme.primary,
                    showCheck = APIConfig.isConfigured,
                    onClick = { showTokenDialog = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    title = "消息通知",
                    subtitle = "推送和提醒设置",
                    iconColor = PrimaryOrange,
                    onClick = { }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Shield,
                    title = "隐私设置",
                    subtitle = "权限和数据管理",
                    iconColor = PrimaryGreen,
                    onClick = { }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Settings,
                    title = "主题设置",
                    subtitle = if (useCustomColors) "自定义 · $themeModeText" else themeModeText,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = { showThemeSettings = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.BugReport,
                    title = "调试日志",
                    subtitle = "查看 API 请求和响应",
                    iconColor = PrimaryOrange,
                    onClick = { showLogDialog = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                val mqttStatusText = when (mqttState) {
                    MqttConnectionState.CONNECTED -> "已连接"
                    MqttConnectionState.CONNECTING -> "连接中..."
                    MqttConnectionState.RECONNECTING -> "重连中..."
                    MqttConnectionState.DISCONNECTED -> "未连接"
                    MqttConnectionState.FAILED -> "连接失败"
                }
                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    title = "MQTT配置",
                    subtitle = "实时推送 · $mqttStatusText",
                    iconColor = when (mqttState) {
                        MqttConnectionState.CONNECTED -> PrimaryGreen
                        MqttConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> PrimaryOrange
                    },
                    onClick = { showMqttDialog = true }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Watch,
                    title = "Apple Watch",
                    subtitle = "手表同步设置",
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = { }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tools Section — 蓝牙钥匙 & ADB
        Text(
            text = "工具",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Filled.Lock,
                    title = "获取蓝牙钥匙",
                    subtitle = "从服务器下载 BLE 数字钥匙",
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        scope.launch {
                            viewModel.vehicleManager.fetchAndStoreBleKey()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // About Section
        Text(
            text = "关于",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "用户协议",
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.PrivacyTip,
                    title = "隐私政策",
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "关于我们",
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { }
                )

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "版本",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout Button
        Button(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "退出登录",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryRed
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Token Input Dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = {
                Text(
                    text = "配置 Access Token",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "输入您的五菱API访问令牌",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Access Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "如何获取 Token？\n1. 打开五菱官方App\n2. 登录后抓包获取 accessToken\n3. 将 Token 粘贴到上方输入框",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedToken = tokenInput.trim()
                        if (trimmedToken.isNotEmpty()) {
                            viewModel.vehicleManager.saveAndConfigureToken(trimmedToken)
                            showTokenDialog = false
                            tokenInput = ""
                        }
                    },
                    enabled = tokenInput.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTokenDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Logout Confirm Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "确认退出登录？",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "退出后将清除已保存的 Token，需要重新配置才能使用远程控制功能。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.vehicleManager.logout()
                        showLogoutDialog = false
                        tokenInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed)
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // MQTT Config Dialog
    if (showMqttDialog) {
        // 打开弹窗时加载已有凭据
        LaunchedEffect(Unit) {
            try {
                val creds = viewModel.vehicleManager.mqttManager.getCurrentCredentials()
                mqttBrokerInput = creds.broker
                mqttClientIdInput = creds.clientId
                mqttUsernameInput = creds.username
                mqttPasswordInput = creds.password
            } catch (_: Exception) {}
        }
        AlertDialog(
            onDismissRequest = { showMqttDialog = false; mqttAutoError = null },
            title = {
                Text(text = "MQTT 配置", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    // ── 一键连接区域 ──
                    val vin = selectedVehicle?.vin.orEmpty()
                    val phone = selectedVehicle?.carInfo?.bindCarUserMobile.orEmpty()
                    val canAutoConnect = vin.isNotEmpty() && phone.isNotEmpty()

                    Text(
                        text = "自动获取MQTT凭据并连接",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (canAutoConnect)
                            "VIN: ${vin.take(6)}...  手机: ${phone.takeLast(4)}"
                        else
                            "需要先选择车辆且车辆信息包含绑定手机号",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (mqttAutoError != null) {
                        Text(
                            text = mqttAutoError!!,
                            fontSize = 12.sp,
                            color = PrimaryRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Button(
                        onClick = {
                            mqttAutoLoading = true
                            mqttAutoError = null
                            viewModel.vehicleManager.fetchAndConnectMqtt { result ->
                                mqttAutoLoading = false
                                result.onSuccess {
                                    showMqttDialog = false
                                }.onFailure { e ->
                                    mqttAutoError = e.message
                                }
                            }
                        },
                        enabled = canAutoConnect && !mqttAutoLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen
                        )
                    ) {
                        if (mqttAutoLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        } else {
                            Text("一键连接 MQTT")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    // ── 手动配置（折叠） ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManualMqtt = !showManualMqtt },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "手动配置",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "展开",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (showManualMqtt) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "默认 Broker: tcp://parkingdata.sgmwcloud.com.cn:1883",
                            fontSize = 11.sp,
                            color = PrimaryGreen
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = mqttBrokerInput,
                            onValueChange = { mqttBrokerInput = it },
                            label = { Text("Broker") },
                            placeholder = { Text("tcp://parkingdata.sgmwcloud.com.cn:1883") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = mqttClientIdInput,
                            onValueChange = { mqttClientIdInput = it },
                            label = { Text("Client ID") },
                            placeholder = { Text("clientId") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = mqttUsernameInput,
                            onValueChange = { mqttUsernameInput = it },
                            label = { Text("Username") },
                            placeholder = { Text("username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = mqttPasswordInput,
                            onValueChange = { mqttPasswordInput = it },
                            label = { Text("Password") },
                            placeholder = { Text("password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (showManualMqtt) {
                    Button(
                        onClick = {
                            val broker = mqttBrokerInput.trim().ifEmpty { "tcp://parkingdata.sgmwcloud.com.cn:1883" }
                            if (mqttClientIdInput.trim().isNotEmpty() &&
                                mqttUsernameInput.trim().isNotEmpty() &&
                                mqttPasswordInput.trim().isNotEmpty()
                            ) {
                                val vin = selectedVehicle?.vin.orEmpty()
                                viewModel.vehicleManager.updateMqttCredentials(
                                    broker = broker,
                                    clientId = mqttClientIdInput.trim(),
                                    username = mqttUsernameInput.trim(),
                                    password = mqttPasswordInput.trim(),
                                    vin = vin
                                )
                                showMqttDialog = false
                            }
                        },
                        enabled = mqttClientIdInput.trim().isNotEmpty() &&
                                  mqttUsernameInput.trim().isNotEmpty() &&
                                  mqttPasswordInput.trim().isNotEmpty()
                    ) {
                        Text("保存并连接")
                    }
                } else {
                    TextButton(onClick = { showMqttDialog = false }) {
                        Text("关闭")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showMqttDialog = false; mqttAutoError = null }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Log Viewer Bottom Sheet
    if (showLogDialog) {
        LogViewerSheet(
            onDismiss = { showLogDialog = false }
        )
    }

    // Theme Settings Bottom Sheet
    if (showThemeSettings) {
        ThemeSettingsSheet(
            onDismiss = { showThemeSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var logs by remember { mutableStateOf(AppLogger.getAllLogs()) }
    var logEnabled by remember { mutableStateOf(AppLogger.isEnabled()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "调试日志",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.weight(1f))

                // 启用/禁用开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = logEnabled,
                        onCheckedChange = {
                            logEnabled = it
                            AppLogger.setEnabled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 清空按钮
                TextButton(onClick = {
                    AppLogger.clear()
                    logs = emptyList()
                }) {
                    Text("清空", color = PrimaryRed)
                }

                // 刷新按钮
                TextButton(onClick = { logs = AppLogger.getAllLogs() }) {
                    Text("刷新", color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 复制到剪贴板
                TextButton(onClick = {
                    val text = logs.joinToString("\n") { "[${it.formattedTime}] [${it.tag}] ${it.message}" +
                        (it.details?.let { "\n  $it" } ?: "") }
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("调试日志", text))
                    android.widget.Toast.makeText(context, "已复制 ${logs.size} 条日志", android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Text("复制", color = PrimaryGreen)
                }

                // 导出到文件
                TextButton(onClick = {
                    try {
                        val text = logs.joinToString("\n") { "[${it.formattedTime}] [${it.tag}] ${it.message}" +
                            (it.details?.let { "\n  $it" } ?: "") }
                        val dir = java.io.File(context.getExternalFilesDir(null), "logs")
                        dir.mkdirs()
                        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        val file = java.io.File(dir, "wuling_debug_${ts}.txt")
                        file.writeText(text)
                        android.widget.Toast.makeText(context, "已导出: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "导出失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("导出", color = PrimaryOrange)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Log list
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无日志\n执行操作后日志将显示在这里",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(logs.reversed()) { entry ->
                        LogItem(entry = entry)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LogItem(entry: AppLogger.LogEntry) {
    val levelColor = when (entry.level) {
        AppLogger.Level.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        AppLogger.Level.INFO -> MaterialTheme.colorScheme.primary
        AppLogger.Level.WARN -> PrimaryOrange
        AppLogger.Level.ERROR -> PrimaryRed
    }

    val levelPrefix = when (entry.level) {
        AppLogger.Level.DEBUG -> "D"
        AppLogger.Level.INFO -> "I"
        AppLogger.Level.WARN -> "W"
        AppLogger.Level.ERROR -> "E"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[$levelPrefix]",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.formattedTime,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            entry.details?.let { details ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    showCheck: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showCheck) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = PrimaryGreen,
                modifier = Modifier.size(20.dp)
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}
