# API 概览：Assists Web 框架能力列表

本文档描述 **assists-web** 模块通过 WebView 向前端（JS）暴露的能力。前端通过 `assistsx.call(json)`（同步）或 `assistsxAsync.call(json)`（异步）调用，部分子模块使用独立对象（如 `assistsxBarUtils`、`assistsxPath` 等）调用。回调通过 `assistsxCallback`（Base64 编码的 JSON）或各子模块的 `*Callback` 接收。

---

## 调用约定

- **请求**：`CallRequest` JSON，包含 `method`、`arguments`、`nodes`/`node`、`callbackId` 等。
- **响应**：`CallResponse` JSON，包含 `code`（0 成功）、`data`、`message`、`callbackId`。
- **节点**：多数方法支持在根节点或指定 `node`（含 `nodeId`）上操作，节点由 `NodeCacheManager` 缓存，`nodeId` 来自查找类方法返回的 `Node`。

---

## 主接口：assistsx / assistsxAsync

**注入名**：`assistsx`（同步）、`assistsxAsync`（异步，耗时操作通过 `callbackId` 回调）。

### 无障碍与节点

| 方法 | 说明 |
|------|------|
| `findById` | 按 viewId 查找节点，可选 filterText/filterDes/filterClass；可传 node 在子树下查 |
| `findByText` | 按文本查找，可选 filterViewId/filterDes/filterClass |
| `findByTextAllMatch` | 按文本完全匹配查找 |
| `findByTags` | 按 className + 可选 viewId/text/des 查找 |
| `containsText` | 判断节点是否包含指定文本 |
| `getAllText` | 获取节点 text 与 contentDescription 列表 |
| `findFirstParentByTags` | 向上找第一个符合 className 的父节点 |
| `getAllNodes` | 获取当前窗口所有节点，可选 filterText/filterDes/filterClass/filterViewId |
| `getNodes` | 获取指定节点的递归子节点列表 |
| `findFirstParentClickable` | 向上找第一个可点击的父节点 |
| `getChildren` | 获取直接子节点列表 |
| `getBoundsInScreen` | 节点在屏幕中的区域 |
| `getBoundsInParent` | 节点在父容器中的区域 |
| `isVisible` | 节点是否可见，可选 compareNode、isFullyByCompareNode |
| `click` | 节点点击 |
| `longClick` | 节点长按 |
| `paste` | 向节点粘贴文本 |
| `focus` | 请求节点获得焦点 |
| `selectionText` | 设置节点文本选区（selectionStart/selectionEnd） |
| `setNodeText` | 设置节点文本 |
| `scrollForward` / `scrollBackward` | 节点前/后滚动 |

### 系统键与应用

| 方法 | 说明 |
|------|------|
| `back` | 返回键 |
| `home` | Home 键 |
| `notifications` | 打开通知栏 |
| `recentApps` | 最近任务 |
| `launchApp` | 按 packageName 启动应用 |
| `getPackageName` | 当前前台包名 |

### 手势

| 方法 | 说明 |
|------|------|
| `clickByGesture` | 坐标 (x,y) 手势点击，可选 switchWindowIntervalDelay、duration |
| `clickNodeByGesture` | 在 node 位置手势点击，可选 offsetX/offsetY、switchWindowIntervalDelay、clickDuration |
| `doubleClickNodeByGesture` | 在 node 位置双击手势，可选 clickInterval 等 |
| `performLinearGesture` | 直线手势，startPoint/endPoint、duration、switchWindowIntervalDelay |
| `longPressGestureAutoPaste` | 坐标或节点处长按并自动点「粘贴」，参数 text、matchedText、matchedPackageName、timeoutMillis、longPressDuration 等 |

### 截图与界面

