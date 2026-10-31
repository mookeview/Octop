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
| 设备 | API 35 / x86_64 / Google APIs 模拟器；后续已连接 vivo V2502DA 真机，Android 16 / API 36，当前 USB 联调由助手操作 |
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

## 阶段 2：聊天附件选择与移除验收

2026-10-31，沿用 vivo Android 16 / API 36、0.1.2-debug 与 USB 本机实例 `http://127.0.0.1:18088`。助手恢复 `adb reverse tcp:18088 tcp:18088`；用户按一批操作完成以下检查，统一反馈“全部通过”。本批未发送聊天消息。

| 检查 | 用户反馈 |
| --- | --- |
| 打开聊天文件选择器后直接返回 | 通过：没有新增附件，回形针仍可用 |
| 选择、移除后重新选择同一个文件 | 通过：再次正常显示，只有一份附件 |
| 一次选择两个小型文件 | 通过：两个附件正常显示，上传转圈结束 |
| 分别移除附件并重新打开选择器 | 通过：移除一个后另一个保留，全部移除后附件区清空，入口仍可用 |

助手只读核对 `.tools/octop-local-server.out.log`：`POST /api/agents/main/upload` 累计 13 条记录均为 HTTP 200。该累计值包含此前操作，仅作为本机上传链路成功的补充证据，不作为本批请求次数。没有读取或导出用户上传文件的内容。

本机配置现已包含启用的模型提供方，`main.default_model` 为 `Zhipu Coding Plan (BigModel)/glm-5.3-flash`，专家状态为 `running`；仅核对模型标识、凭据是否存在与运行状态，未输出凭据或主动发起推理。下一批由用户验证本机实际回复、单个/多个文本附件发送与读取、附件消息历史恢复。专用测试文件位于手机下载目录 `Download/OctopTest/`。

本批仅更新验收文档与忽略目录中的测试材料，未修改产品源码或重新构建 APK；文档使用 `git diff --check` 校验，未重复运行业务测试。

## 阶段 2：本机附件读取路径诊断与配置修正

2026-10-31，用户反馈本机实际文字回复通过，单个及两个文本附件读取均失败，提示“文件路径超出当前专家的存储根目录”。附件消息历史恢复未收到反馈，继续待验。

