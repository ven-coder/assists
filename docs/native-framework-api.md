# API 概览：Assists 框架原生能力方法列表

本文档描述 Assists 框架的**原生能力**：按模块、类、方法列出，并简要说明每个方法的作用。区别于通过 Web/JS 桥等扩展暴露的能力，此处均为 Kotlin/Java API 直接提供的能力。

---

## 模块：assists

### AssistsCore

- `init(application)` — 初始化 AssistsCore，设置全局日志 TAG
- `openAccessibilitySetting()` — 打开系统无障碍设置页，引导用户开启服务
- `isAccessibilityServiceEnabled()` — 判断无障碍服务是否已开启
- `getPackageName()` — 获取当前前台窗口所属应用包名
- `keepScreenOn(tip)` — 屏幕保持常亮并显示提示
- `clearKeepScreenOn()` — 取消屏幕常亮
- `findById(id, filterText?, filterDes?, filterClass?)` — 从根节点按 viewId 查找节点，可叠加文本/描述/类名过滤
- `AccessibilityNodeInfo?.findById(...)` — 在指定节点下按 id 查找
- `findByText(text, ...)` — 从根节点按文本查找
- `findByTextAllMatch(text, ...)` — 从根节点按文本完全匹配查找
- `AccessibilityNodeInfo?.findByText(...)` — 在指定节点下按文本查找
- `AccessibilityNodeInfo?.containsText(text)` — 判断节点或其描述是否包含某文本
- `AccessibilityNodeInfo?.getAllText()` — 获取节点 text 与 contentDescription 列表
- `findByTags(className, viewId?, text?, des?)` — 从根节点按类名+可选 id/文本/描述多条件查找
- `AccessibilityNodeInfo.findByTags(...)` — 在指定节点下按多标签查找
- `AccessibilityNodeInfo.findFirstParentByTags(className)` — 向上找第一个符合类名的父节点
- `AccessibilityNodeInfo.findFirstParentByTags(className, container)` — 同上，结果放入 container
- `getAllNodes(filterViewId?, ...)` — 获取当前窗口所有节点，可过滤
- `AccessibilityNodeInfo.getNodes()` — 获取该节点及其递归子节点列表
- `AccessibilityNodeInfo.findFirstParentClickable()` — 向上找第一个可点击的父节点
- `AccessibilityNodeInfo.getChildren()` — 获取直接子节点列表
- `dispatchGesture(gesture, nonTouchableWindowDelay)` — 执行手势，执行前将浮窗设为不可触摸
- `gesture(startLocation, endLocation, startTime, duration)` — 执行直线滑动手势
- `gesture(path, startTime, duration)` — 执行自定义路径手势
- `AccessibilityNodeInfo.getBoundsInScreen()` — 获取节点在屏幕上的矩形区域
- `AccessibilityNodeInfo.getBoundsInParent()` — 获取节点在父容器中的矩形区域
- `AccessibilityNodeInfo.click()` — 执行节点点击
- `AccessibilityNodeInfo.longClick()` — 执行节点长按
- `gestureClick(x, y, duration)` — 在坐标 (x,y) 执行点击手势
- `longPressByGesture(x, y, duration)` — 在坐标处长按手势
- `AccessibilityNodeInfo.nodeGestureClick(...)` — 在节点位置执行点击手势（可设偏移与不可触摸窗口）
- `AccessibilityNodeInfo.nodeGestureClickByDouble(...)` — 在节点位置执行双击手势
- `AccessibilityNodeInfo.isVisible(compareNode?, isFullyByCompareNode?)` — 判断节点是否可见，可选与另一节点做遮挡判断
- `back()` — 执行返回键
- `home()` — 执行 Home 键
- `notifications()` — 打开通知栏
- `recentApps()` — 打开最近任务
- `AccessibilityNodeInfo.paste(text)` — 向节点粘贴文本（先设剪贴板再 ACTION_PASTE）
- `AccessibilityNodeInfo.focus()` — 请求节点获得焦点
- `AccessibilityNodeInfo.longPressGestureAutoPaste(...)` — 在节点处长按并自动点「粘贴」菜单项
- `longPressGestureAutoPaste(x, y, text, ...)` — 在坐标处长按并自动粘贴
- `AccessibilityNodeInfo.selectionText(selectionStart, selectionEnd)` — 设置节点文本选区
- `AccessibilityNodeInfo.setNodeText(text)` — 设置节点文本（ACTION_SET_TEXT）
- `getX(baseWidth, x)` — 按基准宽度换算实际 X 坐标
- `getY(baseHeight, y)` — 按基准高度换算实际 Y 坐标
- `getAppBoundsInScreen()` — 获取当前应用窗口在屏幕中的区域
- `initAppBoundsInScreen()` — 初始化并缓存应用窗口区域
- `getAppWidthInScreen()` — 获取当前应用窗口宽度（用缓存）
- `getAppHeightInScreen()` — 获取当前应用窗口高度（用缓存）
- `AccessibilityNodeInfo.scrollForward()` — 节点向前滚动
- `AccessibilityNodeInfo.scrollBackward()` — 节点向后滚动
- `launchApp(intent)` — 通过 Intent 启动应用（借助浮窗点击）
- `launchApp(packageName)` — 通过包名启动应用
- `AccessibilityNodeInfo.takeScreenshotSave(file, format)` — 截取节点区域并保存到文件（API 30+）
- `takeScreenshotSave(file, format)` — 截取全屏并保存到文件（API 30+）
- `AccessibilityNodeInfo.takeScreenshot(screenshot?)` — 截取节点区域得到 Bitmap（API 30+）
- `takeScreenshot()` — 截取全屏得到 Bitmap（API 30+）
- `AccessibilityNodeInfo.getMD5(scale?, cornerRatio?, file?, format?)` — 截取节点区域并做缩放/圆角后计算 MD5（API 30+）
- `AccessibilityNodeInfo.logNode(tag)` — 在日志中打印节点位置、文本、id、类型等
- `AccessibilityNodeInfo.toNodeTree()` — 将节点转为 NodeTree（含子节点）
- `getRootNodeTree()` — 获取当前窗口根节点树
- `getRootNodeTreeJson(prettyPrint)` — 根节点树转 JSON 字符串
- `AccessibilityNodeInfo.toNodeTreeJson(prettyPrint)` — 指定节点树转 JSON 字符串
- `saveRootNodeTreeJson(file, prettyPrint)` — 根节点树 JSON 保存到文件
- `getClipboardText()` — 获取剪贴板文本（前台直接取，后台通过透明 Activity）
- 扩展：`isFrameLayout()` / `isTextView()` / `isButton()` 等 — 按 className 判断节点是否为对应控件类型
- `txt()` / `des()` — 获取节点 text / contentDescription 字符串

