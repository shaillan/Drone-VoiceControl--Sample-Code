# Drone Voice Control — Sample Code

基于 **DJI Mobile SDK for Android** 的无人机语音控制示例工程。

在 DJI 官方 Android Sample Code 的基础上，新增了一个完整的语音飞控页面：把自然语言语音指令经「语音识别 → 大模型语义解析 → 飞控指令」三级链路，最终转换为 DJI 虚拟摇杆控制信号，实现用说话控制无人机起飞、降落、前后移动和左右转向。

---

## 功能特性

- **语音控制飞行** —— 按一下按钮说一句话，即可执行起飞、降落、前进、后退、左转、右转、悬停
- **自然语言理解** —— 不依赖固定口令词，由大模型把口语解析成结构化 JSON 指令（例如「往前飞三米」→ `{"action":"forward","value":3}`）
- **实时图传** —— 集成 DJI 图传解码，SurfaceView 全屏显示飞行画面
- **手动飞行面板** —— 保留虚拟摇杆开关、起飞、降落等按钮，可随时接管
- **模块化重构** —— 图传、飞控、语音识别各自拆分为独立 Helper 类，Activity 只负责 UI 与调度

---

## 工作原理

```
┌──────────┐   ①录音 3 秒   ┌──────────────┐
│  麦克风   │ ─────────────▶ │ MediaRecorder │
└──────────┘                └──────┬───────┘
                                   │ ②AAC/m4a, 16kHz, 单声道
                                   ▼
                          ┌────────────────────┐
                          │ 百度语音识别 (ASR)   │  ③Base64 POST
                          │ vop.baidu.com      │ ────────────┐
                          └────────────────────┘             │
                                   ▲                         ▼
                                   │ ④识别文本          ┌──────────┐
                                   └─────────────────── │ 识别结果  │
                                                        └────┬─────┘
                                                             │ ⑤原始语句 + 约束提示词
                                                             ▼
                                                    ┌──────────────────┐
                                                    │ DeepSeek Chat LLM │
                                                    │ 语义 → JSON 指令   │
                                                    └────────┬─────────┘
                                                             │ ⑥ {"action":"forward","value":3}
                                                             ▼
                                                    ┌──────────────────┐
                                                    │ FlightController  │
                                                    │ 虚拟摇杆 100ms 下发 │
                                                    └──────────────────┘
```

1. **录音**：点击「按住说话」按钮后录音 3 秒，输出 16 kHz / 单声道 / AAC(m4a)。
2. **语音识别**：音频 Base64 编码后 POST 到百度短语音识别接口（`dev_pid=1537`，普通话），返回识别文本。
3. **语义解析**：识别文本与一段「只输出 JSON」的提示词一起发给 DeepSeek，得到 `{"action": ..., "value": ...}`。
4. **指令执行**：按 `action` 调用虚拟摇杆接口。前进/后退按 `value` 秒后自动悬停；左转/右转按 `value` 度偏航。

---

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Android Studio | 4.2 及以上（推荐 Arctic Fox 或更新） |
| Gradle | 7.6.1 |
| Android Gradle Plugin | 7.0.4 |
| Kotlin | 1.5.10 |
| compileSdk / targetSdk / minSdk | 33 / 34 / 23 |
| DJI Mobile SDK | 4.18 |
| JDK | 8+ |

**硬件**：支持 DJI SDK 的机型（如 Mavic / Phantom / Matrice 系列）+ 遥控器，Android 手机需支持 USB 配件模式（OTG）。

---

## 快速开始

### 1. 配置 DJI SDK 密钥