本机测试专家配置的 `backend.root_dir` 为 `C:/`，`workspace_dir` 为 `D:/SynologyDrive/Coding/Octop/.tools/octop-data/agents/main`。通过真实 `save_attachment` → 工作区下载 → 生成附件路径提示 → 文件工具后端读取复现：前两步正常，工具读取抛出 `outside root directory: C:\`。对比实验只把根目录设为该专家工作区，附件提示改为 `/inbound/...`，工具成功读取同一校验文本。问题归属本机专家配置，未发现需要在 Octop 或运行时库修改源码的证据。

修正通过本机已登录测试会话调用 `PATCH /api/agents/main`，只将后端根目录对齐实际工作区，其余配置保留；接口返回 HTTP 200。随后 `POST /api/agents/main/reload` 返回 HTTP 204，专家状态为 `running`。配置快照及不含认证信息的结果保留在忽略目录 `.tools/main-config-before-attachment-root-fix.json` 和 `.tools/local-attachment-root-fix-result.json`。未操作手机界面或自动发送模型请求。

验证命令及结果：

- `.venv/Scripts/python.exe .tools/repro_attachment_tool_path.py`：修正前稳定复现越界错误；修正后输出 `TOOL_READ_OK`，读取到测试标记。复现脚本通过工作区接口保存和清理自己的诊断附件。
- `uv run --locked pytest tests/unit/gateway/test_attachment_path_resolution.py tests/unit/gateway/test_inbound_store.py tests/unit/api/test_attachments.py -q --basetemp D:/OctopAndroidCache/pytest-attachment-root-fix`：`36 passed, 2 skipped`，跳过项为 POSIX 专用用例。

本轮仅修正忽略目录中的测试实例配置、更新验收文档，未修改产品源码，未运行 `make all` 或重新构建 APK。尚需用户在新会话复测单/双文本附件内容读取及附件消息历史保留，不能据自动读取通过将整条聊天链路标为通过。

## 阶段 2：附件元数据路径拆分越界修复

2026-10-31，根目录配置修正后，用户复测单附件仍失败，红框提示“模型调用失败：list index out of range”。本机结构化日志显示异常发生在 Octop 的 `BinaryReadGuardMiddleware.awrap_tool_call` → `_attachment_media_type` → `_workspace_path`，未进入实际 `read_file` 后端。双附件和历史保留本轮未验收。

复现命令：

```powershell
uv run --locked python -c "from octop.infra.agents.middleware.binary_read_guard import _workspace_path; assert _workspace_path('inbound/octop-attachment-a.txt') == 'inbound/octop-attachment-a.txt'; print('ATTACHMENT_PATH_OK')"
```

修复前稳定抛出相同 `IndexError`。原因是判断 `/inbound/` 存在时使用临时添加前导斜杠的字符串，随后却拆分原字符串；`inbound/...` 和 `/inbound/...` 都会触发。修复归属 Octop 的附件元数据适配，未改动运行时库：仅将拆分操作改为使用同一个带前导斜杠的字符串。

`tests/unit/agents/test_binary_read_guard.py` 新增工具调用层回归用例，使用真实 `ToolCallRequest` 和 `HumanMessage.octop_inbound_attachments`，覆盖相对路径、虚拟根路径、通过 `tmp_path` 构造的主机绝对路径及反斜杠路径；同时检查普通文本、通过 MIME 标注的文本和通过 MIME 识别的二进制附件。测试在修复前复现同一异常，修复后文本正常交给后端，二进制附件仍被拦截。此前已有测试只覆盖拦截理由函数，没有走元数据匹配链路。

验证：

- 上述原复现命令：修复后输出 `ATTACHMENT_PATH_OK`。
- `uv run --locked pytest tests/unit/agents/test_binary_read_guard.py tests/unit/gateway/test_attachment_path_resolution.py tests/unit/gateway/test_inbound_store.py tests/unit/api/test_attachments.py -q --basetemp D:/OctopAndroidCache/pytest-read-guard-green`：`60 passed, 2 skipped`，跳过项为 POSIX 专用用例。
- `.venv/Scripts/python.exe .tools/repro_attachment_tool_path.py`：扩展诊断到上传、工作区下载、路径提示、附件元数据检查和实际工具读取，输出 `TOOL_READ_OK` 与 `ATTACHMENT_PIPELINE_OK`；使用自己的测试标记并经工作区接口清理诊断附件，没有调用模型。
- `uv run --locked ruff check src tests`、`uv run --locked ruff format --check src tests`：通过；`uv run --locked mypy src/octop`：615 个源文件通过。
- `cd dashboard; npx --no-install prettier --write src` 后格式检查通过。初次检查发现 Windows 检出文件的 CRLF 与 Prettier 的 LF 不一致；处理换行符并恢复与任务无关的 SVG 原始换行符后，最终 `npx --no-install prettier --check src --end-of-line auto` 通过。Git 确认没有 dashboard 差异或已暂存改动。
- 本机没有 `make`，`make all` 无法直接执行，已分项执行对应检查。首次非 live 全量测试为 `4502 passed, 207 skipped, 1 failed`，失败用例 `test_entrypoint_restart_skips_init_for_external_db` 调用了 `C:/Windows/system32/bash.exe`（本机不可用的 WSL 启动器）；将 `C:/Program Files/Git/bin` 放在该命令的 PATH 前部后，此未改动的用例单独复测通过。
- 使用上述 Git Bash PATH，执行 `uv run --locked pytest -n 4 -m "not live" --basetemp D:/OctopAndroidCache/pytest-read-guard-full-git-bash --maxfail=1 -q`：`5127 passed, 223 skipped, 18 warnings`，耗时 443.57 秒。临时目录在 D 盘；日志为忽略的 `.tools/backend-read-guard-full-git-bash.log`。没有为这项环境问题修改 Docker 启动脚本或其测试。

助手停止并重启测试服务的操作被自动审批拒绝，仅返回 `blocked by policy`；准备 `.tools/restart-local-octop.cmd` 供用户手动运行，脚本校验 18088 端口进程属于本项目后才重启。用户反馈“已重启”，只读检查确认根网页 HTTP 200、`GET /api/agents/main` 返回运行状态及正确根目录。没有自动操作手机界面或提交模型请求。

用户随后统一反馈修复后的 3 项真机检查“全部通过”：

| 检查 | 用户反馈 |
| --- | --- |
| 单文本附件发送和读取首行 | 通过：回复含 `OCTOP_ATTACHMENT_A_7316`，已发送消息显示附件 |
| 双文本附件发送和分别读取首行 | 通过：回复分别含 a、b 校验标记，已发送消息显示两个附件 |
| 从历史重新打开测试会话 | 通过：两轮消息、附件和回复完整保留 |

该 `IndexError` 已经真机复测关闭；图片消息、上传失败提示及恢复、下载和系统打开继续按矩阵验收。

下一批测试材料：API 核对本机上传限制为 100 MiB，手机下载目录 `Download/OctopTest/` 新增 `octop-over-limit.bin`（104857601 字节）及 `中文 附件测试.txt`（首行标记 `OCTOP_ATTACHMENT_NAME_5824`），均已与本地材料比对 SHA-256。超限文件的本地副本放在非同步缓存 `D:/OctopAndroidCache/fixtures/`。此准备仅复制测试材料，没有操作手机界面或发送模型请求。

## 阶段 2：超限提示、恢复与中文文件名验收

2026-10-31，用户按一批操作统一反馈以下 3 项“全部通过”：

| 检查 | 用户反馈 |
| --- | --- |
| 选择超限文件 | 通过：出现最大 100MB 的大小限制提示，没有新增附件，回形针仍可用 |
| 超限后正常上传并发送文本 | 通过：a 文本正常上传、发送并读出 `OCTOP_ATTACHMENT_A_7316` |
| 中文和空格文件名的附件 | 通过：`中文 附件测试.txt` 的文件名完整保留，回复含 `OCTOP_ATTACHMENT_NAME_5824` |

超限由前端大小校验拒绝，这批未覆盖网络或服务端上传错误。用户反馈后，助手先比对本机与手机超限测试文件的 SHA-256，再只删除两处对应的 `octop-over-limit.bin`；已验证两处文件均不存在，保留正常文本测试材料。

随后准备图片预览测试材料：在同一手机目录准备 `octop-image-a.png`（640×480，红圆与 A 7316）和 `octop-image-b.png`（640×480，蓝方块与 B 4289），均以 Pillow 绘制，PNG 校验及手机/本机 SHA-256 比对通过。该批操作验证单图放大关闭、多图切换、图文混合附件分别移除及输入草稿保留，不发送消息；结果见下节。

只读核对本机模型配置：唯一声明图片输入的 `glm-5v-turbo` 未启用。图片消息发送与识别尚不具备本机测试前置条件，本批未变更模型配置，也没有自动操作手机界面或提交模型请求。

本轮仅更新验收文档、清理超限临时材料并准备图片，使用 `git diff --check` 校验；产品源码没有新增变更，未重复运行此前已通过的全量检查。

## 阶段 2：图片预览与图文混合附件验收

2026-10-31，沿用 vivo Android 16 / API 36、0.1.2-debug 与 USB 本机实例，用户统一反馈以下 3 项“均通过”：

| 检查 | 用户反馈 |
| --- | --- |
| 单图片缩略图、放大与关闭 | 通过：关闭预览后输入草稿和附件保留 |
| 两张图片放大后切换查看 | 通过：可切换查看两图，关闭后附件保留 |
| 图片与文本混合选择、分别移除 | 通过：可分别移除附件，输入草稿保留 |

本批没有发送图片消息；当时本机已有 `Zhipu Coding Plan (BigModel)/glm-5v-turbo`，声明支持文字和图片输入，但尚未启用。随后安排用户启用并保存，在通用助手新会话明确选择该模型，验证单/双图识别及图片消息历史保留，结果见下节。

本轮仅同步三份验收文档，使用 `git -c core.safecrlf=false diff --check` 校验；未新增产品源码变更，沿用此前已通过的全量检查结果。

## 阶段 2：图片发送、识别与历史保留验收

2026-10-31，沿用 vivo Android 16 / API 36、0.1.2-debug 与 USB 本机实例，用户统一反馈以下 4 项“均通过”：

| 检查 | 用户反馈 |
| --- | --- |
| 启用视觉模型并选择 | 通过：在提供方设置中启用并保存 `glm-5v-turbo`，在通用助手新会话选中 |
| 单图发送与识别 | 通过：识别 a 图为红色圆形、7316，已发送消息显示图片 |
| 双图发送与分别识别 | 通过：a 图为红色圆形、7316；b 图为蓝色方块、4289 |
| 从历史重新打开测试会话 | 通过：两轮图片和回复完整保留，图片仍可放大查看 |

模型设置与手机操作由用户完成，助手未自动发送模型请求。随后安排上传网络失败提示与恢复验收，结果见下节。

助手只读检查确认设备连接和 `tcp:18088` 映射存在。本轮仅同步三份验收文档，使用 `git -c core.safecrlf=false diff --check` 校验；未新增产品源码变更，未重复运行此前已通过的全量检查。

## 阶段 2：上传网络失败与恢复验收

2026-10-31，沿用 vivo Android 16 / API 36、0.1.2-debug 与 USB 本机实例，用户反馈“测试全部通过”：

| 检查 | 用户反馈 |
| --- | --- |
| 移除 18088 映射后选择小文本附件 | 通过：出现失败提示，上传转圈结束，没有新增附件 |
| 上传失败后的输入草稿 | 通过：原草稿保留 |
| 恢复映射后原页面重选同一文件 | 通过：无需刷新，只显示一个附件，草稿仍保留 |
| 恢复后发送并读取附件 | 通过：回复含 `OCTOP_ATTACHMENT_A_7316` |

连接中断与恢复由用户在电脑手动执行，手机操作及模型请求也由用户完成。此记录验证真实网络连接失败及恢复，不覆盖服务端 HTTP 错误。下一步实现 Android 文件保存与系统打开，真机验收另行记录。

## 阶段 2：0.1.3 文件保存与系统打开实现

2026-10-31，修改归属本仓库 Android 容器与 dashboard 下载工具，未改动运行时库。版本为 `0.1.3-debug` / versionCode 4，APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`。

