# 更新日志

### 版本3.2.222

- 新增：（仅示例）Pro 浮窗「录制截图并识别屏幕词组位置」流程（主库无 API 变更）。

### 版本3.2.221

- 新增：屏幕中文文字识别迁入 assists 核心（`TextRecognitionChineseLocator`）。
- 新增：（仅示例）扩展示例浮窗与中文日志相关展示。
- 修改：`assistsxMlkit` 与 Kotlin 侧识别能力对齐（词组/全屏文字位置等）。

### 版本3.2.220

- 新增：`assists-log` 模块与 H5 桥 `assistsxLog`（本地日志、订阅、上传及与后台配合）。
- 新增：浮窗体系 `assistsxFloat`（多层显隐、`temporarilyHideAll`、当前 Web 浮窗显隐等）。
- 新增：`assistsxBarUtils`、`assistsxGallery`、主桥剪贴板与截图/节点树 JSON 保存增强；Core 侧剪贴板与 ML Kit 识别基础能力。
- 新增：路径/文件/网络/音频/图片等分桥（`assistsxPath`、`assistsxFileIO`、`assistsxFileUtils`、`assistsxHttp`、`assistsxIme`、`assistsxImageUtils` 等）。
- 修改：`assistsxAsync` 移除 WebView 覆盖层相关实现；加载浮层请用主桥 `assistsx` 的 `loadWebViewOverlay` 或改用 `assistsxFloat`。
- 修改：工程依赖改为本地多模块；JitPack 构建修复；IME 与根工程 SDK 对齐。
