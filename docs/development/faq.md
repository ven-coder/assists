# 常见问题

本文档汇总开发与使用过程中的常见问题与解决办法。

## 构建与环境

**Q：Gradle 同步失败或依赖下载超时？**  
A：检查网络与代理，必要时配置 Gradle/ Maven 镜像；确认 JDK 与 Android SDK 版本符合 [安装指南](installation.md)。

**Q：编译报错找不到 SDK 或 NDK？**  
A：在 `local.properties` 中配置 `sdk.dir`（及 `ndk.dir` 如需要），或使用 Android Studio 的 SDK Manager 安装对应组件。

## 运行与调试

**Q：WebView 中调用 `assistsx` 无响应？**  
A：确认 WebView 已注入接口、页面已加载完成，以及请求 JSON 格式符合 [WEB_CAPABILITIES](../../WEB_CAPABILITIES.md) 中的约定；可查看 Logcat 中相关 TAG 的日志。

**Q：节点查找返回空或报错？**  
A：确认无障碍/所需权限已开启，当前界面已有对应节点，且传入的 `filterText`/`filterDes`/`filterClass` 等条件与真实节点一致；注意部分厂商对无障碍或节点信息的限制。

## 文档与 API

**Q：能力列表在哪里？**  
A：Web 端能力见 [WEB_CAPABILITIES.md](../../WEB_CAPABILITIES.md)，Native 能力见 [NATIVE_CAPABILITIES.md](../../NATIVE_CAPABILITIES.md)；开发文档入口为 [开发文档 README](README.md)。

**Q：如何参与贡献？**  
A：请阅读 [贡献指南](contributing.md)，按分支、开发、测试、文档、PR 流程参与。

---

如有未覆盖的问题，欢迎提 Issue 或补充到本文档。
