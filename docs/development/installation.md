# 安装指南

本文档说明 Assists 的依赖、构建方式与安装步骤。

## 依赖

- **JDK**：17 或更高
- **Android Gradle Plugin**：版本见项目根目录 `build.gradle` 或 `gradle.properties`
- **Gradle**：使用项目自带或指定版本（见 `gradlew` / `gradle/wrapper`）
- **Android SDK**：编译与运行所需 API 级别见各模块 `build.gradle`

## 构建类型

| 任务 | 说明 |
|------|------|
| `assembleDebug` | 调试版 APK |
| `assembleRelease` | 发布版 APK（需配置签名） |
| `bundleRelease` | Android App Bundle（如支持） |

## 安装到设备

构建完成后，可通过 ADB 安装：

```bash
adb install -r path/to/your-app-debug.apk
```

或在 Android Studio 中通过 Run 直接安装并启动。

## 常见问题

安装或构建失败时，可参考 [常见问题](faq.md)。
