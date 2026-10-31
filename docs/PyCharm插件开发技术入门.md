# PyCharm 插件开发技术入门（macOS、2024 Professional）

## 目标与版本

本项目的目标运行环境是 **Apple Silicon 芯片、macOS 15.6、PyCharm Professional 2024.1.7**，本机安装构建号为 **`PY-241.19416.19`**。插件的目标功能是：在已打开文件中识别图片链接，并在 PyCharm 内预览图片。插件是运行在 IntelliJ Platform 上的 JVM 程序，通常用 Kotlin 或 Java 编写；只有在需要调用外部 Python 工具时，才需要另行管理 Python 环境。[PyCharm 插件开发说明](https://plugins.jetbrains.com/docs/intellij/pycharm.html) · [JetBrains 旧版本下载页](https://www.jetbrains.com/pycharm/download/other/)

| 目标版本 | 平台分支 | 对应 Java 版本 | 构建工具要点 |
| --- | --- | --- | --- |
| 2024.1 | `241` | 17 | 可使用 IntelliJ Platform Gradle Plugin 2.x |
| 2024.2 | `242` | 21 | 必须使用 IntelliJ Platform Gradle Plugin 2.x |
| 2024.3 | `243` | 21 | 必须使用 IntelliJ Platform Gradle Plugin 2.x |

本项目已选定上表的 **2024.1 / Java 17**，不以 2024.2 或 2024.3 为首版兼容目标。Gradle 自身的运行要求应按所选 Gradle 插件版本另行核对；若以后扩大兼容范围，应逐个目标版本验证。[版本与构建号对照](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) · [Gradle 插件要求](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)

## 需要掌握的技术

1. **Kotlin 或 Java 与 Gradle**：用于编写插件代码、管理依赖、运行开发版 IDE、测试和打包。建议先选一种实现语言，并使用 Gradle Wrapper 固定构建工具版本。
2. **IntelliJ Platform SDK**：理解项目、编辑器、虚拟文件系统（VFS）、程序结构接口（PSI）、动作（Action）和扩展点（Extension Point）。需要分析 Python 代码时使用 PyCharm 提供的 Python PSI，而不是自行解析文本。[SDK 入门](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html) · [扩展点说明](https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html)
3. **插件清单 `plugin.xml`**：声明插件标识、名称、依赖、动作和扩展点。使用 PyCharm 的 Python API 时，需声明 `<depends>com.intellij.modules.python</depends>`；Gradle 依赖也要包含目标版本对应的 Python 插件。对于 2024.2 及之后版本，若使用 `Pythonid` 功能，需同时依赖 `PythonCore` 和 `Pythonid`。[PyCharm 依赖说明](https://plugins.jetbrains.com/docs/intellij/pycharm.html)
4. **线程与界面**：Swing 事件派发线程（EDT）负责界面响应，耗时扫描和网络请求应在后台执行；读取 PSI/VFS 与修改文件要遵守平台的读写动作规则。目标为 2024.1 及之后版本时，可优先学习 Kotlin 协程相关 API。[线程模型](https://plugins.jetbrains.com/docs/intellij/threading-model.html)

## 图片链接预览的实现要点

首版以**当前打开的编辑器**为范围：在鼠标悬停位置识别 `http://`、`https://` 链接，确认返回图片后在编辑器附近显示预览。对于 Python 字符串、Markdown 链接及普通文本，先用可复用的 URL 识别逻辑处理，不扫描整个项目。具体流程见[图片链接预览插件总体技术方案](图片链接预览插件总体技术方案.md)。[编辑器事件扩展点](https://plugins.jetbrains.com/docs/intellij/extension-point-list.html) · [弹窗说明](https://plugins.jetbrains.com/docs/intellij/popups.html)

预览应在用户触发后异步加载，避免打开文件或输入时批量请求网络资源。不能只依据 `.png` 等后缀判断是否为图片：链接可能没有后缀，也可能返回非图片内容；应校验响应类型及实际可解码性。设置连接超时、下载大小和图像尺寸上限，处理重定向、失效链接与离线状态，并缓存已成功加载的缩略图。界面更新回到 EDT，网络和解码放在后台；不要把远端内容当作可信代码执行。[线程模型](https://plugins.jetbrains.com/docs/intellij/threading-model.html)

验收时至少覆盖：正常图片、无后缀图片链接、非图片响应、无效链接、超大图片、同一文件中的多个链接，以及编辑文件或切换标签页后预览位置是否正确。总体方案暂定**悬停预览**；本地路径、相对路径和需要登录的图片地址不属于首版范围。

## 建议的项目结构与开发流程

```text
build.gradle.kts                 # Gradle 与目标 PyCharm 配置
gradle/wrapper/                  # 固定 Gradle 版本
src/main/kotlin/                 # 插件实现（若选择 Java，则用 src/main/java/）
src/main/resources/META-INF/plugin.xml
src/test/kotlin/                 # 自动化测试
```

先实现一个最小动作或工具窗口，再逐步接入编辑器、Python 代码模型等功能。配置完成后，使用 `./gradlew runIde` 启动隔离的 PyCharm 开发实例，使用 `./gradlew test` 运行测试，使用 `./gradlew buildPlugin` 生成插件包。发布或交付前，使用插件兼容性验证工具检查声明的目标版本范围，并在实际的 macOS PyCharm Professional 2024 版本上安装验证。[Gradle 任务说明](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html) · [兼容性验证](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)

## macOS 专项检查

- 目标机器已确定为 Apple Silicon，系统为 macOS 15.6；如插件调用本地可执行文件或动态库，必须提供 `arm64` 版本。纯 JVM 实现的首版图片预览通常无需本地库。
- 检查菜单位置、`⌘` 快捷键、文件选择、系统权限与深色模式下的表现。避免把个人机器的绝对路径写进代码。
- 仅面向 macOS 使用时，仍应在真实 macOS 环境完成沙盒测试；若无需调用系统专属 API，插件主体可以保持跨平台实现。

目前仓库尚未建立 Gradle 工程，上述命令是**完成工程初始化后的目标流程**，不是现成可运行的命令。工程骨架应按 PyCharm Professional 2024.1.7 创建，并以 `PY-241.19416.19` 为首版验证目标。
