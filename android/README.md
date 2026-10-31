# Octop Android

首个 Android 客户端原型，使用 Kotlin + WebView 访问 Octop 服务。智能体与工具继续在后端运行。

当前提供：服务地址配置与保存、原网页加载、登录跳转、30 秒超时与失败重试、证书错误提示、系统返回、外部链接、系统文件选择，以及文件保存与系统打开。0.1.1 根据用户反馈移除原生顶部域名与三点菜单，网页直接占用这部分空间；连接失败页仍可重试和修改服务地址。连接页参考 [hermes-android](https://github.com/SenZh/hermes-android) 的深色、金色强调及卡片布局，未复制该项目的代码或图片。网页内的导航与聊天布局仍为原版，按后续阶段修改。

0.1.2 修复系统栏主题：顶部状态栏和底部导航栏跟随 Octop 网页的实际背景色，图标自动切换明暗；支持页面内实时切换、刷新和从后台返回。Android 15 的透明系统栏由原生安全区域提供同色背景。连接页、错误页继续使用其原生配色。

0.1.3 接入系统“另存为”与保存后的“打开”。网页通过原有鉴权请求取得文件，再把内容分块传到 Android，写入用户选择的文档 URI；取消保存可以继续操作。需同时使用本次构建的 dashboard。vivo Android 16 / API 36 真机已通过取消后重试、文本/中文空格文件名/PNG/PDF 保存与系统打开，以及约 2 MiB 文件完整性检查。完成提示使用实际保存名称；服务返回通用二进制 MIME 时按扩展名识别文件类型。普通 URL 下载监听及完整生命周期恢复仍待补验。

## 构建

要求：JDK 17 或 21、Android SDK Platform 35、Build Tools 36.0.0。最低 Android 8.0（API 26）。Gradle 版本及分发包 SHA-256 已固定在 Wrapper 配置。

PowerShell，从本目录运行：

```powershell
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

文件传输脚本测试（在仓库根目录运行）：`node --test android/app/src/test/js/downloads.test.cjs`。

SDK 从 `ANDROID_HOME` 或未跟踪的 `local.properties` 读取。初次构建需要下载 Gradle 和 Maven 依赖；如 Windows 使用代理，需要给 JVM 配置对应代理，不能仅依赖系统浏览器的代理设置。代理配置保存在本地，不提交到仓库。

本次开发的缓存与代理配置位于非同步目录 `D:\OctopAndroidCache\gradle-user-home`，避免 Synology 同步目录的 Gradle 文件重命名冲突及 C 盘空间不足。在同一机器复用这些下载时，先设置：

```powershell
$env:GRADLE_USER_HOME = 'D:\OctopAndroidCache\gradle-user-home'
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。Debug 包名为 `io.github.mookeview.octop.debug`，可以与将来的正式版并存。

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.mookeview.octop.debug/io.github.mookeview.octop.MainActivity
```

## 服务与登录

连接页预填 `https://octop.mookeview.cn:8880`，点击后保存。账号在服务网页中登录，Android 不单独保存密码或提取网页登录令牌。

已根据该实例的公开登录跳转配置以下 HTTPS SSO 来源：

- `https://nas.mookeview.cn:8880`
- `https://auth.mookeview.cn:8880`

允许的来源需要精确匹配协议、主机和端口；没有按域名后缀放行。其他站点的用户点击链接交给系统浏览器。接入不同 SSO 部署时，需要核对实际认证来源并更新 `authentication_origins`。用户已确认该服务公网可访问，需要 SSO 与二步验证；2026-10-30 用户实测确认已成功返回 Octop 首页。

下载使用 AndroidX WebKit 的 WebMessageListener，仅允许配置的 Octop 来源和主框架调用；SSO 来源与子框架不能调用下载桥。App 不提取网页登录令牌，也不忽略 TLS 证书错误。正式构建要求 HTTPS；Debug 为本机联调允许 HTTP。WebView 调试仅在 Debug 启用。

系统栏同步只在 App 前台展示网页时读取公开的主题属性和背景色，每 500 毫秒核对一次；进入后台或原生页面即停止。优先采用网页 CSS 的实际背景色，避免旧版 PWA `theme-color` 元数据与页面配色不一致。此修复随 APK 生效，无需更新服务端网页。

文件选择调用 Android 文档选择器，支持单选、多选和网页的文件类型过滤，无需申请整个存储空间的访问权限。取消或离开页面时清理待处理回调；只接受系统提供的 content URI。文件保存同样使用系统文档选择器，每次最多传输一个 64 KiB 分块，收到写入确认后才继续，结束时核对总字节数。保存失败或页面离开时关闭输出并尝试删除未完成的文档；打开时只授予所选文件的临时读取权限。系统分享、语音权限和第三方 OAuth 弹窗仍待适配，下载的完整生命周期恢复按后续阶段验证。

## 本机测试实例

后端数据放在根目录 `.tools/octop-data`，与用户已有服务独立。启动方式见 [环境与基线记录](../docs/android-development-baseline.md)。Android 模拟器通过 `http://10.0.2.2:18088` 访问主机上的测试实例；模拟器内的 `127.0.0.1` 指向模拟器本身。

仅 Debug 支持通过启动参数选择测试服务，便于设备验收：

```powershell
adb shell am start -n io.github.mookeview.octop.debug/io.github.mookeview.octop.MainActivity --es server_url http://10.0.2.2:18088
```

该参数不包含账号或令牌，正式版忽略此参数。

## 完成范围

当前已完成阶段 1 基础能力和阶段 2 第一批文字对话真机验收。附件、下载、后台恢复、通知及复杂远程操作的状态以 [功能矩阵](../docs/android-feature-matrix.md) 和 [Plan](../docs/android-client-plan.md) 为准。APK 成功构建不等于这些功能均已验收。

## USB 连接本机编译版本

用户已选择后续开发优先连接本机 Octop。先在仓库根目录构建网页：

```powershell
npm --prefix dashboard run build
```

构建结果由本机 `http://127.0.0.1:18088` 提供，服务启动方式见[环境记录](../docs/android-development-baseline.md)。在手机与本机保持 USB 连接时，执行 `adb reverse tcp:18088 tcp:18088`，手机即可通过同一地址连接。Debug 构建支持此 HTTP 地址；本机实例使用独立账号和数据，模型需另行配置。

本次设备连接脚本为忽略的 `.tools/connect-phone-to-local.cmd`，用户可在仓库根目录的 PowerShell 中运行：

```powershell
& '.\.tools\connect-phone-to-local.cmd'
```

本机测试账号保存在忽略的 `.tools/dev-account.json`。重新插拔 USB 后需重新建立映射。前端修改后重新构建并刷新 App 内网页即可加载新版本，原生容器代码变化时才需要重新构建 APK。2026-10-31 用户已授权后续 USB 安装、端口映射和手机界面操作由助手执行；测试结果区分用户反馈与助手实测。

## 正式服务首次登录验收（历史步骤）

1. 安装 debug APK，点击“连接服务”，使用预填的正式服务地址。
2. 在 Synology 页面自行完成账号登录与二步验证，确认返回 Octop，而不是停留在认证页。
3. 打开专家、聊天和设置入口；关闭并重新打开 App，确认地址和登录状态保留。

2026-10-30 用户已确认返回 Octop。后续 USB 验收由助手执行，需要本人解锁或输入身份凭据时再交由用户处理。无需在聊天中发送密码、验证码或模型密钥。正式服务数据仍由原服务管理。
