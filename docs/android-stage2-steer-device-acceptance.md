# Android 阶段 2：真实模型追加信息与历史恢复验收

2026-10-31，USB 真机操作由助手按用户授权执行。设备为 vivo V2502DA、Android 16 / API 36，WebView 为 `138.0.7204.179`，沿用手机已有的 `0.1.3-debug` / versionCode 4。工作目录为家用电脑上的 `D:\Coding\Octop`，分支为 `feature/android-client`，基线为 `d8859bb8`，包含此前尚未提交的续轮消息 ID 修复。

## 结论

生成中排队自动续发、直接 Steer、真实模型理解追加内容、消息去重和 App 重启后的历史恢复均通过。结合此前的文字、附件、下载、审批、后台恢复及无真机补验，阶段 2 已列出的核心验收范围完成；阶段 3 尚未开始。锁屏、网络切换、进程回收、通知及签名升级兼容继续按阶段 6 验收。

## 本机环境重新配置

单位电脑的 `.tools/octop-data`、测试账号和模型配置没有随 Git 克隆迁移。本轮经用户确认重新配置家用电脑的独立实例：

- 服务地址：`http://127.0.0.1:18088`，仅监听本机；USB 映射限定真机序列号 `10AG3E0AZU004PQ`。
- 数据目录：`.tools/octop-data`。测试账号保存在忽略文件 `.tools/dev-account.json`，未输出密码或手机登录令牌。
- 完成 SQLite 初始化、独立管理员创建、手机滑块验证和密码登录；网页响应与当前构建的 `src/octop/dashboard/index.html` 哈希一致。
- 用户在手机设置页填写真实模型凭据；通用助手使用 `Zhipu Coding Plan (BigModel)/glm-5.3-flash`。本轮没有访问正式服务或使用正式账号。

首轮真实请求被工作区根目录配置拦截：专家工作区在 D 盘，`backend.root_dir` 为 `C:/`。通过本机认证 API 仅将该值改为 `D:/Coding/Octop/.tools/octop-data/agents/main`，保留其余配置，重载返回 204，运行状态为 `running`。后续真实回复正常。此问题与此前单位电脑的配置问题相同，本轮没有新增产品源码修改。

手机已有 APK 与家用电脑 debug APK 的签名不同，覆盖安装返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。没有卸载或清除原 App 数据；本次聊天验收沿用现有容器，加载本机网页与后端。普通 URL 文件名修复的最新原生 APK 仍以上一轮模拟器验收为依据，不能将本轮记作该 APK 的真机覆盖安装通过。

## 真机验收

首条消息设置数量 17、单价 9，并要求生成较长正文，为追加操作留下可观察的时间窗口。追加指令只把数量改为 23，要求沿用原单价并返回总额，没有在追加指令中给出单价或计算结果。

| 检查 | 结果 |
| --- | --- |
| 生成中输入并排队 | 通过：点击排队前，后端 `turn_active=true`；截图中有排队消息，输入框清空 |
| 不点 Steer，等待自动续发 | 通过：原回复结束后排队消息正常发送，真实模型返回数量 23、单价 9、总额 207；历史恰有两条用户消息 |
| 直接点击 Steer | 通过：新会话中，开始生成约 3 秒后排队并点击实际纠正按钮；点击前再次确认后端仍为 active |
| 理解原始上下文与追加修改 | 通过：原回复保留 `INITIAL_DONE_7316`，续轮返回 `{"tag":"PHONE_STEER_4289","quantity":23,"unit_price":9,"total":207}` |
| 追加消息历史与去重 | 通过：首条和追加各保留一次，两个用户消息 ID 不同，结构化答案出现一次 |
| App 重启与历史恢复 | 通过：结束后重启 App，登录保留，重新打开验收会话；前后历史消息数组完全一致，追加及答案仍在 |
| 最终状态 | 通过：后端 `turn_active=false`；真机显示同一 JSON 答案，输入框为空，显示“发送”，没有“停止”按钮 |

Steer 会话为 `thr_01M68EY9049YPM80VGC2EPQ04D`，已命名为 `S2 Phone Steer 7316`，保留在本机供复核。首条用户消息 ID 为 `1793433000566-3t932mmw2`，追加消息 ID 为 `01M68EZR6HH7ZP13GFVWZQ3VXT`，验证了真实网页带客户端 ID 时续轮仍能独立保存。自动续发会话为 `thr_01M68ETFXHYXMJQ86RGY1SM9AT`。

早期驱动遇到回合刚创建时状态尚未变为 active，以及键盘动画改变输入框位置的问题；未通过的尝试没有记入上述结果。最终驱动在输入前核对系统输入法状态，使用实际截图确认的按钮位置，并在排队与 Steer 前查询后端活动状态。历史断言核对实际收到的完整指令，不依赖生成期间可能滞后的可访问性文本。

## 复核命令与证据

本机启动命令：

```powershell
. .tools/env.ps1
$env:OCTOP_HOME = Join-Path (Get-Location) '.tools/octop-data'
uv run --locked octop run --host 127.0.0.1 --port 18088
```

已执行并通过的验收命令：

```powershell
uv run python .tools/stage2_real_flow.py
uv run python .tools/stage2_real_check.py verify thr_01M68EY9049YPM80VGC2EPQ04D
uv run python .tools/stage2_phone.py verify-idle
```

驱动绑定本轮真机与截图坐标；再次执行会发送真实模型请求。验收记录及脚本保存在忽略目录 `.tools/`：

- `stage2-real-preflight.json`、`stage2-real-root-fix.json`：环境及根目录修正。
- `stage2-real-native-events.json`：实际按钮操作与两次 active 检查，最终驱动退出码为 0。
- `stage2-real-auto-queue-verification.json`、`stage2-real-auto-queue-history.json`：自动续发。
- `stage2-real-verification.json`、`stage2-real-history.json`：Steer 后的真实模型答案与两条用户消息。
- `stage2-real-history-before-reopen.json`、`stage2-real-reopen-verification.json`、`stage2-real-ui-reopened.json`：重启前后内容一致及最终界面断言。
- `stage2-phone-addition-queued.png`、`stage2-phone-addition-sent.png`、`stage2-phone-addition-finished.png`、`stage2-phone-history-reopened.png`：排队、投递、完成与重开截图。

本轮只配置本机实例、执行真机验收并更新文档，未新增产品源码修改。沿用上一轮最终 `make all` 的 **5129 passed / 223 skipped**，前端专项 **243 项**、Android 单测 **11 项**及构建通过的结果；本轮没有重复运行或声称新跑过全量检查。`git diff --check` 在文档收尾后核对。未提交、推送或部署正式服务。
