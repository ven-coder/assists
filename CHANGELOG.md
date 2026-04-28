# 更新日志

### 3.5.0（2026-04-28）

- **构建与目标平台**：Gradle / Android Gradle Plugin **8.9**、Kotlin **2.1**、Gradle Wrapper 等与 **compileSdk / targetSdk 36** 对齐；`settings.gradle` 集中管理插件版本，便于后续升级。
- **无障碍服务声明**：按 API 层级补充 **xml-v26 / v33 / v34** 等资源变体；默认配置收窄监听的事件类型（减少对滚动、悬停等极高频事件的订阅）、调整 `notificationTimeout` 等以降低事件风暴；扩展无障碍能力与 flag（如增强网页无障碍、过滤按键事件等）。集成方若依赖特定事件类型，请核对新版 `assists_service.xml` 是否与业务一致。
- **前台服务（Android 14+）**：`assists-mp` 的 `MPService` 使用 `ServiceCompat.startForeground` 并声明 **mediaProjection** 类型，与 Manifest 及权限一致；**simple** 示例的前台服务改为 **specialUse** 类型并配合 Manifest，避免 API 34+ 缺少类型导致的异常。
- **示例应用**：Manifest 与 Overlay 等随上述约束做小调整；移除示例内重复的独立无障碍 XML，统一引用核心库声明。

#### 集成方升级到 3.5.0 的适配说明

以下为**任意依赖 assists 的应用或中间层库**在升级到 **3.5.0** 时的自检步骤；按顺序核对可减少编译错误与运行时异常。

