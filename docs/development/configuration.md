# 配置说明

本文档描述 Assists 的构建与运行时配置方式。

## 构建配置

- **Gradle**：根目录 `build.gradle`、`settings.gradle`，各模块 `build.gradle`
- **局部配置**：`local.properties`（SDK 路径等，不提交版本库）
- **环境变量**：如需通过环境变量控制构建，可在 `build.gradle` 中读取 `System.getenv()`

## 运行时配置

- 应用级配置：根据项目实际使用方式（如 `BuildConfig`、配置文件、远程配置）在对应模块中维护
- 权限与组件：见 `AndroidManifest.xml` 及各模块说明

## 扩展与定制

若项目支持通过配置文件或 BuildConfig 开关功能，可在此补充具体字段与含义。
