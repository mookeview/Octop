# Android 阶段 2：无真机补验

日期：2026-10-31（Asia/Shanghai）。工作目录：`D:\Coding\Octop`；基于 `feature/android-client` / `d8859bb8`。设备操作限定独立模拟器 `emulator-5580`，不使用正式服务账号或模型凭据。

## 当前结论

三项补验已完成自动化及 API 35 模拟器验证，并发现、修正两个产品问题。生成流程使用受控的 `FakeHarnessAgent`；不将这些结果记作真实模型理解或真机验收通过。阶段 3 尚未开始。

后续：同日已在 vivo API 36 使用真实模型补齐生成中排队自动续发、直接 Steer、上下文理解与计算、历史去重及 App 重启恢复，详见[真机收尾记录](android-stage2-steer-device-acceptance.md)。下文仍记录本轮无真机补验本身的覆盖边界。

| 补验项 | 已验证 | 边界 |
| --- | --- | --- |
| 普通 URL 下载 | 实际 WebView DownloadListener、携带页面会话的 HTTP 请求、系统保存框、英文及中文空格文件名、取消后重试；四个文件的字节数与 SHA-256 全部一致；HTML Viewer 打开文本、Photos 打开 PNG | API 35 模拟器；不覆盖锁屏、网络切换或进程回收 |
| 上传 HTTP 错误与恢复 | 自动化覆盖真实 XHR 的 413/500/502/503；模拟器使用实际聊天页、系统选择器及真实上传 API，依次得到 500/200/500/200；失败提示、草稿与已有附件保留、同一文件重选恢复通过 | 500 由隔离测试服务注入，其余状态码在自动化中验证 |
| 生成中追加信息 | 实际聊天页生成中输入、排队、输入框清空；点击 Steer 后后台接收追加；原回复结束后继续执行一次，运行时共收到两次请求；历史恰有两条用户消息，追加只出现一次，ID 与 WebSocket 通知一致，最终空闲 | 本轮未覆盖真实模型；随后由[真机验收](android-stage2-steer-device-acceptance.md)补齐 |

## 本轮发现与修正

### URL 下载文件名退回 URL 路径

服务端返回同时包含 `filename` 和 UTF-8 `filename*` 的标准 `Content-Disposition` 时，原 APK 保存框把 `url-note.txt` 显示成 `text.txt`，把 `中文 URL.txt` 显示成 `chinese.txt`。同一链接重复操作可稳定复现，文件名断言失败。

归属本仓库 `android/`。`WebDownloads.kt` 将 `android.webkit.URLUtil.guessFileName` 替换为已有 AndroidX WebKit 依赖中的 `URLUtilCompat.guessFileName`，保留原文件名清理及保存流程。重新构建、覆盖安装后，同一响应头和保存框断言通过，中文和空格保留，保存文件哈希正确。

反馈环直接检查真实 Android 保存框；普通 JVM 测试不能覆盖框架文件名解析，本轮没有用浅层替身测试代替它。

### 生成中追加消息在历史中消失

实际网页先发送带客户端消息 ID 的首条消息，再排队并 Steer。运行时收到追加内容，页面有回复，但历史接口只有首条用户消息。将首条消息 ID 加入集成测试后，结果为 **1 failed / 1 passed**；不带 ID 的旧场景通过，带 ID 的网页场景稳定失败。

归属本仓库 `infra/gateway/ws/` 的续轮编排。`_continuation_inbound` 原样继承首条消息的 `client_message_id`，导致历史按同一 ID 去重。修复将已排空追加消息的 `message_id` 传入续轮，使历史与 WebSocket 用户气泡使用同一 ID。

新增回归覆盖首条消息有/无客户端 ID、相同追加请求重试去重、继续执行一次、历史保留一次及 ID 对齐。修复后相关后端 **80 项通过**。模拟器重新创建对话复验，历史保留两条用户消息，追加 ID 与 `steer.drained.message_id` 一致，最终 `turn_active=false`。

## 模拟器证据

- AVD：`OctopStage2_20261031`，Android 15 / API 35，x86_64，WHPX；WebView `124.0.6367.219`。
- APK：`0.1.3-debug` / versionCode 4，`android/app/build/outputs/apk/debug/app-debug.apk`。
- 修复后 APK SHA-256：`EDF209FDB4AB4D8A65C8311E8FB3ACFF00015239475CE0FEB03FDE5F64613B17`。
- 下载服务要求页面会话 Cookie；DownloadListener 请求和后续取数均携带会话。由系统文档 URI 保存后，从模拟器读取字节核对。

| 文件 | 字节数 | SHA-256 |
| --- | --- | --- |
| `url-note.txt` | 24 | `33628582ca0ee54efaec650fbc78188bc4723de711a40ee0ef5cea53ed51e2d2` |
| `中文 URL.txt` | 27 | `3b50d8a9165a34abfd638c85e207602ce231073c29fc32d534c13f4a51a97f19` |
| `url-image.png` | 68 | `12c5f2d047c104162162d8a5a74281257ecd3b38e225d4fb8bda77e3427d65dd` |
| `url-large.bin` | 2097173 | `a2740af02cd2d65e83432c67a7c8c3bc41bea4a0cc5adbea46cd0b6f8cd78d08` |