- 网页原有带鉴权的文件请求保持在网页执行；`saveBlobAsFile` 识别 Android 下载适配器，将文件交给系统文档保存框。普通浏览器、桌面和其他内嵌浏览器继续使用既有分支。
- AndroidX WebKit `1.12.1` 的 WebMessageListener 仅注册配置的 Octop 来源，原生回调再次检查来源、当前页面及主框架；SSO 来源和子框架没有下载权限。不向原生传递登录令牌。
- 用户确认保存位置后，网页每次传输至多 64 KiB，收到磁盘写入确认再发送下一块；保存结束前核对总字节数。磁盘写入在单独执行器运行。取消不报下载失败，页面离开或写入失败时关闭输出并尝试删除未完成文档。
- 保存完成后提供“打开”，以所选文档 URI 和对应 MIME 调用系统应用，只附加临时读取授权。没有处理程序时提示文件已保存，未申请整个存储空间权限。
- 普通同源 URL、blob 与 data 下载监听也已接入；设备上的文本、图片、PDF、普通 URL 和生命周期恢复仍按批次验收，不以单元测试替代。

验证命令与结果：

- 设置既有 JDK 与 `GRADLE_USER_HOME=D:/OctopAndroidCache/gradle-user-home` 后，运行 `android/gradlew.bat -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain`：构建成功，11 项单元测试通过，lint 0 errors、6 warnings（含新增依赖版本提示，下载 API 的功能检查警告已修正）。
- `node --test android/app/src/test/js/downloads.test.cjs`：4 项通过，覆盖二进制多块完整性、取消后重试、磁盘失败中断及普通页面不安装适配器。
- `cd dashboard; npx --no-install vitest run src/utils/saveBlobAsFile.test.ts`，设置 `NODE_OPTIONS=--no-experimental-webstorage`：5 项通过，覆盖 Android 分支、错误传播和既有下载分支。
- `cd dashboard; npx --no-install tsc -b`、对修改的两个 TypeScript 文件运行 ESLint、`npx --no-install prettier --check src --end-of-line auto`：通过。
- `cd dashboard; npm run build`：TypeScript、Vite、PWA 构建通过，保留既有大分包体积提示。
- `git -c core.safecrlf=false diff --check`：通过。本机没有 `make`；后端源码本轮未变更，沿用此前对应检查及非 live 全量 `5127 passed, 223 skipped` 的结果，未重复运行。

