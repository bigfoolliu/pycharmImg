# Image Link Preview

Preview decodable PNG, JPEG, and GIF images from HTTP(S) links by hovering over them in the editor. After about 400 ms, the plugin sends an HTTP GET to the hovered URL and displays a scaled preview; GIFs show the first frame.

### Network and privacy

- A request is sent only after the pointer stays over a URL for about 400 ms. The request includes the URL path and query string (URL fragments are not sent in HTTP requests), so the destination server receives the request and can see your IP address.
- The plugin follows up to three HTTP(S) redirects. Localhost and private-network addresses are not blocked. Requests time out after 5 seconds and response bodies are limited to 5 MiB. Moving away cancels pending work.
- The plugin inspects only the line under the pointer. It does not scan other project files, modify files, send telemetry, or write images and URLs to disk. Decoded previews are kept in a small in-memory cache.
- Source images are limited to 4096 × 4096 pixels; previews are limited to 480 × 320 pixels. URLs with embedded usernames or passwords are rejected. The plugin does not provide a sign-in flow. It does not read PyCharm passwords or explicitly set `Cookie` or `Authorization` headers. JVM-wide cookie/authentication handlers and system proxy settings may affect HTTP connections.

在文本编辑器中将鼠标停留在 HTTP(S) 图片链接上约 400 毫秒，即可在链接附近查看缩放预览。插件只分析鼠标所在行，不扫描其他项目文件，也不修改文件。

### 网络与隐私

- 鼠标在 URL 上停留约 400 毫秒后，插件才会向该 URL 发送 HTTP GET 请求；请求包含 URL 路径和查询参数（URL 片段不会随 HTTP 请求发送），因此目标服务器会收到请求并能看到你的 IP 地址。
- 最多跟随 3 次 HTTP(S) 重定向；当前不会阻止 localhost 或内网地址。请求最长 5 秒，响应体最多 5 MiB；移开鼠标会取消待处理请求。
- 插件不发送遥测数据，不把图片或 URL 写入磁盘；解码后的预览图只保存在有限的内存缓存中。
- 原图最大 4096 × 4096 像素，预览最大 480 × 320 像素。含用户名或密码的 URL 会被拒绝；插件不提供登录流程，也不会读取 PyCharm 密码或显式添加 `Cookie`、`Authorization` 请求头。JVM 全局 Cookie/认证处理器和系统代理设置仍可能影响 HTTP 连接。

## 功能与兼容性

- 支持实际可解码的 PNG、JPEG 和 GIF；GIF 显示静态首帧。图片判断以内容为准，不依赖 URL 后缀或服务器声明的类型。
- 对下载量、图像尺寸和请求时间设限；大图在解码时先降采样，减少预览内存；离开链接、编辑文件或切换文件后丢弃过期预览。
- 每个项目最多执行一个图片请求，并仅保留最新的待处理请求；取消或超时会断开当前连接。
- 首版仅验证 **Apple Silicon、macOS 15.6、PyCharm Professional 2024.1.7**（构建号 `PY-241.19416.19`）。不支持本地路径、登录流程、SVG 或 WebP；需认证的图片可能无法加载。

## 构建与安装

macOS 下可在仓库根目录运行一键构建脚本：

```bash
./build.sh
```

脚本会查找 JDK 21，打包后用 Plugin Verifier 检查当前配置的 PyCharm SDK；不运行单元测试。当前检查目标是 PyCharm 2024.1.7。若设置了 `PYCHARM_HOME`，会使用该本机 IDE；否则 Gradle 使用项目配置的 2024.1.7 开发 SDK，首次构建可能需要下载。构建需要 **JDK 21**；Gradle Wrapper 固定 Gradle 9.8.1，并用 `--release 17` 生成目标 PyCharm 所需的 Java 17 字节码。检查报告位于 `build/reports/pluginVerifier/`。

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
