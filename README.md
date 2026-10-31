# 图片链接预览插件

面向 Apple Silicon、macOS 15.6、PyCharm Professional 2024.1.7（`PY-241.19416.19`）的轻量插件。在文本编辑器中将鼠标停留在 HTTP(S) 图片链接上约 400 毫秒，即可在链接附近查看缩放预览。首版支持 PNG、JPEG 和 GIF 静态首帧；图片仅在悬停时按需请求。

## 环境与命令

需要 Gradle 9.8.1 和 Homebrew 安装的 JDK 21（`/opt/homebrew/opt/openjdk@21`）。项目使用本机 `/Applications/PyCharm.app` 作为开发 SDK；Gradle 用 JDK 21 的编译器和 `--release 17` 生成可在目标 PyCharm 运行的 Java 17 字节码。运行前设置：

```bash
export JAVA_HOME=/Applications/PyCharm.app/Contents/jbr/Contents/Home
gradle test
gradle runIde
gradle buildPlugin
```

`runIde` 启动独立的 PyCharm 沙盒；`buildPlugin` 在 `build/distributions/` 生成插件 ZIP。首次运行需要下载 Gradle 插件和测试依赖。若需在另一台机器开发，请确认目标 PyCharm 和 JDK 21 的安装路径，随后调整 `build.gradle.kts` 中的 `local(...)` 及 `gradle.properties` 中的 JDK 路径。

若沙盒实例要求单独激活，可直接在已激活的 PyCharm 2024.1.7 中测试：打开“设置”→“插件”→齿轮菜单→“从磁盘安装插件”，选择 `build/distributions/pycharm-image-link-preview-0.1.1.zip`，按提示重启。之后在文本文件中悬停一个可访问的 PNG、JPEG 或 GIF 链接。测试结束可在“已安装”页禁用或卸载该插件。每次修改源码后须重新运行 `gradle buildPlugin` 并安装新 ZIP。

## 首版边界

插件仅检查鼠标所在行的单个链接，不扫描整个项目。普通网页、损坏图片或超限响应不会显示预览。网络与解码在后台进行，编辑文件、切换标签或离开链接会取消过期预览。不支持本地路径、登录态图片、SVG 或 WebP。

功能和验收要求见[技术规范与总体架构](docs/图片链接预览插件技术规范与总体架构.md)。
