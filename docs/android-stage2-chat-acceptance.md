# Android 阶段 2：文字对话真机验收

日期：2026-10-30（Asia/Shanghai）。本批为阶段 2 的第一小步，12 项检查通过，尚不代表整个阶段 2 完成。

## 环境与范围

- 手机：USB 连接的 vivo V2502DA，Android 16 / API 36，1260×2800。
- 客户端：现有 `0.1.2-debug` / versionCode 3，未修改产品代码或重新构建 APK。
- 正式服务：`https://octop.mookeview.cn:8880`，网页版本显示 `v1.0.3b1`；沿用手机已有登录会话。
- 测试专家：“小通 · 通用助手”；页面显示所选模型为 `Zhipu Coding Plan (BigModel) / glm-5.3-flash`，实际完成模型回复。
- 在独立测试会话中发送四轮纯文本验收消息，使用 Ask 模式，不在既有业务会话追加消息。没有主动调用连接器或执行文件操作。

## 验收结果

| 检查 | 结果与证据 |
| --- | --- |
| 软键盘与发送按钮 | 通过：页面高度由 742 CSS px 缩至 441 CSS px，输入框和发送按钮均完整可见；有真机截图 |
| 中文输入 | 通过：输入完整中文测试文本，网页输入值与预期一致 |
| 新建会话及真实回复 | 通过：创建接口返回 HTTP 201；用户消息与实际模型回复显示，收到约定的 `A2_CHAT_OK` 标记 |
| 流式文字显示 | 通过：收到完整的 24 项测试文本及 `A2_STREAM_OK`；生成期间记录到 28 种不同的中间文本长度 |
| 停止生成 | 通过：已出现部分正文时点击停止，停止按钮消失，发送按钮恢复；正文在随后两次检查中保持 154 字符，没有完成全部长回复 |
| 停止后继续发送 | 通过：同一会话继续发送，收到 `A2_RESUME_OK` |
| 从历史列表重新打开 | 通过：先新建空白会话，再从历史列表点回测试会话，四条用户消息、四组助手回复及测试标记恢复 |
| 刷新后的历史 | 通过：重新加载当前网页后，消息数量和测试标记保持一致 |
| 切到桌面再返回 | 通过：当前测试会话和已完成消息保留 |
| 专家切换 | 通过：切换至健身教练，再切回通用助手，正确恢复测试会话 |
| 消息列表触摸滚动 | 通过：真实上下滑动后，列表位置先由约 1890 降至 1623，再回到 1887 CSS px |
| 重新启动 App | 通过：结束应用进程后重新打开，登录保留；通过专家入口重新打开测试会话，四轮消息恢复。此项不要求自动回到上次路由 |

首轮自动化脚本在发送后立即触发 Android 返回，离开待创建的会话，该轮没有用于判定业务通过或产品故障。去除这一额外操作后，创建会话和真实回复通过；后续操作均等待会话状态稳定再执行。

测试期间临时开启 USB 充电保持屏幕亮起，结束后已恢复原值 `0`。临时 ADB 调试转发已移除；重新启动 App 清理了调试命令产生的重复 Activity，最后只保留一个 WebView 页面。手机保留测试会话，方便人工复核。

## 可追溯记录

所有调试脚本、设备记录和截图均位于忽略的 `.tools/`，不随源码提交：

- 汇总：`stage2-text-acceptance-result.json`，12 项全部通过。
- 分项：`stage2-send-probe.json`、`stage2-chat-result.json`、`stage2-stop-result.json`、`stage2-history-result.json`、`stage2-navigation-result.json`、`stage2-cold-open-result.json`。
- 驱动：`stage2_device.py`；按准备状态执行的 `stage2_select.py`、`stage2_send_probe.py`、`stage2_chat.py`、`stage2_stop.py`、`stage2_history.py`、`stage2_navigation.py`、`stage2_cold_open.py`。脚本限定当前可见的正式服务 WebView 和专用测试会话。
- 截图：`screenshots/stage2-keyboard.png`、`stage2-text-reply.png`、`stage2-stopped.png`、`stage2-history-reopened.png`、`stage2-chat-restored.png`、`stage2-scroll-older.png`、`stage2-cold-open.png`。

网络诊断仅保留请求路径、方法和状态码，不导出认证请求头、密码或令牌。应用进程的 AndroidRuntime 日志未发现 `FATAL EXCEPTION`。本批仅新增验收文档与本机调试记录，没有产品源码变更，未重复运行 Gradle 构建或全栈基线检查。

## 后续小步

先验证真实聊天中的文本、图片附件上传、取消和多选，再实现并验收文件下载及系统打开。工具审批、错误恢复、生成中追加信息，以及回复期间切到后台的任务状态核对仍待后续批次；完整网络切换、锁屏与进程重建加固属于阶段 6。
