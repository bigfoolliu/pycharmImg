# 仓库贡献指南

## 项目结构

本仓库开发面向 Apple Silicon、macOS 15.6、PyCharm Professional 2024.1.7 的图片链接预览插件。Java 17 源码位于 `src/main/java/`，JUnit 4 测试位于 `src/test/java/`，插件清单位于 `src/main/resources/META-INF/plugin.xml`，设计与命令说明位于 `docs/`。不要提交构建产物和本机 IDE 配置。

## 构建、测试与本地运行

目标 IDE 固定为构建号 `PY-241.19416.19`，Java 字节码目标为 17。将 `JAVA_HOME` 指向 JDK 21，使用仓库的 Gradle 9.8.1 Wrapper：`./gradlew test` 运行测试，`./gradlew buildPlugin` 打包插件，`./gradlew runIde` 启动沙盒。可设置 `PYCHARM_HOME` 使用本机 PyCharm；详细命令和产物路径见 `docs/构建与测试命令.md`。

## 代码风格与命名

Java 使用 4 个空格缩进，不混用制表符；类名使用大驼峰式，方法和变量使用小驼峰式。保持编辑器监听、链接识别、图片加载和弹窗职责清晰。修改 `plugin.xml` 时，同步检查类名、扩展点声明和插件兼容范围。

## 测试要求

为新功能和缺陷修复补充针对性测试，测试类以 `Test.java` 结尾，例如 `ImageLoaderTest.java`。重点覆盖链接识别、图片校验、请求取消与超时。涉及编辑器交互的改动，应在目标 PyCharm 中手动验证，并在拉取请求中记录结果。目前没有覆盖率门槛。

## 提交与拉取请求

现有提交标题很短，尚未形成固定格式。后续请使用简短、明确的标题，例如“修复 CDN 图片响应类型误判”。拉取请求应说明改动、测试结果及关联问题；界面变化附截图，兼容性变化写明验证过的 PyCharm 版本。完整步骤见 `CONTRIBUTING.md`。本项目采用 MIT 许可证。