| 方法 | 说明 |
|------|------|
| `takeScreenshot` | 截取指定 nodes 区域，返回 Base64 图片列表（可先隐藏浮窗 overlayHiddenScreenshotDelayMillis） |
| `takeScreenshotSave` | 截屏并保存到文件（异步） |
| `takeScreenshotToFile` | 截屏并保存到指定路径（异步） |
| `getScreenSize` | 屏幕宽高 |
| `getAppScreenSize` | 当前应用窗口在屏幕中的区域 |
| `overlayToast` | 浮窗 Toast，参数 text、delay |
| `setOverlayFlags` | 设置浮窗 flags（数组） |
| `loadWebViewOverlay` | 加载 WebView 浮窗，参数 url、initialWidth/Height、minWidth/minHeight、initialCenter、keepScreenOn 等 |

### 设备与剪贴板

| 方法 | 说明 |
|------|------|
| `getAppInfo` | 指定包名的应用信息（异步回调） |
| `getDeviceInfo` | 设备信息（唯一 ID、AndroidID、MAC、厂商、型号、SDK、ABI、模拟器/平板、是否 root、ADB/开发者选项状态等）（异步回调） |
| `getUniqueDeviceId` | 设备唯一 ID |
| `getAndroidID` | Android ID |
| `getMacAddress` | MAC 地址（异步回调） |
| `getClipboardLatestText` | 剪贴板最新一条内容（text/uri/intent 等） |
| `getClipboardText` | 获取剪贴板文本（异步，支持后台） |

### 网络与其它

| 方法 | 说明 |
|------|------|
| `httpRequest` | 发起 HTTP 请求，参数 url、method、headers、body、timeout；异步回调 statusCode、body、headers |
| `getNetworkType` | 网络类型（异步回调） |
| `isAppInstalled` | 指定包名是否已安装 |
| `openUrlInBrowser` | 用系统浏览器打开 URL |
| `keepScreenOn` | 屏幕常亮，可选 tip |
| `clearKeepScreenOn` | 取消屏幕常亮 |
| `download` | 下载文件到本地（异步） |
| `audioPlayFromFile` | 从文件路径播放音频（**assistsxAsync**） |
| `audioStop` | 停止音频播放（**assistsxAsync**） |
| `audioPlayRingtone` | 播放系统电话铃声 |
| `audioStopRingtone` | 停止铃声 |
| `recognizeTextInScreenshot` | 截图中文字识别（异步） |
| `addContact` | 添加联系人（异步） |
| `getAllContacts` | 获取全部联系人（异步） |
| `saveRootNodeTreeJson` | 根节点树保存为 JSON 文件（异步） |
| `setAccessibilityEventFilters` | 设置无障碍事件过滤列表（value 为 AccessibilityEventFilter 数组） |
| `addAccessibilityEventFilter` | 追加一条无障碍事件过滤（value 为单条 AccessibilityEventFilter） |
| `scanQR` | 调起扫码，返回 `value` 字符串（会先 hideAll 浮窗，结束后 showTop；异步回调） |

> **说明（与源码一致）**
>
> - `closeOverlay`：在 `CallMethod` 中保留常量名，但 **`assistsx` / `assistsxAsync` 主接口未实现对应分支**（未匹配方法时返回 `code = -1`）。关闭 Web 浮窗请使用 **`assistsxFloat.close`**。
> - `pathJoin`：在 `PathCallMethod` 中定义，但 **`assistsxPath` 实现里已注释**，当前不可用。

---

## 无障碍事件回调（前端）

当设置了 `setAccessibilityEventFilters` 或 `addAccessibilityEventFilter` 且匹配到事件时，WebView 会执行 `onAccessibilityEvent(encodedJson)`，前端需定义全局函数 `onAccessibilityEvent`，参数为 Base64 编码的 `CallResponse`，其中 `data` 包含 packageName、className、eventType、action、texts、node 等。

---

## 日志流推送（全局，可选）

`ASWebView` 会订阅 `AssistsLog.latestLine` 与 `AssistsLog.entireLogText`，在内容变化时调用全局函数（若存在）：

- **函数名**：`onAssistsLogUpdate(encodedBase64)`
- **载荷**：Base64 编码的 `CallResponse`，`data.stream` 为 `latestLine` 或 `entireLogText`，`data.text` 为对应文本。