1. **构建栈**：本版本与 **Android Gradle Plugin 8.9.x**、**Kotlin 2.1.x**、**Gradle 8.11.x** 档及 **compileSdk / targetSdk 36** 对齐。请在宿主工程中同步升级上述组合（可参考本仓库根目录 [`build.gradle`](build.gradle)、[`settings.gradle`](settings.gradle)、[`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties)）。若宿主同时使用 **Kotlin 2.x** 与 **Room**：优先用 **KSP** 替代 kapt 引入 **`room-compiler`**，以免 suspend DAO 与 kapt 在 Kotlin 2.x 下不兼容。
2. **无障碍配置 XML**
   - **引用库内默认声明**：在 `AndroidManifest` 的无障碍服务 `meta-data` 中使用 **`@xml/assists_service`** 时，各档位资源由 **`assists-base`（或等价工件）**随版本提供；一般**无需**在应用内复制 `assists_service.xml`，除非你 intentionally 覆盖。
   - **使用自定义资源名**：若 `android:resource` 指向**自有文件名**（例如 `@xml/my_accessibility_service`），须在应用的 **`res/xml/`** 以及 **`xml-v26` / `xml-v30` / `xml-v31` / `xml-v33` / `xml-v34`** 等目录中，将**同名文件**与本仓库 **`assists` 模块中对应 API 档位的 `assists_service.xml`**保持结构一致（事件类型、`notificationTimeout`、`accessibilityFlags`、`can*`、`intro` 等），仅保留你的 `@string/...` 文案。
   - **事件列表收窄**：默认不再订阅滚动、悬停等极高频事件；若业务脚本依赖这些事件，在应用模块对**实际生效的资源名**提供覆盖文件并扩展 `accessibilityEventTypes`。
3. **前台服务（targetSdk ≥ 34）**
   - **MediaProjection / 投屏**：依赖 **`assists-mp`** 时，`MPService` 已使用 **`mediaProjection`** 类型；合并后的 Manifest 应包含 **`FOREGROUND_SERVICE_MEDIA_PROJECTION`**（通常由 **`assists-mp`** AAR 合并，发布前用合并清单核对）。
   - **应用自有前台服务**：凡自行 **`startForeground`** 且非上述类型，须声明 **`foregroundServiceType`** 并使用 **`ServiceCompat.startForeground`**；用于「无法用其它类型概括」的场景可参考 **`simple`** 模块 [`ForegroundService`](simple/src/main/java/com/ven/assists/simple/ForegroundService.kt)：**specialUse** + Manifest 内 **`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`** + 权限 **`FOREGROUND_SERVICE_SPECIAL_USE`**。
4. **API 与行为**：无障碍开关判断请逐步改用 **`AssistsCore.isA11yEnabled()`**（详见上文 **3.3.0** 条目）；服务实例请使用 **`AssistsService.getOrNull()`**。升级后建议在 **API 34+** 设备上验证无障碍开启、投屏与前台服务路径无 **`SecurityException` / `MissingForegroundServiceTypeException`**。

### 3.4.0（2026-04-22）

- **节点获取增强**：默认走「全窗口」根节点聚合（`NodeLookupScope`），可遍历 **PopupWindow**、**系统级浮窗** 等独立窗口层；许多在 **uiautomatorviewer 中不可见或无法展开** 的节点，在此模式下可被查找与导出。相关查找、`getRootNodeTree*`、包名解析等 API 已与之对齐。
- **节点类型**：新增 `AssistsNodeClassNames` 及配套 `isXxx()` 判断，扩充常见系统 / AndroidX / Material 类名；原 `NodeClassValue` 与 `AssistsCore` 内旧扩展标为过时，建议迁移至 `com.ven.assists.utils`。
- **逻辑与稳定性**：修正多条件过滤、全文匹配、双击手势返回值与触摸层调度等边界行为，并做少量清理与内部去重。

### 3.3.0（2026-04-21）

- 新增：`isA11yEnabled` 判断无障碍是否已在系统设置中开启；省略 `Context` 时使用 `AssistsCore.init` 保存的 `Application`；支持 manifest 中注册为 `AssistsService` 子类的实现。
- 废弃：`isAccessibilityServiceEnabled`，请改用 `isA11yEnabled`。
- 修改：`AssistsService` 使用 `getOrNull()`（`@JvmStatic`）获取实例，`instance` 标为过时。
- 修改：无障碍服务资源补充 `xml-v31` 等声明，适配更高系统版本。

### 3.2.222（2026-04-09）

- 新增：（仅示例）演示录制屏幕后识别词组位置。

### 3.2.221（2026-04-08）

- 新增：在核心库中提供中文屏幕文字识别，方便多处复用。
- 修改：网页端文字识别能力与核心库保持一致，便于在页面脚本里做词组或全屏文字定位。

### 3.2.220（2026-04-07）

- 新增：内置日志能力，并支持在网页里读写日志、订阅变化以及向服务端上报，便于排查问题。
- 新增：新的浮窗体系，用浮窗承载界面交互，替代原先基于覆盖层的用法。
- 新增：为网页脚本补充路径、文件、网络、相册、系统栏、输入法、图片等一类系统级能力入口。
- 新增：支持保存截图与导出无障碍节点树等能力，便于自动化分析与编排。
- 修改：异步通道不再负责网页覆盖层相关逻辑；加载或关闭这类界面需改走主通道或新的浮窗能力，以免行为不一致。

### 3.2.218（2026-02-06）

- 新增：网页脚本侧可读取系统剪贴板文本，与前一版已提供的基础能力配套使用。

### 3.2.215（2026-02-06）

- 新增：在核心侧支持剪贴板读取，并接入屏幕文字识别（词组与区域等），供上层页面与脚本调用。

### 3.2.214（2025-11-02）

- 修改：调整保持屏幕常亮相关默认值与缓存清理逻辑，减少异常场景下的错误表现。

### 3.2.213（2025-10-31）

- 新增：支持控制屏幕常亮，并优化使用系统浏览器打开链接时的行为。
- 新增：网页侧可获取设备与网络信息、判断应用是否已安装；并支持从网页侧请求用外部浏览器打开指定链接。

### 3.2.212（2025-10-16）

- 重构：步骤执行相关模块的包路径与结构，便于维护与扩展。
- 新增：在覆盖层场景中增加微信未读消息检查能力。

### 3.2.211（2025-10-15）

- 新增：网页脚本可通过统一入口发起 HTTP 请求，并配置超时、请求头与正文等，获取响应状态与内容。

### 3.2.210（2025-09-30）

- 新增：网页侧可为无障碍事件配置过滤条件，减少无关事件干扰。
- 新增：节点边界与几何相关信息更丰富，便于脚本计算位置与点击。

### 3.2.204（2025-09-23）

- 新增：网页侧可读取的节点属性扩展（如提示文案、可选中与聚焦状态、密码框与可见性、绘制顺序等），便于脚本判断控件细节。

### 3.2.203（2025-09-14）

- 修复：关闭网页浮窗后，相关脚本或状态未正确清理的问题。
- 修改：改进网页浮窗拖动与展示效果，以及节点信息回传网页时的序列化与特殊字符处理，避免解析失败。
