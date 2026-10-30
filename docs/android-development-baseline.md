# Android 开发环境与原版基线

日期：2026-10-30（Asia/Shanghai）。本地基线：`44c22434b090cb78d3a4a2d6fad786e7bbc95863`，分支 `feature/android-client`。

## 环境

| 项目 | 本轮确认结果 |
| --- | --- |
| 工作目录 | `D:\SynologyDrive\Coding\Octop` |
| Node / npm | 25.9.0 / 11.20.0 |
| Python | 项目由 uv 管理的 3.12.14；系统 Python 为 3.13.12 |
| uv | 0.12.18 |
| JDK | Microsoft OpenJDK 21.0.6，已配置 `JAVA_HOME` |
| Android SDK | Platform 35、36；Build Tools 36.0.0 |
| 设备 | 已创建任务专用 API 35 / x86_64 / Google APIs 模拟器；暂未连接真机 |
| 虚拟化 | WHPX 可用 |
| Gradle | 8.11.1，官方分发包 SHA-256 已校验 |
| make | 当前 Windows PATH 中没有 GNU make；检查使用对应的直接命令记录结果 |
| Git hooks | 已设置 `core.hooksPath=.githooks`；本轮未提交或推送 |

依赖按现有锁文件安装，未升级版本：`uv sync --locked --python 3.12`、`npm ci --no-audit --no-fund`。

## 两个测试目标

正式联调目标为用户提供的 `https://octop.mookeview.cn:8880`。公开健康接口和登录页会重定向到 Synology SSO；因此未登录时的 HTTP 200 HTML 不能记为 Octop 健康接口通过。本轮没有向该服务写入账号、配置或任务。

本机独立实例用于源码基线与客户端自动化，地址为 `http://127.0.0.1:18088`，数据保存在忽略目录 `.tools/octop-data`。已通过原 API 绑定 SQLite、创建独立开发账号、完成初始化，并验证登录及 `/api/auth/me` 返回管理员角色、默认专家已创建。未配置真实模型，不将其记为真实对话推理通过。

启动本机实例：

```powershell
cd D:\SynologyDrive\Coding\Octop
$env:OCTOP_HOME = Join-Path (Get-Location) '.tools\octop-data'
uv run --locked octop run --host 127.0.0.1 --port 18088
```

该目录下的开发账号只用于本机实例，凭据存储在忽略的 `.tools/dev-account.json` 中，不提交到仓库。使用已有服务时，在网页中登录自己的账号。

## 原版检查结果

| 检查 | 结果 |
| --- | --- |
| 前端 `npm run build` | 通过；包含 TypeScript 检查、Vite 构建和 PWA 构建 |
| 后端 `ruff check src tests` | 通过 |
| 后端 `ruff format --check src tests` | 通过，1275 个文件已符合格式 |
| 后端认证/初始化测试 | 10 项通过；仅第三方 ldap3 的弃用提示 |
| 原版 `/login`、`/setup` | 本机实例返回 HTTP 200 HTML |
| 本机初始化、登录、当前用户与默认专家 | 通过实际 HTTP API 验证 |
| 前端导航相关基线测试 | 27 项通过、1 项原版测试失败；见下文 |
| 后端 `mypy src/octop` | 通过，615 个源码文件 |
| 后端非 live 测试基线 | 提前停止：2265 通过、58 跳过、2 失败；不是完整测试全绿，见下文 |
| Android 单元测试、lint、构建 | 6 项单元测试通过；lint 0 errors、5 warnings；debug APK 已成功构建并安装 |
| API 35 设备实测 | 本机密码登录、触摸滑块、刷新后保持登录通过；专家与应用设置页面导航、系统返回通过；文件选择器取消/重新打开及选中文本文件可读取通过 |

### Node 25 的测试环境差异

最小复现：

```powershell
cd dashboard
node ./node_modules/vitest/vitest.mjs run src/layouts/layoutModeStorage.test.ts
```

原环境下 4 项失败，表现为全局 `localStorage.setItem/removeItem` 不是函数。只给父 Node 命令增加参数不能传递给所有测试工作线程；通过进程环境传递后，4 项全部通过：

```powershell
$env:NODE_OPTIONS = '--no-experimental-webstorage'
npm test -- src/layouts/layoutModeStorage.test.ts
```