与下方 **`assistsxLog`** 的 `subscribe` 可并存；全局推送适合简单展示，订阅适合带 `callbackId` 的异步流程。

---

## 子接口：assistsxBarUtils

**注入名**：`assistsxBarUtils`，**回调**：`assistsxBarUtilsCallback`。需在 Activity 环境下使用。

| 方法 | 说明 |
|------|------|
| `getStatusBarHeight` | 状态栏高度 |
| `setStatusBarVisibility` | 设置状态栏显示/隐藏 |
| `isStatusBarVisible` | 状态栏是否可见 |
| `setStatusBarLightMode` | 状态栏是否浅色模式（深色文字） |
| `isStatusBarLightMode` | 是否浅色模式 |
| `setStatusBarColor` | 设置状态栏颜色 |
| `transparentStatusBar` | 透明状态栏 |
| `getActionBarHeight` | ActionBar 高度 |
| `getNavBarHeight` | 导航栏高度 |
| `setNavBarVisibility` | 导航栏显示/隐藏 |
| `isNavBarVisible` | 导航栏是否可见 |
| `setNavBarColor` / `getNavBarColor` | 设置/获取导航栏颜色 |
| `isSupportNavBar` | 是否支持导航栏 |
| `setNavBarLightMode` / `isNavBarLightMode` | 导航栏浅色模式 |
| `transparentNavBar` | 透明导航栏 |

---

## 子接口：assistsxPath

**注入名**：`assistsxPath`。路径与目录常量（与 Blankj `PathUtils` 等对齐）。

- `getRootPath`、`getDataPath`、`getDownloadCachePath` ……
- `getInternalAppDataPath`、`getInternalAppFilesPath`、`getInternalAppCachePath` 等
- `getExternalStoragePath` 及各类外部公共目录
- `getExternalAppDataPath`、`getExternalAppCachePath` 等应用外部目录
- `getRootPathExternalFirst`、`getAppDataPathExternalFirst`、`getFilesPathExternalFirst`、`getCachePathExternalFirst` 等优先外部存储路径

---

## 子接口：assistsxFileUtils

**注入名**：`assistsxFileUtils`。文件/目录操作（存在性、创建、复制、移动、删除、列表、MD5、空间等）。详见实现类 `FileUtilsJavascriptInterface`。

---

## 子接口：assistsxFileIO

**注入名**：`assistsxFileIO`。文件读写（流/通道/MMap、按行/字符串/字节等）。详见 `FileIOJavascriptInterface`。

---

## 子接口：assistsxHttp

**注入名**：`assistsxHttp`。独立 HTTP 封装：`httpGet`、`httpPost`、`httpPostFile`、`httpDownload`、`httpConfigure`、`httpReset`、`httpGetConfig`。

---

## 子接口：assistsxIme

**注入名**：`assistsxIme`：`performEditorAction`、`openInputMethodSettings`、`isInputMethodEnabled`、`isCurrentInputMethod`。

---

## 子接口：assistsxImageUtils

**注入名**：`assistsxImageUtils`。图片尺寸、类型、变换、水印、模糊、压缩、保存等。详见 `ImageUtilsJavascriptInterface`。

---

## 子接口：assistsxMlkit

**注入名**：`assistsxMlkit`。ML Kit 文字识别（基于当前截图）：`findPhrasePositions`、`getScreenTextPositions` 及 `*AsJson` 变体。

---

## 子接口：assistsxGallery

**注入名**：`assistsxGallery`：`addImageToGallery`、`addVideoToGallery`、`deleteFromGallery`。

---

## 子接口：assistsxFloat

**注入名**：`assistsxFloat`，**回调**：`assistsxFloatCallback`。浮窗独立能力（与主接口中的 `loadWebViewOverlay` 等配合使用）。