上一轮设备验证限制：独立 API 35 模拟器恢复快照后 ADB 操作挂起，关闭该测试模拟器并冷启动后，自动审批拒绝了安装并启动 APK 的操作，仅返回 `blocked by policy`；当时没有改用其他执行方式安装，系统保存框和外部应用打开未记为通过。曾准备 `.tools/install-download-client.cmd` 供用户手动操作。此段保留为历史，最新授权与真机结果见下节。

上一轮最终检查时，`http://127.0.0.1:18088/` 拒绝连接，服务未监听，未验证新网页在线响应。本轮已由助手启动独立服务、核对在线构建并完成下述真机验收。

## 阶段 2：0.1.3 文件下载真机验收

2026-10-31，用户明确改为“现在连机操作都由你来进行”。本轮安装、USB 映射、系统保存框、外部应用打开与结果核对均由助手执行。设备为 vivo V2502DA、Android 16 / API 36，包为 `0.1.3-debug` / versionCode 4；没有变更手机安全设置或设置默认查看器。

前置检查：本机 18088 原先未监听，助手启动 `.tools/octop-data` 独立服务并恢复 `adb reverse tcp:18088 tcp:18088`；网页 HTTP 200，在线首页与新构建文件哈希一致。覆盖安装成功，登录与既有会话历史保留。本批只使用专用测试文件，没有发送模型消息。