### AssistsService

- `onCreate()` — 服务创建时设置 instance
- `onServiceConnected()` — 连接成功后初始化窗口管理器并回调监听器
- `onAccessibilityEvent(event)` — 收到无障碍事件时分发给所有监听器
- `onUnbind(intent)` — 解绑时清空 instance 并回调监听器
- `onInterrupt()` — 服务被中断时回调监听器

**伴生**：`instance`（当前服务实例）、`listeners`（监听器列表）。

### AssistsServiceListener

- `onAccessibilityEvent(event)` — 界面发生无障碍事件时回调
- `onServiceConnected(service)` — 服务连接成功后回调
- `onInterrupt()` — 服务被中断时回调
- `onUnbind()` — 服务解绑时回调
- `screenCaptureEnable()` — 录屏/媒体投影权限开启时回调（可选实现）

### AssistsWindowManager

- `init(accessibilityService)` — 用无障碍服务初始化 WindowManager 与 DisplayMetrics
- `getWindowManager()` — 获取系统 WindowManager
- `createLayoutParams()` — 创建默认浮窗 LayoutParams（全屏、无障碍 overlay 等）
- `hideAll(isTouchable, filterViews)` — 隐藏所有浮窗，可选排除部分 View
- `hideTop(isTouchable)` — 隐藏最顶层浮窗
- `temporarilyHideDisplayedTopWindow(isTouchable)` — **挂起**：临时隐藏当前**已显示**的顶层非 Toast 浮窗，供截图等场景；再次调用会先恢复上一轮再重新快照
- `restoreTemporarilyHiddenTopWindow(isTouchable)` — **挂起**：恢复上一轮 `temporarilyHideDisplayedTopWindow` 隐藏的顶层浮窗
- `temporarilyHideAll(durationMs, isTouchable, filterViews)` — 临时隐藏全部浮窗（规则同 `hideAll`），超时后仅恢复隐藏前**本为可见**的窗口
- `hide(view, isTouchable)` — 隐藏指定浮窗
- `showTop(isTouchable)` — 显示最顶层浮窗
- `showAll(isTouchable)` — 显示所有浮窗
- `add(windowWrapper, isStack, isTouchable)` — 添加浮窗包装器
- `add(view, layoutParams, isStack, isTouchable, viewTag)` — 添加 View 为浮窗
- `setFlags(flag)` — 为所有浮窗设置同一 flags
- `push(view, params)` — 添加浮窗并隐藏之前的（栈式）
- `pop(showTop)` — 移除顶层浮窗，可选显示下一层
- `removeView(view)` — 移除指定浮窗（已废弃，建议用 removeWindow）
- `removeWindow(view)` — 移除指定 View 对应浮窗
- `removeWindow(viewTag)` — 按 viewTag 移除浮窗
- `removeAllWindow()` — 移除所有浮窗
- `contains(view)` / `contains(viewTag)` / `contains(wrapper)` — 判断是否已添加该浮窗
- `isVisible(view)` — 判断某浮窗是否可见
- `updateViewLayout(view, params)` — 更新浮窗布局参数
- `touchableByAll()` — 将所有浮窗设为可触摸
- `nonTouchableByAll()` — 将所有浮窗设为不可触摸
- `WindowManager.LayoutParams.focusInput()` — 设置布局为可输入焦点所需 flags
- `WindowManager.LayoutParams.touchableByLayoutParams()` — 设置布局为可触摸
- `WindowManager.LayoutParams.nonTouchableByLayoutParams()` — 设置布局为不可触摸
- `ViewWrapper.touchableByWrapper()` — 将该包装对应浮窗设为可触摸
- `ViewWrapper.nonTouchableByWrapper()` — 将该包装对应浮窗设为不可触摸
- `String.overlayToast(delay)` — 以浮窗形式显示 Toast 文本，指定时长后消失