此设置仅用于当前 Node 25 测试进程，没有修改网页的存储逻辑。开发也可以选择与仓库兼容的 Node LTS 环境。

### 原版侧栏分组测试

在未修改任何业务源码时，`sidebarNavLayout.test.ts` 的 `omits nav items and built-in groups the user cannot access` 失败：预期分组 `settings` 和 `c_aabbccdd`，实际仅有 `c_aabbccdd`。独立于上述存储开关，需在导航改造阶段结合分组设计核对；本轮不为让测试变绿而调整断言或改写原版导航。

完整输出保留在忽略的 `.tools/frontend-baseline-tests-node25.log`。本记录不代表全栈质量检查全绿。

### 后端测试的 Windows 环境限制

非 live 基线命令为 `uv run --locked pytest -n 4 -m 'not live' --basetemp .tools/pytest-full-baseline --maxfail=1 -q`，在约 43% 处提前停止：2265 passed、58 skipped、2 failed。失败发生在未修改原有业务源码时：

- `test_restore_repairs_old_physical_schema_with_current_watermark`：测试读取 SQL 时使用 Windows 默认 GBK，出现 `UnicodeDecodeError`。单独复现后，仅设置进程环境 `PYTHONUTF8=1`，同一测试通过。
- `test_published_list_keeps_public_portrait_without_upload`：Synology 同步目录内 `os.replace` 出现 WinError 5。改用非同步的 `D:/OctopAndroidCache/pytest-baseline-repro` 临时目录后，同一测试通过。

更早一次测试还受 C 盘不足影响；恢复空间并复测后，当时失败的三项全部通过。上述记录表明测试环境影响，不能直接认定为原版业务缺陷，也没有因此修改原版代码或断言。完整后端测试尚未跑完。

本机 Windows 后续测试建议设置 `PYTHONUTF8=1`，并把临时目录放到 D 盘非同步目录。所有日志与本机账号数据均留在忽略目录中，不作为源文件提交。

## Android 构建与设备记录

本机使用 `GRADLE_USER_HOME=D:\OctopAndroidCache\gradle-user-home`。原同步目录缓存出现文件重命名失败后，已改用非同步目录并成功构建；避免继续向空间紧张的 C 盘写入大型缓存。

验证命令：

```powershell
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
$env:GRADLE_USER_HOME = 'D:\OctopAndroidCache\gradle-user-home'
.\android\gradlew.bat -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`。设备为任务专用 `emulator-5580`，API 35，中文语言。设备端正常界面连接 `http://10.0.2.2:18088`，通过本机独立账号实际完成触摸滑块和网页密码登录，没有注入登录 token。页面刷新后仍保持登录；能够打开 `/experts`、`/admin/advanced`，Android 返回键回到 `/experts`。

文件选择器用同源测试文件输入验证：取消后可重新打开，选择系统 Downloads 中的测试文本，网页 File API 正确读取内容。这验证容器能力；本机未配置模型，因此不把它记为真实聊天附件上传完成。

连接超时用独立本地 HTTP fixture 验证：延迟 40 秒才响应，Android 在 30 秒出现原生超时提示；随后同地址服务恢复，点击“重新连接”加载成功，错误覆盖层消失。证据为 `.tools/screenshots/connection-timeout.png`，没有向正式服务制造延迟或写入数据。

本地截图存于忽略的 `.tools/screenshots/`：`connect-en.png`、`local-login.png`、`local-chat.png`、`local-experts.png`、`local-settings.png`、`file-picker.png`。截图中的聊天页明确显示未配置模型；真实对话、流式输出和工具审批仍待验。

用户已确认正式服务公网可达，需 SSO 与二步验证。模拟器首次直连超时，重新连接后，已在 App 内显示 `https://nas.mookeview.cn:8880` 的 Secure Signin 登录页，截图为 `.tools/screenshots/production-sso.png`。2026-10-30 用户反馈：正式服务完成 SSO 与二步验证后能成功返回 Octop 首页；此结果由用户实际登录验证，开发环境未使用其账号。调试参数启动命令被自动审批以 `blocked by policy` 拒绝；APK 安装成功，后续本机连接通过正常界面输入地址完成。

### 0.1.1：移除原生顶部工具栏

按用户反馈删除 52dp 的原生域名与三点菜单，以及相关菜单代码、图标和中英文文案。网页自身导航和系统状态栏继续显示；连接失败页仍有重试、修改地址入口。版本为 `0.1.1-debug` / versionCode 2，可直接覆盖安装。

