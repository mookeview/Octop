# Android 阶段 2：审批、后台恢复与文件交付验收

2026-10-31，全部 USB 与手机操作由助手按用户授权执行。设备为 vivo V2502DA、Android 16 / API 36，客户端为 `0.1.3-debug` / versionCode 4；连接 USB 映射的本机独立实例 `http://127.0.0.1:18088`，使用通用助手与已有 `Zhipu Coding Plan (BigModel)/glm-5.3-flash`，测试会话为 `thr_01M67XP5Z5P2JGKDGHD3WVEFYS`。没有向正式服务写入测试数据。

## 工具审批

仅在本机独立实例临时开启 `write_file` 审批，使用 `outbound/android-A2H_1793414738-*.txt` 专用文件。审批前先核对后端 pending 请求的工具、路径和内容，再用 ADB 点击实际卡片。

| 检查 | 结果 |
| --- | --- |
| 允许执行 | 通过：批准前文件 404，批准后 200，内容与唯一标记一致，回复 DONE |
| 拒绝执行 | 通过：文件始终 404，回复 REJECTED，没有换工具执行 |
| 待审批时刷新页面 | 通过：pending ID `ffd1` 保持一致，只有一张可操作卡片，文件仍为 404 |
| 拒绝后再次执行并批准 | 通过：新文件正确写入，回复 DONE，无残留审批或运行状态 |

原 HITL 配置已恢复并对比一致：`enabled=false`、`tools="default"`、`allowed_decisions=["approve","reject"]`。

## 运行中切到后台再恢复

发送 120 行纯文本及唯一结束标记，在手机已显示部分正文且后端仍执行时按 HOME。分别后台停留 6 秒和 70 秒，再返回同一会话；期间通过独立本机账号查询后端状态，没有把手机令牌导出。

修复前，70 秒流程稳定失败：后端已完整保存且空闲，页面只显示部分正文，停止按钮也已消失。连接记录显示原 socket 关闭，重新订阅得到 `turn_status.active=false`；客户端封存了部分回复，却没有重新加载完整历史。页面前台事件当时也只更新角标。

修复归属本仓库 `dashboard/`：

- `chatStore.ts` 在意外断线重连后的结束事件中标记历史可能缺失，覆盖重连时服务端仍执行和已经结束两种情况。
- `useChatNavigation.ts` 在回到前台或上述恢复结束后使缓存失效，并复用现有历史合并逻辑。仍在生成时等待结束，待审批卡片和仍在流式输出的团队消息保留；普通连续回复结束不额外刷新历史。
- 没有修改 Android 生命周期或运行时库。本机网页构建后已在手机刷新，最终在线入口为 `/assets/index.Bd2gb9Re.js`，无需重新安装 APK。

| 检查 | 后台 6 秒 | 后台 70 秒 |
| --- | --- | --- |
| 部分正文生成中切到 HOME | 通过 | 通过 |
| 后端在后台继续执行 | 通过 | 通过 |
| 返回同一会话且显示完整结束标记 | 通过 | 通过 |
| 用户消息只新增一次 | 通过 | 通过 |
| 完成后前后端空闲且无待审批 | 通过 | 通过 |

70 秒测试中，后端约在后台第 25 秒结束；返回后客户端自动补齐正文，无需手动刷新或重发消息。此结果不代表锁屏、网络切换、系统回收进程或后台通知已经验收。

## 文件交付与历史

使用上面批准生成的 `outbound/android-A2H_1793414738-recover.txt`，继续让真实模型返回现有鉴权下载链接；该轮没有再次调用工具。

| 检查 | 结果 |
| --- | --- |
| 点击模型回复中的下载链接 | 通过：出现 Android 系统保存框，文件名正确 |
| 保存内容与生成结果一致 | 通过：22 字节，正文 `A2H_1793414738_RECOVER` |
| 系统应用打开 | 通过：vivo 文档实际显示上述正文，截图已目视核对 |
| 重开历史 | 通过：下载链接保留，服务端消息 ID 未改变；加载更早记录后 10 条用户消息全部匹配，无重复 |
| 工作区预览生成文件 | 通过：`outbound` 中打开文件，正文标记和下载入口正常 |

文件 SHA-256：`1d045112677f0ecb95c1d6e66c9ad8e442006f7637c2dfa4084b74ede88343d3`。

历史检查最初直接比较重开前后的 DOM 消息总数而失败。实际首屏按 `CHAT_HISTORY_PAGE_SIZE=25` 分页，27 条原始历史记录尚有更早一页；加载后全部匹配。这是验收脚本假设错误，没有因此修改产品分页逻辑。长后台驱动也曾因 CDP 默认 keepalive 超时退出；停用调试通道自动 ping 后，重新完整执行了修复前与修复后测试，没有将驱动错误记为产品通过。

## 自动检查与证据

- `useChatRecovery.test.tsx` 先运行出缺少正文同步的红例，再加入最小修复。最终 7 项覆盖前台恢复、重连时 active/idle、等待流结束、保留审批、普通结束不刷新和新会话首轮结束更新列表。
- `cd dashboard`，设置 `NODE_OPTIONS=--no-experimental-webstorage`，执行 `npx --no-install vitest run src/pages/Chat/hooks`：34 个文件、237 项通过，包含历史、HITL、团队流和 WebSocket 恢复检查。已有历史测试保留 React `act(...)` 警告。
- `npx --no-install tsc -b`、`useChatNavigation.ts` 与新测试的 ESLint、三个相关文件的 Prettier 检查通过。
- 对 `chatStore.ts` 的完整 ESLint 检查仍报第 1486 行既有 `no-extra-boolean-cast`；已与 HEAD 比对，该行不在本次修改中。本轮没有扩展修改该处。
- `npm run build`：TypeScript、Vite、PWA 通过，保留既有分包体积提示。没有修改后端或 Android 源码；沿用此前后端非 live 全量 `5127 passed, 223 skipped` 和 Android 11 项单测及 lint/构建结果。本机没有 `make`，未运行或宣称通过 `make all`。

本机证据在忽略目录 `.tools/`：

- `stage2-hitl-acceptance-result.json`、`stage2-hitl-original.json`。
- `stage2-background-long-before.json` 与 `stage2-background-{long,short}-after.json`；原流程命令为 `uv run python .tools/stage2_background.py 70` 和 `uv run python .tools/stage2_background.py 6`。
- `stage2-delivery-result.json`；截图 `screenshots/stage2-hitl-*.png`、`stage2-background-long-resumed.png`、`stage2-generated-file-{open,preview}.png`。
- `stage2-final-build.json` 与 `stage2-final-state.json`：最终构建已加载，后端空闲、无待审批、原配置一致、无调试 wrapper，19223 调试转发移除而 18088 映射保留。

尚待补验：服务端上传 HTTP 错误、普通 URL 下载监听、生成期间追加信息，以及完整生命周期场景。阶段 3 导航与视觉改造尚未开始。