**内部类**：`ViewWrapper(view, layoutParams, uniqueId)` 表示一个浮窗条目。

### AssistsWindowWrapper

- `ignoreTouch()` — 触摸时不消费事件，传递给下层
- `consumeTouch()` — 触摸时消费事件
- `getView()` — 获取包装的 View（实际为带操作栏的根 View）

通过构造参数与属性配置：初始位置、是否居中、是否显示移动/缩放/关闭、最小/最大宽高等。

### WindowMinimizeManager

- `show()` — 显示最小化浮窗并加入服务监听
- `hide()` — 隐藏最小化浮窗
- `close()` — 关闭并移除最小化浮窗
- `onUnbind()` — 实现 AssistsServiceListener，服务解绑时调用

### StepManager

- `execute(stepImpl, stepTag, delay, data, begin)` — 按 Class 执行指定步骤，可选作为起始步骤（重置 isStop）
- `execute(implClassName, stepTag, delay, data, begin)` — 按类名字符串执行指定步骤
- `register(implClassName)` — 注册步骤实现类（实例化并调用 onImpl 收集步骤）

**属性**：`DEFAULT_STEP_DELAY`、`stepListeners`、`coroutine`、`isStop`。

### Step

- `Step.get(tag, stepImpl?, data?, delay)` — 创建带可选实现类与数据的步骤
- `Step.nextStepImpl(tag, stepImpl?, data?, delay)` — 创建指定下一步实现类的步骤