- 验证：`:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 成功；6 项单元测试通过，lint 0 errors、5 warnings。
- API 35 模拟器覆盖安装成功，应用数据保留；SSO 登录页正常显示。
- WebView 顶边从 y=273 移至 y=136，增加 137 个物理像素（52dp），没有遮挡状态栏或底部系统手势区域。
- 添加同页历史记录后，Android 系统返回键正确回到前一记录；未发现 AndroidRuntime 崩溃。
- 截图：`.tools/screenshots/no-toolbar-sso.png`；测量记录：`.tools/toolbar-before.json`、`.tools/toolbar-after.json`。这些设备记录位于忽略目录。

### 0.1.2：系统栏跟随网页主题

用户反馈：Octop 选择浅色或深色时，Android 系统栏均为深色。最小复现为 API 35 模拟器打开本机 Octop 登录页，在网页存储中分别选择浅色/深色并刷新；`.venv/Scripts/python.exe .tools/check_system_bars.py` 对实际截图采样，修复前两项均失败：页面浅色为 RGB(255,255,255)、深色为 RGB(20,20,20)，上下系统栏始终为 RGB(13,12,10)。该脚本位于忽略的本机调试目录，不包含凭据。

根因：Android 主题固定了系统栏底色，容器未同步网页主题；Android 15 的透明系统栏还会露出原生根视图的固定底色。修复在容器前台读取 Octop 的 `data-theme` 及 `--fn-bg-primary`，同步原生安全区域、系统栏颜色和图标明暗。PWA `theme-color` 可能与当前 CSS 背景色不同，因此优先使用实际 CSS 表面色。后台和原生页面停止读取，并丢弃已过期的异步结果。

验证结果：

- 相同截图回归脚本在修复后通过，浅色两处系统栏均为 RGB(255,255,255)，深色均为 RGB(20,20,20)。
- 不刷新页面，连续切换主题四次通过；后台修改页面主题后返回 App，同步通过。
- 手势导航、三键导航两种模式下，浅色与深色检查均通过；模拟器测试后已恢复手势导航。
- 人工查看截图确认浅色使用深色系统图标，深色使用浅色图标。
- 触发原生跳转错误页时恢复其原生深色配色，回到 Octop 页后恢复网页配色，避免旧异步读取覆盖错误页主题。
- Android `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过：6 项单元测试，lint 0 errors、5 warnings。未修改后端或 dashboard 源码，因此未重复全栈基线测试。

产物版本：`0.1.2-debug` / versionCode 3。截图：`.tools/screenshots/system-bars-light.png`、`system-bars-dark.png`，三键导航截图另存为 `system-bars-light-three-button.png`、`system-bars-dark-three-button.png`。真机补充验证见下节；较旧 Android 系统仍待验。

### 0.1.2：USB 真机补充验收

2026-10-30，按用户要求直接测试 USB 连接的 vivo V2502DA（Android 16 / API 36，屏幕 1260×2800）。使用 `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` 覆盖安装现有 0.1.2-debug，原有正式服务登录状态保留，可直接进入 Octop。此次沿用已登录会话，没有重新执行 SSO 或二步验证。

通过真实设备上网页用户菜单的主题按钮完成验证，ADB 触摸操作使用 WebView 实际边界换算坐标，截图只保留系统栏区域：

- 连续深色、浅色、深色、浅色四次切换均通过。浅色页面和上下系统栏均为 RGB(255,255,255)；深色均为 RGB(20,20,20)。
- 截图确认系统状态栏的时间和图标随背景切换明暗，分别使用深色和浅色图标。
- 深色、浅色各自切到桌面再返回，网页主题和上下系统栏仍保持一致，两项均通过。
- 测试后恢复用户原来的浅色偏好，保留原调色板与自定义强调色；关闭测试时打开的网页菜单。
- 检查当前应用进程的 `logcat --pid=<pid> -d -s AndroidRuntime:E`，未发现 `FATAL EXCEPTION`。

中途自动锁屏时停止设备操作，用户解锁后继续。浅色后台返回的一次自动操作未实际选中浅色，属于测试准备未完成；补测先断言页面确为浅色，再执行后台返回，该项通过。未将这次无效操作记为产品失败或已通过结果。