聊天使用实际构建后的 Dashboard、Octop HTTP/ASGI/WebSocket 路由、数据库和工作区上传。测试服务仅注入上传错误及受控运行时响应。先前准备的独立 ChatInput 页面没有被记作完整聊天页验收。

本地证据位于忽略目录 `.tools/`：

- `url-device-verification.json`、`url-http-events.jsonl`：下载字节、哈希与会话。
- `stage2-url-filename-before.png` / `stage2-url-filename-after.png`、`stage2-chinese-filename-before.png` / `stage2-chinese-filename-after.png`：命名修复前后。
- `stage2-url-text-opened.png`、`stage2-url-png-opened.png`、`stage2-large-saved.png`：系统打开与保存。
- `stage2-full-upload-500.png`、`stage2-full-upload-kept-500.png`、`stage2-full-upload-two-recovered.png`：上传失败、保留与恢复。
- `stage2-full-verification-before.json`、`steer-client-id-red.log`：修复前历史缺失及回归失败。
- `stage2-full-addition-fixed.png`、`stage2-native-ws.jsonl`、`stage2-full-verification.json`：修复后页面、协议及历史。
- 本机复验：`uv run python .tools/stage2_adb.py hashes`、`uv run python .tools/stage2_verify_full.py`；后者需要本轮隔离测试服务运行。

## 自动化与全量检查

- 聊天 hooks：35 个文件、**243 项通过**，包含新增 6 项上传 HTTP、实际输入框保留和排队测试。既有历史测试首次并发运行触发 5 秒超时，单文件复测通过，全组以 `--testTimeout=15000` 通过；未修改既有超时配置或断言。
- 新组件测试提供稳定的测试翻译器，避免全局替身每次渲染重建 `t` 导致循环；未修改产品翻译逻辑。
- 下载脚本：新增 2 项实际 HTTP 测试，连同原有分块、取消、磁盘错误及普通网页检查，共 **6 项通过**。
- 后端 WebSocket、追加协调器及附件相关测试：修复后 **80 项通过**，包含新增的 2 个消息 ID 场景。
- Android：**11 项单测通过**，lint **0 errors / 5 warnings**，debug APK 构建成功。
- TypeScript、新前端测试的 ESLint/Prettier、网页生产构建通过；网页保留既有分包体积提示。

全量检查记录：

1. 首轮 `make all`：Ruff、格式检查、mypy 通过；后端 **5103 passed / 24 failed / 224 skipped**。19 项涉及 Windows GBK 默认编码，另有历史子进程超时及打包脚本测试失败。启用本机 `PYTHONUTF8=1` 后，24 个失败节点独立重跑全部通过。
2. UTF-8 环境下第二轮 `make all`：**5125 passed / 1 failed / 223 skipped / 2 errors**，699.74 秒。失败与错误均发生在既有集成测试的登录初始化，返回 HTTP 401；三个节点独立重跑 **3 passed in 7.99s**。全量运行中的状态或时序原因尚未确认，没有据此记为全绿。
3. 追加历史修复后的最终 `make all` **通过，退出码 0**：Ruff、格式检查、mypy（615 个源文件）通过；后端 **5129 passed / 223 skipped / 18 warnings**，722.69 秒。警告包括浏览器安装测试的子进程输出解码警告及依赖弃用提示，没有失败或错误。

核心复现命令：

```powershell
uv run pytest tests/integration/test_chat_ws.py tests/unit/gateway/test_dashboard_ws.py tests/unit/gateway/steer tests/unit/gateway/test_attachment_hints.py -q
node --test android/app/src/test/js/downloads.test.cjs
```

```powershell
# dashboard/ 下
npx vitest run src/pages/Chat/hooks --maxWorkers=2 --testTimeout=15000
npx tsc -b
npm run build
```

本机完整门禁使用忽略目录内的 GNU Make 4.4.1：

```powershell
. .tools/env.ps1
.tools/make/bin/make.exe all 'SHELL=C:/Program Files/Git/bin/bash.exe' PYTEST_JOBS=4
```

日志包括 `stage2-chat-tests-final.log`、`android-url-filename-build-retry.log`、`steer-client-id-green.log`、`make-all.log`、`make-all-utf8.log`、`utf8-failure-recheck.log` 和 `make-all-final.log`。完整格式化引起的无关前端换行变动已还原。

## 环境限制及后续

用户明确授权模拟器安装和启动后，APK 安装及启动成功。自动审批仍拒绝含端口映射/强制重启，以及带调试地址参数的命令，均只返回 `blocked by policy`，没有具体原因。本轮通过正常连接页、模拟器主机地址 `10.0.2.2` 和同一隔离测试服务完成验收，不再等待这项设备授权。没有使用不可用的桌面交互浏览器连接。

产品改动仅有 Android 文件名解析和 Octop 续轮消息 ID；下载修复包含在上述 APK，历史修复需部署相应后端才会对其他服务生效。本轮未部署正式服务，未提交或推送。

本轮无真机补验未覆盖真实模型理解；该项随后已由同日[真机验收](android-stage2-steer-device-acceptance.md)补齐，阶段 2 已列核心验收完成。本轮全量门禁已通过；锁屏、网络切换、进程回收、下载生命周期、通知和签名升级兼容继续按阶段 6 验收。
