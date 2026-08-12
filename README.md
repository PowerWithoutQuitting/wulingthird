# 五菱汽车车控 APP

独立开源的五菱/宝骏新能源车控 Android 应用，无需 Home Assistant 环境，直接对接官方车联网接口。

> 本项目原由 [wlsqqq/wuling-automotive-control](https://gitee.com/wlsqqq/wuling-automotive-control) 首发，现已转移至 GitHub 继续独立开发。如有侵权，会第一时间删除。

---

## 功能

### 远程控制

| 功能 | 说明 |
|------|------|
| 锁车 / 解锁 | 一键远程上锁/解锁车门 |
| 空调控制 | 开关、温度调节（17-33°C）、风速调节（1-7 档）、快速制冷/制热 |
| 远程启动 | 远程启动车辆（点火授权） |
| 尾箱 | 远程开启后备箱 |
| 寻车 | 远程鸣笛闪灯 |
| 车窗控制 | 一键关闭/打开全部车窗 |

### 车辆状态监控

| 类别 | 数据项 |
|------|--------|
| 电量与续航 | SOC 电量、纯电续航、燃油续航、混动里程、剩余油量 |
| 电池 | SOH 健康度、温度范围、电压、电流、低压电池 |
| 里程 | 总里程、昨日里程、平均能耗 |
| 胎压 | 四轮胎压（bar）+ 轮胎温度 |
| 车门 | 每扇门开关 + 锁定状态，尾箱状态 |
| 车窗 | 每扇窗开关状态 |
| 灯光 | 前雾灯、转向灯、示廓灯、远/近光灯 |
| 充电 | 充电状态、充电功率、剩余时间 |
| 温度 | 车内/空调/电机/逆变器温度 |
| 驾驶 | 档位、方向盘角度、刹车/油门踏板、钥匙状态 |
| 诊断 | 动力系统、发动机温度、ABS、动力转向故障检测 |
| 座椅 | 加热/通风状态 |

### MQTT 实时推送

- 车辆状态变化实时推送（基于 Eclipse Paho MQTT v3）
- Protobuf 消息解析，支持车门/车窗/灯光/空调/充电等状态增量更新
- 自动重连（指数退避，最大 60s 间隔，最多 10 次）
- 连接状态 UI 指示

### 车辆位置

- 高德地图 WebView 集成，实时显示车辆定位
- 一键跳转高德导航找车
- 分享车辆位置

### BLE 无感控车

- 蓝牙数字钥匙：从服务器获取 BLE 密钥连接车载蓝牙
- RSSI 自动控车：靠近自动解锁，远离自动上锁
- 可配置解锁/上锁 RSSI 阈值、持续时间、冷却时间
- 前台保活服务，后台运行
- 附近蓝牙设备扫描

### 个性化配置

- API Token 绑定（DataStore 持久化）
- 主题：浅色 / 深色 / 跟随系统
- 自定义配色（主色、背景色、卡片色、文字色）
- 自定义背景图 + 模糊 + 遮罩
- 卡片透明度调节
- 调试日志查看器（分级过滤、复制、清空）
- 高德地图 Key 配置

---

## 技术架构

```
com.open.wuling/
├── data/
│   ├── api/          # HTTP API 层（OkHttp + Gson）
│   ├── local/        # 本地配置（主题、BLE、地图 Key）
│   ├── model/        # 数据模型
│   ├── mqtt/         # MQTT 通信层（Paho + Protobuf）
│   ├── repository/   # 数据仓库
│   └── store/        # Token 持久化
├── ui/
│   ├── components/   # 通用 Compose 组件
│   ├── screens/      # 页面（Home/Detail/Profile/Location）
│   └── theme/        # Material3 主题
├── ble/              # BLE 蓝牙控制
└── util/             # 工具类
```

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt
- **网络**: OkHttp + Gson
- **MQTT**: Eclipse Paho Client v3
- **蓝牙**: Android BLE API
- **存储**: DataStore Preferences
- **最低版本**: Android 9 (API 26)

---

## 适配车型

| 车型 | 状态 |
|------|------|
| 五菱新能源全系 | 兼容（基于通用接口） |
| 宝骏新能源 | 可尝试（API 域名相同，接口通用） |

---

## 安装与使用

1. 从 [Release 页面](https://github.com/haocat/wulingthird/releases) 下载 APK
2. 安装后前往「我的」→「API Token」配置令牌
3. Token 需自行获取（抓包五菱/宝骏官方 App）
4. 建议使用授权手机号对应的 Token
5. 位置功能需自行配置高德地图 Web JS API Key

---

## 构建

```bash
# 需要在 local.properties 中配置以下字段
wuling.client.id=你的ClientID
wuling.client.secret=你的ClientSecret
wuling.app.code=你的AppCode
wuling.app.version=你的AppVersion
wuling.base.url=你的API地址
wuling.device.imei=设备IMEI
wuling.device.model=设备型号
wuling.device.brand=设备品牌
wuling.api.version=API版本
wuling.api.version.code=API版本号

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（需配置签名）
./gradlew assembleRelease
```

---

## 待开发

- [ ] 桌面小组件（锁车/解锁/空调快捷开关，电量/续航展示）
- [ ] 快捷设置磁贴（下拉通知栏快速操作）
- [ ] 低电量/充电完成本地通知

> 欢迎提交 Issue 讨论需求，也欢迎 PR 一起实现。

---

## 声明

1. 本项目为**非官方第三方开源工具**，仅用于个人学习与研究，禁止用于任何商业用途
2. 本项目不存储用户账号、车辆等任何敏感信息，使用过程中的所有风险由用户自行承担
3. 「五菱」「宝骏」为相关汽车品牌注册商标，本项目与官方无任何隶属或合作关系

---

## 开源协议

基于 [MIT License](LICENSE) 开源，完全免费。
