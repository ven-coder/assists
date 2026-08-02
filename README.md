<div align="center"><a name="readme-top"></a>

<img src="https://github.com/user-attachments/assets/d8179c9e-cfab-4dc4-bb4d-5d8517a92b56" width="800" />

基于Android无障碍服务（AccessibilityService）封装的自动化开发框架

简化自动化脚本开发 · 提供各种增强能力 · 提高脚本易维护性 · 支持Web平台自动化脚本开发

[![][maven-central-shield]][maven-central-link]
[![][license-shield]][license-link]
[![][stars-shield]][stars-link]
[![][forks-shield]][forks-link]
[![][issues-shield]][issues-link]

[📱 下载Demo][demo-download] · [🤖 Assists MCP][assists-mcp-link] · [📜 更新日志](CHANGELOG.md) · [📘 开发文档][docs-link] · [🐛 反馈问题][issues-link] · [💬 交流反馈](#交流反馈) · [💰 赞助支持](#-赞助支持) · [⭐ Star支持][stars-link]

</div>

---

## 📖 目录

<details>
<summary><kbd>展开目录</kbd></summary>

- [📖 目录](#-目录)
- [🎯 适用场景](#-适用场景)
- [💡 为什么选 Assists](#-为什么选-assists)
- [🌟 基于 Assists 的开发生态](#-基于-assists-的开发生态)
- [🤖 Assists MCP · AI Agent 协作](#-assists-mcp--ai-agent-协作)
- [📋 核心能力](#-核心能力)
- [🎬 功能示例](#-功能示例)
- [开发文档](#开发文档)
  - [依赖集成（Maven Central）](#依赖集成maven-central)
- [交流反馈](#交流反馈)
- [💝 支持开源](#-支持开源)
  - [⭐ Star支持](#-star支持)
  - [💰 赞助支持](#-赞助支持)
- [Star History](#star-history)
- [License](#license)

</details>

## 🎯 适用场景

基于无障碍与 Assists 生态，可开发各类**自动化脚本**与**辅助工具**，例如：

- 微信自动抢红包、自动接听电话
- 支付宝蚂蚁森林浇水、芭芭农场施肥与能量收集
- 各平台拓客、引流、营销自动化
- 远程控制与设备协作

上述能力可基于 **原生（assists）** 或 **Web（assistsx-js + AssistsX）** 两种方式实现，详见 [开发生态](#-基于-assists-的开发生态)。

## 💡 为什么选 Assists

Assists 是整条生态的**基础库**：基于 Android 无障碍服务封装，提供统一 API 与增强能力，让上层模块与应用专注业务而非底层细节。

- **开发更简单**：无障碍能力封装为易用 API，减少样板代码
- **能力可扩展**：浮窗、截图、输入法、图像识别、Web 桥接等按需选用
- **脚本易维护**：步骤器与模块化设计，便于复用与迭代
- **技术栈灵活**：支持原生开发，也支持 HTML + JS/Vue 的 Web 自动化（[assistsx-js][assistsx-js-link]）

## 🌟 基于 Assists 的开发生态

> **从基础库到运行平台、从 Native 到 Web，一套完整可用的自动化开发生态已围绕 Assists 形成。**  
> 无论你偏好原生开发还是 Web 技术栈，都能找到对应模块与示例，快速上手、持续扩展。

| 角色 | 项目 | 说明 |
|------|------|------|
| **🏠 基础库** | **assists** | 自动化**基础库**：无障碍服务封装、能力增强 API、浮窗管理，一切能力的根基。 |
| **⌨️ 输入法** | **assists-ime** | 自定义**输入法**：在仅能通过输入法触发搜索等场景下，为自动化提供关键能力。 |
| **📹 屏幕录制** | **assists-mp** | **屏幕录制**与截图：整屏/节点截图，弥补无障碍无法直接截图的场景。 |
| **🖼️ 图像处理** | **assists-opcv** | **图像处理**：集成 OpenCV，模板匹配等能力增强屏幕识别。 |
| **🌐 Web 桥接** | **assists-web** | **Web 自动化支持**：WebView 原生↔JS 通道，让 Web 端直接调用 Assists 能力。 |
| **📱 运行平台** | [**AssistsX**][assistsx-link] | **Web 自动化运行平台**及**节点分析工具**：安装即用，支持插件本地/局域网/在线加载，开启节点分析后可在局域网用浏览器分析页面节点。 |
| **📜 JS 库** | [**assistsx-js**][assistsx-js-link] | **Web 自动化 JS 库**：节点查找、手势、步骤器……用前端技术栈轻松编写与维护自动化。 |
| **🤖 AI 协作** | [**Assists MCP**][assists-mcp-link] | **MCP 商业产品**：凡基于无障碍服务的自动化均可协作；与 AssistsX 搭配可发挥全部潜能（插件创建、逻辑编写、节点分析、Bug 排查）。 |
| **📂 示例仓库** | [**assists-examples**][assists-examples-link] | **Assists 相关示例项目**：包含**原生自动化**与 **Web 端自动化**示例，即拿即跑。 |
| **📊 日志节点分析** | **日志节点上报分析系统** | 测试人员可提交**运行日志**与**页面节点信息**，开发基于上报数据分析页面结构、精准定位多设备/多界面 Bug，解决「无日志、无节点难以复现」的痛点。 |

**推荐路径**：使用 [assistsx-js][assistsx-js-link] 在 Web 端开发自动化 → 在手机安装 [AssistsX][assistsx-link] 运行插件 → 接入 [Assists MCP][assists-mcp-link] 让 AI Agent 协作开发与排障 → 参考 [assists-examples][assists-examples-link] 学习与扩展。

## 🤖 Assists MCP · AI Agent 协作

> **新增 MCP 支持，全面增强 AI Agent 与无障碍自动化的协作能力。**  
> 通过 MCP，AI Agent 从辅助工具提升为自动化开发流程中的核心协作者。

接入 **Cursor、Claude Code、Codex、OpenCode** 等支持 MCP 的 AI 编程助手后，Agent 可全程参与开发与调试：不只是「遥控手机」，而是覆盖**基于无障碍服务的自动化**——读节点、写逻辑、查 Bug，让 AI Agent 真正进入自动化落地全流程。

与 [AssistsX][assistsx-link] 协作时，可进一步发挥 MCP 的全部潜能：覆盖插件创建、实现与排障，显著提升效率。

| 能力 | 说明 |
|------|------|
| **多端 MCP 接入** | 支持 Cursor、Claude Code、Codex、OpenCode 等 MCP 客户端 |
| **无障碍自动化协作** | 凡基于 Android 无障碍服务的自动化，均可读节点、写逻辑、查 Bug |
| **AssistsX 全潜能** | 与 AssistsX 搭配时，支持插件创建、逻辑编写、调试与异常排查 |
| **节点读取与分析** | 原生无障碍服务节点读取与逻辑分析 |
| **异常与 Bug 排查** | 运行异常分析与问题定位 |

**详细介绍及配置教程** → [https://assists.cn/mcp][assists-mcp-link]

## 📋 核心能力

| 能力 | 说明 |
|------|------|
| **无障碍 API** | 节点查找、点击、输入、滚动等，接口简洁易用 |
| **浮窗管理** | 浮窗的添加/删除/显示/隐藏/缩放/移动，快速实现悬浮控制 |
| **步骤器** | 可复用、易维护的自动化步骤框架与编排 |
| **屏幕与截图** | 整屏截图、指定节点区域截图（assists-mp） |
| **图像识别** | 结合 OpenCV 的模板匹配等，辅助屏幕内容识别（assists-opcv） |
| **Web 自动化** | WebView 桥接，HTML + JS/Vue 开发脚本，对应 [assistsx-js][assistsx-js-link] |

## 🎬 功能示例

Demo 覆盖**基础操作**、**高级自动化**（收能量、发朋友圈、接听电话等）、**截图与浮窗**等。

[📱 直接下载 Demo][demo-download]



## 开发文档

### 依赖集成（Maven Central）

当前推荐版本：**3.5.4**

```gradle
repositories {
    mavenCentral()
}

dependencies {
    // 核心库（必选）
    implementation "io.github.ven-coder:assists-base:3.5.4"
    // 按需引入，版本号与上相同
    // implementation "io.github.ven-coder:assists-web:3.5.4"
    // implementation "io.github.ven-coder:assists-mp:3.5.4"
    // implementation "io.github.ven-coder:assists-log:3.5.4"
    // implementation "io.github.ven-coder:assists-ime:3.5.4"
    // implementation "io.github.ven-coder:assists-opcv:3.5.4"
}
```

| 模块 | artifactId |
|------|------------|
| assists | `assists-base` |
| assists-web | `assists-web` |
| assists-mp | `assists-mp` |
| assists-log | `assists-log` |
| assists-ime | `assists-ime` |
| assists-opcv | `assists-opcv` |

> **迁移说明**：3.5.3 起官方仓库为 Maven Central（`io.github.ven-coder`）。旧版 JitPack 坐标 `com.github.ven-coder.assists:*` 仍可用于历史版本，新项目请使用上表坐标。模块说明见 [开发文档][docs-link]。

各版本新增与变更见仓库根目录 **[更新日志](CHANGELOG.md)**。

请移步至[在线文档](https://ahcirffybg.feishu.cn/wiki/space/7561797853589553156?ccm_open_type=lark_wiki_spaceLink&open_tab_from=wiki_home)


## 交流反馈

有问题欢迎反馈交流

| QQ交流群 | 微信群 | 作者微信 |
|:------:|:------:|:--------:|
| <img src="https://github.com/ven-coder/assists/blob/master/images/qq-group-code.png" width="200" /> | <img src="images/wechat-group.jpg" width="200" /> | <img src="https://github.com/user-attachments/assets/49378ec3-71a2-4a5e-8510-bec4ec8d915e" width="200" /> |

微信群二维码过期请加作者邀请加入


## 💝 支持开源

开源不易，您的支持是我坚持的动力！

如果Assists框架对您的项目有帮助，可以通过以下方式支持我喔：

### ⭐ Star支持

- 给项目点个Star，让更多开发者发现这个框架
- 分享给身边的朋友和同事

### 💰 赞助支持

- [爱发电支持][afdian-link] - 您的每一份支持都是我们前进的动力
- 一杯Coffee的微信赞赏

<img src="https://github.com/user-attachments/assets/3862a40c-631c-4ab0-b1e7-00ec3e3e00ad" width="150" />

**感谢所有的支持者，得到你们的支持我将会更加完善开源库的能力！** 🚀

## Star History

[![Star History Chart][star-history-chart]][star-history-link]

<div align="right">

[![][back-to-top]](#readme-top)

</div>

---

## License

[GNU General Public License v3.0][license-link]

Copyright © 2025 [ven-coder][profile-link]

<!-- LINK GROUP -->

[back-to-top]: https://img.shields.io/badge/-返回顶部-151515?style=flat-square
[maven-central-shield]: https://img.shields.io/maven-central/v/io.github.ven-coder/assists-base?label=maven%20central&color=blue&labelColor=black&style=flat-square
[maven-central-link]: https://central.sonatype.com/artifact/io.github.ven-coder/assists-base
[license-shield]: https://img.shields.io/badge/license-GPL--3.0-blue?labelColor=black&style=flat-square
[license-link]: https://github.com/ven-coder/assists/blob/master/LICENSE
[stars-shield]: https://img.shields.io/github/stars/ven-coder/assists?color=ffcb47&labelColor=black&style=flat-square
[stars-link]: https://github.com/ven-coder/assists/stargazers
[forks-shield]: https://img.shields.io/github/forks/ven-coder/assists?color=8ae8ff&labelColor=black&style=flat-square
[forks-link]: https://github.com/ven-coder/assists/network/members
[issues-shield]: https://img.shields.io/github/issues/ven-coder/assists?color=ff80eb&labelColor=black&style=flat-square
[issues-link]: https://github.com/ven-coder/assists/issues
[profile-link]: https://github.com/ven-coder
[demo-download]: https://www.pgyer.com/1zaijG
[docs-link]: https://ahcirffybg.feishu.cn/wiki/space/7561797853589553156?ccm_open_type=lark_wiki_spaceLink&open_tab_from=wiki_home
[assistsx-js-link]: https://github.com/ven-coder/assistsx-js
[assistsx-link]: https://github.com/ven-coder/assistsx
[assists-mcp-link]: https://assists.cn/mcp
[assists-examples-link]: https://github.com/ven-coder/assists-examples
[api-reference]: https://github.com/ven-coder/assists/blob/master/API_REFERENCE.md
[changelog]: CHANGELOG.md
[afdian-link]: https://afdian.com/a/vencoder
[star-history-link]: https://www.star-history.com/#ven-coder/assists&Date
[star-history-chart]: https://api.star-history.com/svg?repos=ven-coder/assists&type=Date
[tutorial-appium]: https://juejin.cn/post/7483409317564907530
[tutorial-weditor]: https://juejin.cn/post/7484188555735613492
[tutorial-uiautomator]: https://blog.csdn.net/weixin_37496178/article/details/138328871?fromshare=blogdetail&sharetype=blogdetail&sharerId=138328871&sharerefer=PC&sharesource=weixin_37496178&sharefrom=from_link

<!-- IMAGE GROUP -->

[image-banner]: https://github.com/user-attachments/assets/59357dc6-dc2a-4a9e-9194-babfa0838fc9
[image-basic-demo]: https://github.com/user-attachments/assets/b537bab4-cc55-41c2-8f81-9e8b965e939a
[image-advanced-demo]: https://github.com/user-attachments/assets/9b50628c-603e-47d1-a6ae-5600358575fc
[image-expert-demo]: https://github.com/user-attachments/assets/262b9028-5926-478b-93bd-3e20110db391
[image-screenshot-demo]: https://github.com/user-attachments/assets/522236e4-5880-4f00-9f4c-76728df3cfe6
[image-auto-collect]: https://github.com/ven-coder/Assists/assets/27257149/8d1d09b2-e4b3-44dc-b5df-68fcdcac7a62
[image-auto-post]: https://github.com/ven-coder/Assists/assets/27257149/4713656b-a8ff-4c99-9814-a0b883ebbe64
[image-auto-scroll]: https://github.com/ven-coder/Assists/assets/27257149/056ef46b-8076-4f90-ab5a-263ff308f8e8
[image-accessibility-guide]: https://github.com/user-attachments/assets/9e20a757-8d8f-47e6-999b-8532b4e6827a
[image-prevent-notification]: https://github.com/user-attachments/assets/76613db4-c0a9-4ad8-abde-ec0ef8f7ed09
[image-notification-listener]: https://github.com/user-attachments/assets/cc6a861a-3512-43c0-9c1d-4e61229dc527
[image-auto-answer]: https://github.com/user-attachments/assets/25472235-8d6d-4327-9bc5-db47253b7f0e
[image-window-control]: https://github.com/user-attachments/assets/184fb248-66e0-4bb4-aaae-c1b8c4cef70a
[image-qrcode]: https://github.com/ven-coder/Assists/assets/27257149/c4ce8c21-ac8b-4d3f-bfe4-257a525fb3c5
[image-logcat]: https://github.com/user-attachments/assets/81725dc3-d924-44f4-89fe-75938ae659e9
[image-wechat-group]: https://github.com/user-attachments/assets/a31109fe-3106-4922-8abd-5cdc9c6a22e4
[image-wechat-personal]: https://github.com/user-attachments/assets/49378ec3-71a2-4a5e-8510-bec4ec8d915e
[image-wechat-reward]: https://github.com/user-attachments/assets/3862a40c-631c-4ab0-b1e7-00ec3e3e00ad
