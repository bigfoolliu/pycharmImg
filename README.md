# Image Link Preview

一个轻量的 PyCharm 图片链接预览插件。在文本编辑器中将鼠标停留在 HTTP(S) 图片链接上约 400 毫秒，即可在链接附近查看缩放预览。插件只在悬停时请求图片，不扫描项目或修改文件。

## 功能与兼容性

- 支持实际可解码的 PNG、JPEG 和 GIF；GIF 显示静态首帧。图片判断以内容为准，不依赖 URL 后缀或服务器声明的类型。
- 对下载量、图像尺寸和请求时间设限；大图在解码时先降采样，减少预览内存；离开链接、编辑文件或切换文件后丢弃过期预览。
- 每个项目最多执行一个图片请求，并仅保留最新的待处理请求；取消或超时会断开当前连接。
- 首版仅验证 **Apple Silicon、macOS 15.6、PyCharm Professional 2024.1.7**（构建号 `PY-241.19416.19`）。不支持本地路径、需要登录的图片、SVG 或 WebP。

## 构建与安装

构建需要 **JDK 21**；Gradle Wrapper 固定 Gradle 9.8.1，并用 `--release 17` 生成目标 PyCharm 所需的 Java 17 字节码。默认下载 PyCharm 2024.1.7 开发 SDK；若本机已安装该版本，可设置 `PYCHARM_HOME` 使用本地文件。

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PYCHARM_HOME=/Applications/PyCharm.app  # 可选：使用本机 PyCharm 2024.1.7
./gradlew test buildPlugin --no-daemon
```

插件包位于 `build/distributions/pycharm-image-link-preview-0.1.1.zip`。在已安装的 PyCharm 中打开“设置 → 插件 → 齿轮菜单 → 从磁盘安装插件”，选择 ZIP 并按提示重启。也可运行 `./gradlew runIde --no-daemon` 启动独立沙盒；该实例可能需要单独激活。

详细的环境说明、逐条命令注释、测试报告位置和图片诊断方法见[构建与测试命令](docs/构建与测试命令.md)。

## 文档与贡献

- [技术规范与总体架构](docs/图片链接预览插件技术规范与总体架构.md)
- [PyCharm 插件开发技术入门](docs/PyCharm插件开发技术入门.md)
- [贡献指南](CONTRIBUTING.md)

本项目采用 [MIT 许可证](LICENSE)。