| 方法 | 说明 |
|------|------|
| `open` | 打开浮窗；参数含 url、initialWidth/Height、initialX/Y、minWidth/Height、initialCenter、keepScreenOn、showTopOperationArea、showBottomOperationArea、backgroundColor 等；返回含 `uniqueId` |
| `close` | 关闭当前 Web 所在浮窗并销毁 WebView |
| `setFlags` | 设置浮窗 flags |
| `toast` | 浮窗 Toast |
| `move` | 移动浮窗（x、y 为**相对当前位置的位移**） |
| `refresh` | 刷新当前浮窗布局/样式；可选 showTopOperationArea、showBottomOperationArea、backgroundColor（支持 `"default"` 恢复 drawable）、width、height、x、y |
| `hideAll` | 对应 `AssistsWindowManager.hideAll`，可选 isTouchable |
| `hideTop` | 隐藏最顶层浮窗 |
| `showAll` / `showTop` | 显示全部 / 最顶层浮窗 |
| `temporarilyHideAll` | 临时隐藏全部，参数 durationMs、isTouchable |
| `touchableByAll` / `nonTouchableByAll` | 全部浮窗可触摸 / 不可触摸 |
| `pop` | 移除栈顶浮窗，可选 showTop |
| `removeAllWindows` | 移除所有浮窗（**必须** `confirm: true`） |
| `hideCurrent` / `showCurrent` | 仅当前 Web 浮窗隐藏/显示 |
| `isCurrentVisible` / `containsCurrent` | 当前 Web 浮窗是否可见 / 是否已加入管理器 |

---

## 子接口：assistsxLog（新增）

**注入名**：`assistsxLog`，**回调**：`assistsxLogCallback`（Base64 JSON）。用于读写 `AssistsLog` 单文件日志、订阅流、上传诊断包、查询日志服务域名。

**管理后台**：`uploadLogs` 将日志文件、截图、节点树 JSON 一并提交到日志服务后，可在 **Assists 管理后台** 中查看对应的 **文本日志**、**截图** 与 **节点树信息**（与 `getLogServiceBaseUrl` 返回的站点同源；默认根地址与原生 `AssistsLogDiagnostics.adminWebBaseUrl()` 一致）。若你在业务里自定义 `baseUrl` 上传，请以实际部署的后台为准。

同步入口 `call` 立即返回占位 `CallResponse(code=0)`，真实结果通过 **`assistsxLogCallback`** 异步返回。

| 方法 | 说明 |
|------|------|
| `readAllText` | 读取当前日志全文，`data.text` |
| `clear` | 清空日志文件并推送空内容 |
| `refreshFromFile` | 从磁盘重新读入并更新内存态 |
| `appendLine` | 追加字符串；参数 `line`，可选 `maxLength`（默认与 `AssistsLog.DEFAULT_MAX_FILE_LENGTH` 一致） |
| `appendTimestampedEntry` | 追加带时间戳的条目，参数 `message` |
| `replaceAll` | 用 `content` 整体覆盖；空串等价于 clear |
| `subscribe` | 订阅流；参数 `stream`：`latestLine` 或 `entireLogText`；先回调 `event: subscribed` 与 `subscriptionId`，后续多次 `event: update` 与 `text`；需配合 `callbackId` |
| `unsubscribe` | 参数 `subscriptionId`，取消对应订阅 |
| `uploadLogs` | **API 30+**：截图 + 节点树 + 日志 multipart 上传。可选 `baseUrl`（完整上传接口 URL；不传则用内置默认）、`format`（PNG/JPEG/WEBP）、`prettyPrint`、`overlayHiddenDelayMillis`、`uploadKey`（非空则覆盖本次 `X-Upload-Key`） |
| `getLogServiceBaseUrl` | 返回 `data.baseUrl`（管理后台/日志服务 **站点根地址** origin，无路径；可在浏览器打开以进入后台查看已上传的日志、截图与节点树） |

---

## 依赖说明

- **assists-web** 依赖：`assists`、`assists-mp`（截图）、**assists-log**（日志与诊断上传）、可选 assists-base；第三方（OkHttp、Gson、BarUtils、DeviceUtils、ML Kit、扫码等）。
- 前端页面需在 WebView 内加载，并通过上述注入名调用；异步结果通过对应 `Callback` 或 `callbackId` 回传。