| 检查 | 助手真机结果 |
| --- | --- |
| 取消系统保存框 | 通过：返回 Octop，下载入口可用，没有前端错误，文件预览保留 |
| 取消后再次下载 | 通过：再次打开保存框并正常取消 |
| 文本另存为并改名 | 通过：65 字节，内容与原件一致，完成提示使用实际保存名称 |
| 文本系统打开 | 通过：vivo 文档显示 `OCTOP_ATTACHMENT_A_7316` |
| 中文与空格文件名 | 通过：名称保留，82 字节与原件一致；vivo 文档显示 `OCTOP_ATTACHMENT_NAME_5824` 和中文正文 |
| PNG 保存与系统打开 | 通过：7,071 字节与原件一致；系统收到 `image/png`，相册显示红圆与 `A 7316` |
| PDF 保存与系统打开 | 通过：10,931 字节与原件一致；vivo PDF 查看器显示测试红圆 |
| 约 2 MiB 分块文件 | 通过：2,097,408 字节，SHA-256 与测试原件一致；超过单个 64 KiB 分块上限 |

真机发现并修正两项问题，修改归属本仓库 `android/` 的 `WebDownloads.kt`：

1. 用户在系统保存框修改文件名后，完成提示仍显示请求时的原名。真实回归在修复前得到 `content_match: true`、`dialog_uses_saved_filename: false`；改为在 IO 执行器查询所选 URI 的 `OpenableColumns.DISPLAY_NAME`，完成提示及打开时的标签使用实际名称，回归两项均为 `true`。
2. 工作区下载响应为 `application/octet-stream`，原生端把它直接用于系统打开，PNG 被交给文档查看器并提示不支持。实际 ACTION_VIEW 检查先记录 `actual_types: ["application/octet-stream"]`、`passed: false`；对通用类型按文件扩展名查询 Android MIME 表后，同一真机流程得到 `actual_types: ["image/png"]`、`passed: true`，相册显示正确。未知扩展名仍保留通用二进制类型。

这两项依赖真实文档提供方与系统 Intent，回归采用真机完整链路，未以浅层 mock 代替。使用 `diagnosing-bugs` 流程先记录失败，再修复、重建、覆盖安装和复验。最终 Gradle 命令与上节一致，11 项单元测试通过，lint 0 errors / 6 warnings，APK 构建成功。前端与后端本轮未新增源码变更，沿用上节针对性检查及此前非 live 全量结果；本机没有 `make`，未声称本轮运行了 `make all`。

本机证据位于忽略目录：`.tools/phone-download-repro-red.json`、`phone-download-repro-result.json`、`phone-download-mime-red.json`、`phone-download-mime-repro.json`、`phone-download-acceptance-result.json`。回归命令为 `uv run python .tools/repro_phone_download.py`（先打开指定 a 文本预览）及 `uv run python .tools/repro_phone_download_mime.py`（测试 PNG 已由系统打开时运行）。截图为 `.tools/screenshots/phone-download-{text,chinese,png,pdf}-open.png`，正文和图形已目视核对。验收结束已确认没有残留 trace wrapper、没有前端错误，并返回 Octop 聊天页；临时 WebView 调试转发已移除，18088 服务映射保留。