本机验证脚本和原始记录位于忽略目录 `.tools/`：`phone_system_bars.py`、`phone-system-bars-result.json`、`phone-resume-light-result.json`；汇总为 `phone-verification-result.json`。证据截图为 `.tools/screenshots/phone-switch-*-bars.png`、`phone-resume-dark-bars.png`、`phone-resume-light-bars.png`，不包含聊天正文或账号菜单内容。

此次仅补充设备验收与文档，没有修改 Android、dashboard 或后端源码，也未重新构建相同 APK 或重复全栈基线测试。真机使用其原有系统导航方式，没有切换手机系统导航设置；三键导航验证仍以 API 35 模拟器记录为准。此结果不代表聊天、下载、通知或全部功能已验收。

## 阶段 2：文字对话补充验收

2026-10-30，现有 0.1.2-debug 在 vivo Android 16 / API 36 上完成正式服务的 12 项文字对话验收，包括真实模型与流式回复、停止后继续、键盘、滚动、专家切换及历史恢复。测试后恢复屏幕设置；本批没有产品代码变更。过程、证据与剩余范围见[文字对话验收记录](android-stage2-chat-acceptance.md)。

## 本地编译联调与附件提示调整

2026-10-30，用户确认回形针能够打开系统文件选择器，并要求去掉点击时的黑色提示。修改归属本仓库 `dashboard/src/pages/Chat/components/ChatInputActionsRow.tsx`：手机布局时将附件 Tooltip 的标题设为 `null`，保留按钮的无障碍名称与上传菜单。

- 现有 `ChatInputActionsRow.test.tsx` 的 13 项测试通过。
- 该文件 ESLint、Prettier 通过；`npm run build` 中的 TypeScript、Vite 与 PWA 构建通过。日志保留于忽略的 `.tools/frontend-build-upload-tooltip.log`。Vite 仍有既有大分包体积提示。
- 本机服务已使用独立 `.tools/octop-data` 启动，监听 `127.0.0.1:18088`。根网页返回 HTTP 200，响应与新编译的 `src/octop/dashboard/index.html` 字节哈希一致；初始化状态 `setup_required=false`。
- 用户要求后续 App 使用本机编译版本。计划通过 USB reverse 将手机 `http://127.0.0.1:18088` 映射到电脑服务。自动审批拦截组合 ADB 操作，仅返回 `blocked by policy`，命令未执行；没有改用其他执行方式重试该设备操作。
- 已准备本地忽略脚本 `.tools/connect-phone-to-local.cmd` 供用户手动运行，并沿用 `.tools/dev-account.json` 中的独立测试账号。用户已反馈手机显示本地登录页；服务端日志确认请求进入本机实例。助手用本机账号完成滑块与登录，手机已进入本地 `/chat` 首页；登录令牌未输出。此前手机端手动输入返回 401，之后由助手重新提交成功。回形针提示移除的真机结果尚不记为设备验收通过。
- 用户随后手动点击回形针确认：手机布局不再出现黑色 Tooltip，上传入口仍可用；该项以用户反馈记为通过。下一项继续手动验证系统选择器选中文件后的附件预览。
- 用户手动选择小型测试文件后确认附件预览正常显示；未发送消息，避免本地实例模型配置影响该项。取消附件操作仍待单独验证。
- 用户确认取消附件后状态正常。真机测试后续按新约定执行：简单独立单步合并测试，涉及状态变化或风险的流程继续逐步验证。

本批未修改 Android APK 或后端源码。当前本机实例未配置模型，正式服务的真实推理结果不等于本机模型已配置。

## 功能覆盖

[功能矩阵](android-feature-matrix.md) 有 42 项主要功能记录，并覆盖原路由表 63 个显式路由及登录/向导/邀请入口。来源为真实路由、导航与权限代码；所有未进行设备操作的项目仍标记“待验”。

## 界面参考

用户指定 [SenZh/hermes-android](https://github.com/SenZh/hermes-android)。已查看其 README 的连接、对话、审批、会话、Agent、技能、定时任务与消息平台截图。拟参考：暖黑背景、低对比卡片边框、金色主要操作、清晰的标题与正文层级、底部主要导航。

该参考项目是 React Native 客户端，采用不同后端协议；本项目当前保留 Octop React/Web API 与 Kotlin 容器路线。连接页先应用视觉方向，其余页面按 Plan 逐步推进。未复制参考项目的代码或素材。