在 [DJI 开发者平台](https://developer.dji.com/user/apps/) 创建应用并获取 App Key，填入 `app/src/main/AndroidManifest.xml`：

```xml
<meta-data
    android:name="com.dji.sdk.API_KEY"
    android:value="你的_DJI_App_Key" />
```

> 注意：DJI App Key 与应用的 `applicationId` 绑定。若修改了包名，需同步在开发者平台登记，否则注册会失败。

### 2. 配置百度语音识别密钥

在 `app/src/main/java/com/dji/sdk/sample/demo/flightcontroller/VoiceRecognitionHelper.java` 中填入[百度智能云](https://console.bce.baidu.com/ai/#/ai/speech/overview/index)语音技术的应用凭证：

```java
private static final String API_KEY = "你的百度 API Key";
private static final String SECRET_KEY = "你的百度 Secret Key";
```

### 3. 配置 DeepSeek 密钥

在 `app/src/main/java/com/dji/sdk/sample/demo/flightcontroller/VoiceControlActivity.java` 中填入 [DeepSeek 开放平台](https://platform.deepseek.com/)的 API Key：

```java
String apiKey = "你的 DeepSeek API Key";
```

### 4. 编译运行

```bash
# 命令行构建 Debug 包
./gradlew assembleDebug

# 安装到已连接的设备
./gradlew installDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

也可以用 Android Studio 打开工程后直接点击 Run。

### 5. 使用流程

1. 手机通过 OTG 连接遥控器，遥控器连接飞机，给飞机上电。
2. 启动 App，进入「语音控制」页面，等待图传画面出现。
3. 点击 **开启虚拟摇杆** —— 这是语音控制生效的前提。
4. 点击 **按住说话**，在 3 秒内说出指令。
5. 界面会依次显示识别文本、解析出的指令和最终执行动作。

> **安全提示**：首次调试建议在 DJI Assistant 2 模拟器中运行，或在不装桨的空旷环境下测试。虚拟摇杆一旦开启，飞控将完全交由 App 接管。

---

## 支持的语音指令

| 说的话（示例） | action | value | 行为 |
| --- | --- | --- | --- |
| 「起飞」「起飞吧」 | `takeoff` | 0 | 自动起飞 |
| 「降落」「下来」 | `land` | 0 | 自动降落 |
| 「向前飞三米」 | `forward` | 米数 | 以 0.3 速度前进，N 秒后悬停 |
| 「后退两米」 | `backward` | 米数 | 以 0.3 速度后退，N 秒后悬停 |
| 「向左转 45 度」 | `left` | 角度 | 偏航左转 |
| 「向右转 90 度」 | `right` | 角度 | 偏航右转 |
| 「停」「悬停」 | `hover` | 0 | 速度归零悬停 |
| 无法理解 | `unknown` | 0 | 提示「指令无法识别」 |

模型侧约束：**飞行距离最大 5 米，旋转角度最大 90 度**，超出范围的请求由大模型归一化处理。

---

## 项目结构

本仓库是在 DJI 官方 Sample Code 上做的增量开发，官方 demo 目录（`demo/camera`、`demo/battery`、`demo/missionmanager` 等）保持原样，语音控制相关代码集中在以下几个文件：

```
app/src/main/java/com/dji/sdk/sample/
├── demo/flightcontroller/
│   ├── VoiceControlActivity.java      # 语音控制主页面（UI + 流程调度 + LLM 调用）
│   ├── VoiceRecognitionHelper.java    # 录音 + 百度语音识别
│   ├── FlightControllerHelper.java    # 虚拟摇杆 / 起飞降落 / 方向控制
│   ├── VideoFeedManager.java          # DJI 图传解码与显示
│   └── VirtualStickView.java          # 官方虚拟摇杆示例（参考用）
├── internal/
│   ├── controller/                    # SDK 初始化、主界面、USB 附件监听
│   ├── utils/                         # ModuleVerificationUtil、ToastUtils 等工具
│   └── audiohandler/                  # 官方音频处理示例
└── ...

app/src/main/res/layout/activity_voice_control.xml   # 语音控制页面布局
```

---

## 核心类说明

### `VoiceControlActivity`

语音控制的入口页面，同时注册为 Launcher Activity。负责：

- 初始化并串联三个 Helper
- 申请 `RECORD_AUDIO` 权限
- 把识别结果 + 提示词发给 DeepSeek，解析返回的 JSON
- 将 `action` 分发到 `FlightControllerHelper`

### `VoiceRecognitionHelper`

封装录音与在线识别，通过 `RecognitionCallback` 向外部回调 `onResult` / `onError` / `onStatus`：

- `MediaRecorder` 采集 16 kHz 单声道 AAC 音频，录制 3 秒后自动停止
- 录音文件小于 1 KB 时判定为「录音太短」，直接提示重说
- 先取 `access_token`，再 Base64 上传音频到 `vop.baidu.com/server_api`
- 所有回调切回主线程执行

### `FlightControllerHelper`

对 DJI `FlightController` 的薄封装：

- 开启虚拟摇杆时设置为 `VELOCITY` 速度控制模式 + `BODY` 机体坐标系
- 通过 `SendTask` 每 100 ms 下发一次 `FlightControlData`，这是 DJI 要求的持续下发
- 提供 `takeOff` / `landing` / `moveForward` / `moveBackward` / `turnLeft` / `turnRight` / `hover`

### `VideoFeedManager`

- Surface 创建后**立即**初始化 `DJICodecManager`，不依赖飞机是否已连接
- 注册 `VideoFeeder` 数据监听，飞机上线后自动开始推流解码
- 生命周期与 Surface 的 `created` / `destroyed` 对齐，避免内存泄漏

---

## 注意事项与已知限制

- **密钥不可提交**：三个第三方密钥（DJI、百度、DeepSeek）目前需要硬编码在源码中，提交代码前请确认已移除或替换为占位符。生产环境建议改为服务端代理转发，避免密钥随 APK 泄露。
- **固定录音时长**：录音为固定 3 秒，暂不支持静音自动断句（VAD）。
- **无状态校验**：虚拟摇杆未开启时语音指令不会生效，但界面不会主动拦截，使用前请先手动开启。
- **单次指令**：一次只处理一条指令，不支持「先左转再前进」这类复合语句。
- **网络依赖**：识别与大模型解析均为在线调用，需要手机保持联网，指令端到端延迟受网络影响。
- **安全边界**：语音指令的速度、角度上限由提示词约束，属于软约束而非代码强制校验，请勿在真实飞行中依赖其作为唯一安全措施。

---

## 参考资源

- [DJI Mobile SDK for Android 开发文档](https://developer.dji.com/mobile-sdk/documentation/introduction/index.html)
- [DJI 开发者平台](https://developer.dji.com/)
- [百度智能云 — 短语音识别标准版](https://ai.baidu.com/ai-doc/SPEECH/Jk5dll5cm)
- [DeepSeek API 文档](https://platform.deepseek.com/api-docs/)

---

## 许可证

本工程基于 DJI 官方 Android Sample Code 修改而来，其使用受 [DJI 最终用户许可协议（EULA）](https://developer.dji.com/policies/eula/) 约束，使用本工程即表示同意该协议。商用前请自行确认 DJI SDK 的授权范围，以及百度、DeepSeek 两家服务的使用条款。