完整性摘要：文本 `5cb8a03b98e580508f7adbc28ca35b61310a36210875343fa874a5057218d089`；中文文本 `4bfa2bf4902d4c4aba7d364c0790689b1b5f9b9760b9a27e5bc66094c758a75d`；PNG `3748727bcba6d5cead8f437b8c9ec77b6faa3b7d7ff216c025b08d4d35fa86e6`；PDF `00af9fa8aa90677da2db64dc6b53c0954351edbbbfc8fe56fb07db154aef0020`；分块文件 `ad4707187ce2c9c3d16909bd20eb1c440fa9d2717bff5b836db2d32f997da4d2`。

测试脚本曾误把保存位置当作 `Download/OctopTest`，以及在 `adb exec-out` 参数中为中文路径加入了 shell 引号；均已改为读取系统实际保存的文件并通过 `adb pull` 比对字节。这些是驱动错误，未记为产品文件丢失。原始材料在 `Download/OctopTest`，本批保存位置为 `Download` 根目录。

此批当时仅覆盖工作区文件通过网页鉴权请求后的 blob 保存与系统打开。工具审批、模型生成文件链接及运行中后台恢复的后续结果见下节；普通 URL 下载监听、页面离开/旋转/进程回收时的传输恢复，以及分享仍待补验。

## 阶段 2：工具审批、后台恢复与生成文件交付

2026-10-31，助手沿用上述 vivo API 36、0.1.3-debug 和本机独立实例，完成工具允许、拒绝、待审批刷新及拒绝后继续 4 项真机验收。全局审批策略仅为本机测试临时调整，结束后已恢复并对比原值。

运行中后台 70 秒测试发现真实问题：后端已完成且保存完整回复，手机重连后只封存了部分正文。先以真实 `useChat`、导航 hook 和 store 组合复现，再最小修改本仓库 dashboard 的恢复同步；后台 6 秒、70 秒各 5 项通过，没有重复消息，前后端结束状态一致。没有重装 APK 或修改后端、Android 及运行时库。

经批准生成的文件已在工作区预览，从模型回复的鉴权链接进入系统保存框，保存后由 vivo 文档显示正确正文，22 字节和 SHA-256 匹配。历史重开按 25 条分页加载，加载更早后全部 10 条用户消息 ID 与服务端一致；最初直接比较 DOM 总数的断言属于脚本假设错误。

本轮 `vitest` 聊天 hooks 共 34 文件、237 项通过；前端类型检查、新 hook/测试的 ESLint、三个相关文件的 Prettier 及网页构建通过。`chatStore.ts` 完整 ESLint 仍有 HEAD 已存在的第 1486 行 `no-extra-boolean-cast`，本次未改该行。本机没有 `make`，后端与 Android 没有本轮源码修改，沿用此前全量和构建结果，未重复或声称运行 `make all`。

结束已核对后端空闲、无待审批、原安全设置一致、网页无错误和调试 wrapper，关闭文件面板并留在 Octop；移除临时 19223 调试转发，保留 18088 服务映射。完整步骤、失败原因、检查命令与证据见[专项验收记录](android-stage2-approval-recovery-acceptance.md)。

## 功能覆盖

[功能矩阵](android-feature-matrix.md) 有 42 项主要功能记录，并覆盖原路由表 63 个显式路由及登录/向导/邀请入口。来源为真实路由、导航与权限代码；所有未进行设备操作的项目仍标记“待验”。

## 界面参考

用户指定 [SenZh/hermes-android](https://github.com/SenZh/hermes-android)。已查看其 README 的连接、对话、审批、会话、Agent、技能、定时任务与消息平台截图。拟参考：暖黑背景、低对比卡片边框、金色主要操作、清晰的标题与正文层级、底部主要导航。

该参考项目是 React Native 客户端，采用不同后端协议；本项目当前保留 Octop React/Web API 与 Kotlin 容器路线。连接页先应用视觉方向，其余页面按 Plan 逐步推进。未复制参考项目的代码或素材。