**常量**：`Step.none`（结束）、`Step.repeat`（重复当前步骤）。

### StepImpl

- `onImpl(collector)` — 子类实现：在 collector 中注册各 stepTag 对应的逻辑
- `runIO(function)` — 在 IO 协程中执行挂起块
- `runMain(function)` — 在主线程协程中执行挂起块

### StepCollector

- `get(stepTag)` — 获取已注册的 StepOperator，未注册则抛异常
- `next(stepTag, isRunCoroutineIO, next)` — 注册一步：stepTag 对应 next 逻辑，返回 this 便于链式

### StepOperator

- `execute(delay, data)` — 延迟后执行该步骤的 next，根据返回的 Step 决定重复或进入下一步

**属性**：`implClassName`、`step`、`next`、`isRunCoroutineIO`、`data`、`repeatCount`。

### StepListener

- `onStepStart(step)` — 某步骤开始执行时回调
- `onStepStop()` — 步骤器停止时回调
- `onStepCatch(e)` — 步骤执行抛异常时回调
- `onStep(step)` — 步骤执行时回调
- `onLoop(step)` — 步骤循环时回调
- `onIntercept(step)` — 拦截并返回替代的 Step，可改变流程

### CoroutineWrapper

- `launch(isMain, block)` — 在默认或主线程协程作用域中启动协程

---

## 模块：assists-log（新增）

单文件日志、诊断截图/节点树与 multipart 上传；供原生与 **assists-web**（`assistsxLog`）共用。

### 管理后台（日志服务）

上传成功后，可在 **Assists 日志管理后台** 中查看与本次诊断关联的 **文本日志**、**屏幕截图** 与 **无障碍节点树（JSON）** 等信息，便于远程排查问题。后台与上传接口同源：默认根地址由 `AssistsLogDiagnostics.adminWebBaseUrl()` 返回（源码中默认为 `https://admin.assists.cn`，不含具体 API 路径）。宿主若自定义日志服务域名，需与 `uploadLogs` 使用的上传地址、密钥策略保持一致。

### AssistsLog（`object`）

- `DEFAULT_MAX_FILE_LENGTH` — 默认单文件最大字符数（超出时从头部丢弃最旧内容）
- `latestLine: SharedFlow<String>` — 每次写入发射本条追加片段（或覆盖时的片段）
- `entireLogText: StateFlow<String>` — 每次变更后的**整份**正文
- `appendTimestampedEntry(message)` — 追加「时间戳 + 换行 + 正文」条目
- `appendLine(line, maxLength)` — 原样拼接写入，超长时从头部截断
- `readAllText()` — 读取当前日志全文
- `refreshFromFile()` — 从磁盘同步到 `entireLogText`
- `clear()` — 清空文件与 Flow
- `replaceAll(content)` — 整体覆盖；空串等价于 `clear`

### AssistsLogPaths（`object`）

应用内部 `files` 目录下的固定文件名：

- `LOG_FILE_NAME`、`SCREENSHOT_FILE_NAME`、`NODE_TREE_FILE_NAME`
- `logFile()`、`screenshotFile(extension)`、`nodeTreeFile()`

### AssistsLogDiagnostics（`object`）

- `setUploadKey(key)` — 由宿主注入上传密钥（覆盖内置默认值，用于 `X-Upload-Key`）
- `adminWebBaseUrl()` — 管理后台根地址（origin，与上传接口同源）；浏览器打开该地址可在后台查看已上传的日志正文、截图与节点树等
- `takeScreenshotSaveToDefault(file?, format, overlayHiddenDelayMillis, targetNode?)` — **API 30+**，保存截图到默认或指定路径；可先临时隐藏顶层浮窗再截图
- `saveRootNodeTreeJsonToDefault(prettyPrint, file?)` — 将当前根节点树 JSON 存到默认或指定文件
- `uploadLogs(baseUrl, format, prettyPrint, overlayHiddenDelayMillis, uploadKey?)` — **API 30+**，顺序：截图 → 保存节点树 → multipart 上传；`baseUrl` 默认为内置完整上传 URL；`uploadKey` 非空则作为本次请求头

