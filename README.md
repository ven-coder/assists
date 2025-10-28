<div align="center"><a name="readme-top"></a>

<img src="https://github.com/user-attachments/assets/62e6ced0-0e21-420d-b0da-ceb73b850c94" width="800" />

基于Android无障碍服务（AccessibilityService）封装的自动化开发框架

简化自动化脚本开发 · 提供各种增强能力 · 提高脚本易维护性 · 支持Web平台自动化脚本开发

[![][jitpack-shield]][jitpack-link]
[![][license-shield]][license-link]
[![][stars-shield]][stars-link]
[![][forks-shield]][forks-link]
[![][issues-shield]][issues-link]

[📱 下载Demo][demo-download] · [📘 开发文档][docs-link] · [🐛 反馈问题][issues-link] · [💬 交流反馈](#交流反馈) · [💰 赞助支持](#-赞助支持) · [💁 付费社群](#-付费社群) · [⭐ Star支持][stars-link]

</div>

---

## 📖 目录

<details>
<summary><kbd>展开目录</kbd></summary>

- [📖 目录](#-目录)
- [利用无障碍服务能做什么](#利用无障碍服务能做什么)
- [Assists作用](#assists作用)
- [主要能力](#主要能力)
- [功能示例](#功能示例)
  - [基础功能](#基础功能)
  - [高级示例](#高级示例)
  - [更多示例](#更多示例)
- [🎉 新增JS支持库](#-新增js支持库)
- [🚀 快速开始](#-快速开始)
  - [1. 导入依赖](#1-导入依赖)
    - [1.1 项目根目录build.gradle添加](#11-项目根目录buildgradle添加)
    - [1.2 主模块build.gradle添加](#12-主模块buildgradle添加)
  - [2. 注册\&开启服务](#2-注册开启服务)
    - [2.1 主模块AndroidManifest.xml中注册服务](#21-主模块androidmanifestxml中注册服务)
    - [2.2 开启服务](#22-开启服务)
- [步骤器-快速实现复杂自动化脚本](#步骤器-快速实现复杂自动化脚本)
  - [1. 继承 `StepImpl`](#1-继承-stepimpl)
  - [2. 开始执行](#2-开始执行)
  - [3. 停止执行](#3-停止执行)
- [API列表](#api列表)
  - [初始化和服务管理](#初始化和服务管理)
  - [元素查找](#元素查找)
  - [元素信息获取](#元素信息获取)
  - [元素层级操作](#元素层级操作)
  - [元素操作](#元素操作)
  - [更多API](#更多api)
- [示例教程](#示例教程)
- [其他教程博客](#其他教程博客)
  - [获取节点信息](#获取节点信息)
  - [版本历史](#版本历史)
- [交流反馈](#交流反馈)
- [💝 支持开源](#-支持开源)
  - [⭐ Star支持](#-star支持)
  - [💰 赞助支持](#-赞助支持)
  - [💁 付费社群](#-付费社群)
- [Star History](#star-history)
- [License](#license)

</details>

## 利用无障碍服务能做什么

可以开发各种各样的自动化脚本程序以及协助脚本，比如：

1. 微信自动抢红包
2. 微信自动接听电话
3. 支付宝蚂蚁森林自动浇水
4. 支付宝芭芭农场自动施肥、自动收集能量...
5. 各种平台的拓客、引流、营销系统
6. 远程控制

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## Assists作用

基于Android无障碍服务（AccessibilityService）封装的框架

1. 简化自动化脚本开发
2. 为自动化脚本提供各种增强能力
3. 提高脚本易维护性
4. 支持html+js/vue开发自动化脚本

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## 主要能力

1. 易于使用的无障碍服务API
2. 浮窗管理器：易于实现及管理浮窗
3. 步骤器：为快速实现、可复用、易维护的自动化步骤提供框架及管理
4. 配套屏幕管理：快速生成输出屏幕截图、元素截图
5. 屏幕管理结合opencv：便于屏幕内容识别为自动化提供服务
6. 封装webview接口支持html+js/vue开发自动化脚本

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## 功能示例

### 基础功能

| 基础示例 | 进阶示例 | 高级示例 | 图片截取 |
| :------: | :------: | :------: | :------: |
| <img src="https://github.com/user-attachments/assets/b537bab4-cc55-41c2-8f81-9e8b965e939a" width="200" /> | <img src="https://github.com/user-attachments/assets/9b50628c-603e-47d1-a6ae-5600358575fc" width="200" /> | <img src="https://github.com/user-attachments/assets/262b9028-5926-478b-93bd-3e20110db391" width="200" /> | <img src="https://github.com/user-attachments/assets/522236e4-5880-4f00-9f4c-76728df3cfe6" width="200" /> |

### 高级示例

| 自动收能量 | 自动发朋友圈 | 自动滑动朋友圈 | 无障碍服务开启引导 |
| :--------: | :----------: | :------------: | :----------------: |
| <img src="https://github.com/ven-coder/Assists/assets/27257149/8d1d09b2-e4b3-44dc-b5df-68fcdcac7a62" width="200" /> | <img src="https://github.com/ven-coder/Assists/assets/27257149/4713656b-a8ff-4c99-9814-a0b883ebbe64" width="200" /> | <img src="https://github.com/ven-coder/Assists/assets/27257149/056ef46b-8076-4f90-ab5a-263ff308f8e8" width="200" /> | <img src="https://github.com/user-attachments/assets/9e20a757-8d8f-47e6-999b-8532b4e6827a" width="200" /> |

| 防止下拉通知栏 | 通知/Toast监听 | 自动接听微信电话 | 窗口缩放&拖动 |
| :------------: | :------------: | :--------------: | :-----------: |
| <img src="https://github.com/user-attachments/assets/76613db4-c0a9-4ad8-abde-ec0ef8f7ed09" width="200" /> | <img src="https://github.com/user-attachments/assets/cc6a861a-3512-43c0-9c1d-4e61229dc527" width="200" /> | <img src="https://github.com/user-attachments/assets/25472235-8d6d-4327-9bc5-db47253b7f0e" width="200" /> | <img src="https://github.com/user-attachments/assets/184fb248-66e0-4bb4-aaae-c1b8c4cef70a" width="200" /> |

### 更多示例

更多示例可以直接下载demo查看

<img src="https://github.com/user-attachments/assets/39568ee6-b9f3-447f-8a81-4ef25692815b" width="150" />

[📱 直接下载][demo-download]

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## 🎉 新增JS支持库

新增支持通过Web端实现Android平台自动化脚本的JS库：**[assistsx-js][assistsx-js-link]**

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## 🚀 快速开始

### 1. 导入依赖

#### 1.1 项目根目录build.gradle添加

```gradle
allprojects {
    repositories {
        //添加jitpack仓库
        maven { url 'https://jitpack.io' }
    }
}
```

#### 1.2 主模块build.gradle添加

最新版本：[![][jitpack-shield]][jitpack-link]

```gradle
dependencies {
    //按需添加
    //基础库（必须）
    implementation "com.github.ven-coder.Assists:assists-base:最新版本"
    //屏幕录制相关（可选）
    implementation "com.github.ven-coder.Assists:assists-mp:最新版本"
    //opencv相关（可选）
    implementation "com.github.ven-coder.Assists:assists-opcv:最新版本"
    //web端支持（可选）
    implementation "com.github.ven-coder.Assists:assists-web:最新版本"
}
```

<div align="right">

[![][back-to-top]](#readme-top)

</div>

### 2. 注册&开启服务

#### 2.1 主模块AndroidManifest.xml中注册服务

一定要在主模块中注册服务，不然进程被杀服务也会自动被关闭需要再次开启（小米可保持杀进程保持开启，其他vivo、oppo、鸿蒙机型似乎不行）

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.ven.assists.simple">

    <application
        android:name="com.ven.assists.simple.App"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:requestLegacyExternalStorage="true"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/AppTheme"
        android:usesCleartextTraffic="true">
        <!-- 添加代码 ↓-->
        <service
            android:name="com.ven.assists.service.AssistsService"
            android:enabled="true"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <!--android:priority="10000" 可提高服务在设置中的权重，排在前面-->
            <intent-filter android:priority="10000">
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/assists_service" />
        </service>
      
        <!-- 或者使用下面的服务可以解决一些应用混淆节点的问题，比如微信8.0.51以上版本获取的节点元素错乱问题 -->
        <!-- ⚠️ 选其一 -->
        <service
            android:name="com.google.android.accessibility.selecttospeak.SelectToSpeakService"
            android:enabled="true"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <!--android:priority="10000" 可提高服务在设置中的权重，排在前面     -->
            <intent-filter android:priority="10000">
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/assists_service" />
        </service>
        <!-- 添加代码 ↑-->
    </application>

</manifest>
```

#### 2.2 开启服务

调用 `AssistsCore.openAccessibilitySetting()` 跳转到无障碍服务设置页面，找到对应的应用开启服务。

服务开启后执行以下API测试是否成功集成：

```kotlin
AssistsCore.getAllNodes().forEach { it.logNode() }
```

这段代码是获取当前页面所有节点元素的基本信息在Logcat（tag：assists_log）打印出来，如下图：

<img src="https://github.com/user-attachments/assets/81725dc3-d924-44f4-89fe-75938ae659e9" width="350" />

至此，已成功集成Assists。如果没有任何输出请检查集成步骤是否正确。

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## 步骤器-快速实现复杂自动化脚本

步骤器可以帮助快速实现复杂的业务场景，比如自动发朋友圈、获取微信所有好友昵称、自动删除好友...等等都是一些逻辑较多的业务场景，步骤器可帮助快速实现。

### 1. 继承 `StepImpl`

直接在接口 `onImpl(collector: StepCollector)` 写步骤逻辑，每个步骤自定义步骤的序号，用于区分执行的步骤。如果重复则会以最后一个步骤为准

```kotlin
class MyStepImpl:StepImpl() {
    override fun onImpl(collector: StepCollector) {
        //定义步骤序号为1的逻辑
        collector.next(1) {// 1为步骤的序号
            //步骤1逻辑
            ...
            //返回下一步需要执行的序号，通过Step.get([序号])，如果需要重复该步骤可返回Step.repeat，如果返回Step.none则不执行任何步骤，相当于停止
            return@next Step.get(2, delay = 1000) //将会执行步骤2逻辑
        }.next(2) {
            //步骤2逻辑
            ...
            //返回下一步需要执行的序号，通过Step.get([序号])
            return@next Step.get(3)
        }.next(3) {
            //步骤3逻辑
            ...
            //返回下一步需要执行的序号，通过Step.get([序号])
            return@next Step.get(4)
        }
        //其他步骤
        ...
    }
}
```

### 2. 开始执行

执行前请确保无障碍服务已开启

```kotlin
//从MyStepImpl步骤1开始执行，isBegin是否作为起始步骤，默认false
StepManager.execute(MyStepImpl::class.java, 1, isBegin = true)
```

### 3. 停止执行

```kotlin
// 设置停止标志，将取消所有正在执行的步骤
StepManager.isStop = true
```

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## API列表

### 初始化和服务管理

| 方法名 | 说明 | 返回值 |
|--------|------|--------|
| `init(application: Application)` | 初始化AssistsCore | 无 |
| `openAccessibilitySetting()` | 打开系统无障碍服务设置页面 | 无 |
| `isAccessibilityServiceEnabled()` | 检查无障碍服务是否已开启 | Boolean |
| `getPackageName()` | 获取当前窗口所属的应用包名 | String |

### 元素查找

| 方法名 | 说明 | 返回值 |
|--------|------|--------|
| `findById(id, filterText?, filterDes?, filterClass?)` | 通过id查找所有符合条件的元素 | List<AccessibilityNodeInfo> |
| `AccessibilityNodeInfo.findById(id, filterText?, filterDes?, filterClass?)` | 在指定元素范围内通过id查找元素 | List<AccessibilityNodeInfo> |
| `findByText(text, filterViewId?, filterDes?, filterClass?)` | 通过文本内容查找所有符合条件的元素 | List<AccessibilityNodeInfo> |
| `findByTextAllMatch(text, filterViewId?, filterDes?, filterClass?)` | 查找所有文本完全匹配的元素 | List<AccessibilityNodeInfo> |
| `AccessibilityNodeInfo.findByText(text, filterViewId?, filterDes?, filterClass?)` | 在指定元素范围内通过文本查找元素 | List<AccessibilityNodeInfo> |
| `findByTags(className, viewId?, text?, des?)` | 根据多个条件查找元素 | List<AccessibilityNodeInfo> |
| `AccessibilityNodeInfo.findByTags(className, viewId?, text?, des?)` | 在指定元素范围内根据多个条件查找元素 | List<AccessibilityNodeInfo> |
| `getAllNodes(filterViewId?, filterDes?, filterClass?, filterText?)` | 获取当前窗口中的所有元素 | List<AccessibilityNodeInfo> |

### 元素信息获取

| 方法名 | 说明 | 返回值 |
|--------|------|--------|
| `AccessibilityNodeInfo.txt()` | 获取元素的文本内容 | String |
| `AccessibilityNodeInfo.des()` | 获取元素的描述内容 | String |
| `AccessibilityNodeInfo.getAllText()` | 获取元素的所有文本内容（包括text和contentDescription） | ArrayList<String> |
| `AccessibilityNodeInfo.containsText(text)` | 判断元素是否包含指定文本 | Boolean |
| `AccessibilityNodeInfo.getBoundsInScreen()` | 获取元素在屏幕中的位置信息 | Rect |
| `AccessibilityNodeInfo.getBoundsInParent()` | 获取元素在父容器中的位置信息 | Rect |
| `AccessibilityNodeInfo.isVisible(compareNode?, isFullyByCompareNode?)` | 判断元素是否可见 | Boolean |

### 元素层级操作

| 方法名 | 说明 | 返回值 |
|--------|------|--------|
| `AccessibilityNodeInfo.getNodes()` | 获取指定元素下的所有子元素 | ArrayList<AccessibilityNodeInfo> |
| `AccessibilityNodeInfo.getChildren()` | 获取元素的直接子元素 | ArrayList<AccessibilityNodeInfo> |
| `AccessibilityNodeInfo.findFirstParentByTags(className)` | 查找第一个符合指定类型的父元素 | AccessibilityNodeInfo? |
| `AccessibilityNodeInfo.findFirstParentClickable()` | 查找元素的第一个可点击的父元素 | AccessibilityNodeInfo? |

### 元素操作

| 方法名 | 说明 | 返回值 |
|--------|------|--------|
| `AccessibilityNodeInfo.click()` | 点击元素 | Boolean |
| `AccessibilityNodeInfo.longClick()` | 长按元素 | Boolean |
| `AccessibilityNodeInfo.paste(text)` | 向元素粘贴文本 | Boolean |
| `AccessibilityNodeInfo.setNodeText(text)` | 设置元素的文本内容 | Boolean |
| `AccessibilityNodeInfo.selectionText(selectionStart, selectionEnd)` | 选择元素中的文本 | Boolean |
| `AccessibilityNodeInfo.scrollForward()` | 向前滚动可滚动元素 | Boolean |
| `AccessibilityNodeInfo.scrollBackward()` | 向后滚动可滚动元素 | Boolean |

### [更多API][api-reference]

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## 示例教程

- [Appium结合AccessibilityService实现自动化微信登录][tutorial-appium]

## 其他教程博客

### 获取节点信息

- [使用weditor获取节点信息][tutorial-weditor]
- [使用Appium获取节点信息][tutorial-appium]
- [使用uiautomatorviewer获取节点信息][tutorial-uiautomator]

### [版本历史][changelog]

<div align="right">

[![][back-to-top]](#readme-top)

</div>

## 交流反馈

有问题欢迎反馈交流（微信群二维码失效可以加我拉进群）

| 交流群 | 个人微信 |
|:------:|:--------:|
| <img src="https://github.com/user-attachments/assets/7375a985-12e1-49c8-a11e-09f905b69ed3" width="200" /> | <img src="https://github.com/user-attachments/assets/49378ec3-71a2-4a5e-8510-bec4ec8d915e" width="200" /> |

1群已满200人，要进1群可加我备注进1群

<div align="right">

[![][back-to-top]](#readme-top)

</div>

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

付费社群提供的服务：

1. 完整易于阅读的开发文档
2. Assists开发指导
3. 开发疑难解答
4. 群友互助资源对接
5. 基于Assists开发的抖音养号，小红书养号，支付宝能量收集，支付宝农场，无线远程控制等源码（补充中...）

| 资料截图 | 微信扫码加入 |
|:------:|:------:|
| <img src="https://github.com/user-attachments/assets/d6cc7bcf-0b0c-4b8e-b56d-3daa1f333139" width="600" /> | <img src="https://github.com/ven-coder/Assists/assets/27257149/7ae8e825-f489-46e3-96f0-ed03d12db9e8" width="200" /> |

**定制开发可联系个人微信: x39598**

**感谢所有的支持者，得到你们的支持我将会更加完善开源库的能力！** 🚀

<div align="right">

[![][back-to-top]](#readme-top)

</div>

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
[docs-link]: https://github.com/ven-coder/Assists/tree/master/documents
[assistsx-js-link]: https://github.com/ven-coder/assistsx-js
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
