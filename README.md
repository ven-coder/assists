<div align="center"><a name="readme-top"></a>

<img src="https://github.com/user-attachments/assets/62e6ced0-0e21-420d-b0da-ceb73b850c94" width="800" />

基于Android无障碍服务（AccessibilityService）封装的自动化开发框架

简化自动化脚本开发 · 提供各种增强能力 · 提高脚本易维护性 · 支持Web平台自动化脚本开发

[![][jitpack-shield]][jitpack-link]
[![][license-shield]][license-link]
[![][stars-shield]][stars-link]
[![][forks-shield]][forks-link]
[![][issues-shield]][issues-link]

[📱 下载Demo][demo-download] · [🏗️ 架构设计](docs/architecture.md) · [📘 开发文档][docs-link] · [🐛 反馈问题][issues-link] · [💬 交流反馈](#交流反馈) · [💰 赞助支持](#-赞助支持) · [💁 付费社群](#-付费社群) · [⭐ Star支持][stars-link]

</div>

---

## 📖 目录

<details>
<summary><kbd>展开目录</kbd></summary>

- [📖 目录](#-目录)
- [🎯 适用场景](#-适用场景)
- [💡 为什么选 Assists](#-为什么选-assists)
- [🌟 基于 Assists 的开发生态](#-基于-assists-的开发生态)
- [📋 核心能力](#-核心能力)
- [🎬 功能示例](#-功能示例)
- [开发文档](#开发文档)
- [交流反馈](#交流反馈)
- [💝 支持开源](#-支持开源)
  - [⭐ Star支持](#-star支持)
  - [💰 赞助支持](#-赞助支持)
  - [💁 付费社群](#-付费社群)
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
| **📂 示例仓库** | [**assists-examples**][assists-examples-link] | **Assists 相关示例项目**：包含**原生自动化**与 **Web 端自动化**示例，即拿即跑。 |
| **📊 日志节点分析** | **日志节点上报分析系统**（付费） | 测试人员可提交**运行日志**与**页面节点信息**，开发基于上报数据分析页面结构、精准定位多设备/多界面 Bug，解决「无日志、无节点难以复现」的痛点。**仅限付费用户使用。** |

**推荐路径**：使用 [assistsx-js][assistsx-js-link] 在 Web 端开发自动化 → 在手机安装 [AssistsX][assistsx-link] 运行插件 → 参考 [assists-examples][assists-examples-link] 学习与扩展。

## 📋 核心能力

| 能力 | 说明 |
|------|------|
| **无障碍 API** | 节点查找、点击、输入、滚动等，接口简洁易用 |
| **浮窗管理** | 浮窗的添加/删除/显示/隐藏/缩放/移动，快速实现悬浮控制 |
| **步骤器** | 可复用、易维护的自动化步骤框架与编排 |
| **屏幕与截图** | 整屏截图、指定节点区域截图（[assists-mp](docs/architecture.md)） |
| **图像识别** | 结合 OpenCV 的模板匹配等，辅助屏幕内容识别（[assists-opcv](docs/architecture.md)） |
| **Web 自动化** | WebView 桥接，HTML + JS/Vue 开发脚本，对应 [assistsx-js][assistsx-js-link] |

## 🎬 功能示例

Demo 覆盖**基础操作**、**高级自动化**（收能量、发朋友圈、接听电话等）、**截图与浮窗**等，完整示例 GIF 与截图见 **[功能示例图集](docs/demo-gallery.md)**。

[📱 直接下载 Demo][demo-download]



## 开发文档
请移步至[在线文档](https://ahcirffybg.feishu.cn/wiki/space/7561797853589553156?ccm_open_type=lark_wiki_spaceLink&open_tab_from=wiki_home)


## 交流反馈

有问题欢迎反馈交流（微信群二维码失效可以加作者拉进群）

| 交流群 | 作者微信 |
|:------:|:--------:|
| <img src="https://github.com/user-attachments/assets/bf0d6ad2-948f-43c9-9513-c5e0848b3d8f" width="200" /> | <img src="https://github.com/user-attachments/assets/49378ec3-71a2-4a5e-8510-bec4ec8d915e" width="200" /> |

1群已满200人，要进1群可加我备注进1群


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

### 💁 付费社群

加入付费社群可享受以下权益：

- 开发指导
- VIP 交流群
- VIP 外包接单
- 详细开发文档
- 经验总结分享
- 高级示例源码
- 群友互助资源对接
- 新 API 优先提供对接
- 日志上报分析系统（对外使用开发中）

**加入方式与说明**：[在线查看](https://my.feishu.cn/wiki/CXIDwLKlYidE6TkD79gcAvI6nBg) · 或扫码查看：

<img src="https://github.com/user-attachments/assets/2ab13741-a30a-4f12-9906-f558547d0760" width="180" alt="付费社群加入方式与说明二维码" />

**定制开发可联系作者微信: x39598**

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
[jitpack-shield]: https://jitpack.io/v/ven-coder/Assists.svg
[jitpack-link]: https://jitpack.io/#ven-coder/Assists
[license-shield]: https://img.shields.io/badge/license-GPL--3.0-blue?labelColor=black&style=flat-square
[license-link]: https://github.com/ven-coder/Assists/blob/master/LICENSE
[stars-shield]: https://img.shields.io/github/stars/ven-coder/Assists?color=ffcb47&labelColor=black&style=flat-square
[stars-link]: https://github.com/ven-coder/Assists/stargazers
[forks-shield]: https://img.shields.io/github/forks/ven-coder/Assists?color=8ae8ff&labelColor=black&style=flat-square
[forks-link]: https://github.com/ven-coder/Assists/network/members
[issues-shield]: https://img.shields.io/github/issues/ven-coder/Assists?color=ff80eb&labelColor=black&style=flat-square
[issues-link]: https://github.com/ven-coder/Assists/issues
[profile-link]: https://github.com/ven-coder
[demo-download]: https://www.pgyer.com/1zaijG
[docs-link]: https://ahcirffybg.feishu.cn/wiki/space/7561797853589553156?ccm_open_type=lark_wiki_spaceLink&open_tab_from=wiki_home
[assistsx-js-link]: https://github.com/ven-coder/assistsx-js
[assistsx-link]: https://github.com/ven-coder/assistsx
[assists-examples-link]: https://github.com/ven-coder/assists-examples
[api-reference]: https://github.com/ven-coder/Assists/blob/master/API_REFERENCE.md
[changelog]: https://github.com/ven-coder/Assists/releases
[afdian-link]: https://afdian.com/a/vencoder
[star-history-link]: https://www.star-history.com/#ven-coder/Assists&Date
[star-history-chart]: https://api.star-history.com/svg?repos=ven-coder/Assists&type=Date
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
[image-paid-community]: https://github.com/user-attachments/assets/7607a4e6-4845-474e-a9c6-e685cc306523
[image-paid-qrcode]: https://github.com/ven-coder/Assists/assets/27257149/7ae8e825-f489-46e3-96f0-ed03d12db9e8
