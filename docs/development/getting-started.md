# 快速开始

本文档帮助你在本地快速搭建 Assists 开发环境并运行项目。

## 前置要求

- Android Studio（推荐最新稳定版）
- JDK 17+
- Android SDK（API 级别以项目 `build.gradle` 为准）
- Git

## 克隆与打开项目

```bash
git clone <repository-url>
cd assists
```

使用 Android Studio 打开项目根目录，等待 Gradle 同步完成。

## 构建与运行

```bash
# 调试构建
./gradlew assembleDebug

# 运行测试
./gradlew test
```

在 Android Studio 中选择目标设备/模拟器，点击 Run 即可运行应用。

## 下一步

- [安装指南](installation.md)：详细依赖与构建选项
- [配置说明](configuration.md)：环境与运行时配置
- [开发指南](guides/README.md)：专题开发教程