**相关数据类**：`AssistsLogUploadResult`、`LogUploadApiResponse`、`LogUploadData`（上传结果与 HTTP 解析结构，见源码）。

### 扩展函数（`LogExtensions.kt`）

- `Any?.log()` — 追加一行（字符串原样；其它类型优先 Gson JSON）
- `Any?.logAppend()` — 与 `appendTimestampedEntry` 一致，链式写入

---

## 模块：assists-mp

### MPManager

- `init(application)` — 注册 Activity 生命周期，用于媒体投影结果回调
- `request(autoAllow, timeOut)` — 请求媒体投影权限，可选自动点「立即开始/开始」
- `takeScreenshot2Bitmap()` — 获取当前屏幕截图 Bitmap（需先 request 成功）
- `AccessibilityNodeInfo.getBitmap(screenshot?)` — 根据节点区域从全屏截图中裁剪出节点 Bitmap
- `AccessibilityNodeInfo.takeScreenshot2File(screenshot?, file)` — 将节点区域截图保存到文件
- `takeScreenshot2File(file)` — 将全屏截图保存到文件

**常量**：`REQUEST_CODE`、`REQUEST_DATA`。**属性**：`onEnable`、`mediaProjectionCallback`、`isEnable`。

---

## 模块：assists-opcv

### OpencvWrapper

- `init()` — 后台协程加载 OpenCV 本地库并打日志
- `matchTemplate(image, template, mask?)` — 模板匹配，返回结果 Mat（TM_CCORR_NORMED）
- `getResultWithThreshold(result, threshold, ignoreX?, ignoreY?)` — 从匹配结果中取大于等于阈值的点列表，可忽略相近点
- `matchTemplateFromScreenToMinMaxLoc(image, template, mask?)` — 模板匹配并返回最小/最大值及位置
- `createMask(source, lowerScalar, upperScalar, requisiteExtraRectList?, redundantExtraRectList?)` — HSV 阈值生成掩膜，可选黑色/白色矩形区域
- `getScreenMat()` — 获取当前屏幕的 Mat（BGR），依赖 MPManager 截图
- `getTemplateFromAssets(assetPath)` — 从 Assets 加载图片并转为 Mat（BGR）

---

## 模块：assists-ime

### LatinIME

输入法主入口，继承 `InputMethodService`，实现 `KeyboardActionListener`。

- `onCreate()` / `onDestroy()` — 初始化与释放
- `onEvaluateInputViewShown()`、`onCreateInputView()`、`setInputView` / `setCandidatesView`
- `onStartInput` / `onStartInputView`、`onFinishInputView` / `onFinishInput`
- `onWindowShown()` / `onWindowHidden()`、`hideWindow()`、`onComputeInsets`、`onShowInputRequested`
- `onEvaluateFullscreenMode()` / `updateFullscreenMode()`、`onCustomRequest`
- `getCurrentLayoutLocale()`、`onMoveCursorPointer` / `onMoveDeletePointer`
- `onCodeInput`、`onTextInput`、`onPressKey` / `onReleaseKey`
- `launchSettings()`、`switchToNextSubtype()`
- `performEditorAction(actionId)`、`openInputMethodSettings(context)`（静态）
- `isInputMethodEnabled(context)` / `isCurrentInputMethod(context)`（静态）

其它类（InputLogic、Keyboard 等）为内部实现，对外能力主要通过 LatinIME 与系统 API。

---

## 依赖关系

- **assists**：无框架内模块依赖（可依赖 AndroidX 等）。
- **assists-log**：依赖 **assists**（诊断截图与节点树依赖 AssistsCore / 浮窗管理；上传使用 OkHttp 等，见模块 `build.gradle`）。
- **assists-mp**：依赖 assists。
- **assists-opcv**：依赖 assists、assists-mp。
- **assists-ime**：独立输入法，不依赖 assists。

如需某方法的参数与返回值细节，请直接查看对应类的源码或 KDoc。
