# Android 功能覆盖与验收矩阵

记录更新：2026-10-31。基线：`44c22434b090cb78d3a4a2d6fad786e7bbc95863`。

本表依据实际路由、导航及权限代码建立。状态“待验”表示尚未完成 Android 端实测；网页存在该功能不代表 Android 已通过。后续在“结果”列记录设备、操作结果和证据。

权限沿用后端及网页现有规则：管理员通常绕过模块权限，普通用户按分配权限访问；具体判定见 [permissions.ts](../dashboard/src/utils/permissions.ts)。隐藏入口不替代后端权限校验。

| ID | 功能与主要操作 | 现有入口 | 权限/前置条件 | Android 阶段 | 结果 |
| --- | --- | --- | --- | --- | --- |
| A01 | 首次设置、数据库、初始管理员、模型设置 | `/setup` | 未初始化实例；向导密码按实例配置 | 1 | 待验 |
| A02 | 密码登录、验证码、保持登录、退出 | `/login` | 账号；验证码按部署配置 | 1 | 部分通过：API 35 本机密码登录、触摸滑块、刷新后保持登录；退出待验 |
| A03 | SSO/OIDC 登录及回调、邀请链接 | `/login/oidc/complete`、`/invite` | 对应身份提供方或邀请 | 1/4 | 部分通过：2026-10-30 用户确认正式服务 SSO 与二步验证后成功返回 Octop 首页；Octop 内置 OAuth 和邀请链接待验 |
| A04 | 个人资料、语言、主题与界面偏好 | 页头/用户菜单 | 已登录 | 3 | 部分通过：0.1.2 API 35 验证系统栏随页面深浅色切换，含刷新及前后台恢复；vivo Android 16 / API 36 真机连续四次主题切换、深浅色各一次后台返回通过，测试后恢复原偏好；资料、语言及其余偏好待验 |
| C01 | 专家切换、新建对话、历史、流式回复 | `/chat`、`/chat/:agentId`、`/chat/:agentId/:threadId` | 用户所属或可访问专家；模型用于实际推理 | 2 | 通过：0.1.2 API 36 真机在正式服务使用通用助手与 glm-5.3-flash 完成真实文字回复，流式文字记录 28 种中间长度；专家切换、历史重开/刷新及重启 App 后恢复通过，见[记录](android-stage2-chat-acceptance.md)；2026-10-31 用户补验本机实例真实文字回复通过 |
| C02 | 停止生成、进行中追加信息、失败恢复 | 聊天页 | 运行中的对话 | 2 | 部分通过：API 36 真机在生成部分正文后停止，正文停止增长；随后同会话发送并收到真实回复；进行中追加信息与失败恢复待验 |
| C03 | 工具审批的允许、拒绝与继续 | 聊天页审批卡片 | 能触发审批的工具与后端环境 | 2 | 通过：0.1.3 API 36 真机允许写入、拒绝且不换工具、待审批刷新保留同一请求和单张卡片、拒绝后再次执行并批准，共 4 项；审批前后文件与后端状态均已核对，原全局设置已恢复，见[验收记录](android-stage2-approval-recovery-acceptance.md) |
| C04 | 图片/文件附件、生成文件预览与下载 | 聊天附件与工作区文件入口 | Android 文件选择及系统打开 | 2 | 部分通过：选择器、重复/多选、附件预览与移除通过；路径问题修正后，单/双文本附件读取及历史保留 3 项通过；2026-10-31 用户补验上传超限提示、超限后恢复和中文空格文件名保留与读取 3 项通过；单图放大关闭、多图切换、图文混合附件分别移除及草稿保留 3 项通过；启用 glm-5v-turbo、单/双图发送识别、历史重开后图片和回复保留及放大查看 4 项通过；USB 链路中断后上传失败提示、草稿保留、原页面恢复上传与读取通过；助手在 0.1.3 真机完成工作区文本、中文文件名、PNG、PDF 下载与系统打开，以及约 2 MiB 文件完整性检查。经批准生成的文件预览、聊天回复链接下载、系统打开及历史分页恢复通过，见[专项记录](android-stage2-approval-recovery-acceptance.md)。服务端上传 HTTP 错误及普通 URL 下载监听待验 |
| C05 | 语音输入/输出、富文本与 Office/PDF 预览 | 聊天页及相关预览器 | 对应语音服务、文件与权限 | 4 | 待验 |
| E01 | 专家库、专家市场、创建/编辑/启动/停止专家 | `/experts` | `experts`；部分操作需所属专家 | 4 | 待验 |
| E02 | 专家共享、团队协作及多专家任务 | 专家/团队相关入口 | 已配置专家与 AgentTeams；Beta 能力按原版行为 | 4 | 待验 |
| E03 | 专家配置、模型、工作区及文件操作 | `/agent-config`；专家配置抽屉 | 所属专家及相应配置权限 | 4 | 待验 |
| E04 | 个性化、系统提示词与 MBTI | `/personalization/mbti` | `mbti`；所属专家 | 4 | 待验 |
| E05 | 记忆查看与管理 | `/personalization/memory` | `memory`；所属专家 | 4 | 待验 |
| E06 | 技能启停、内置技能及市场 | `/personalization/skills` | `skills`/`skill_builtin`/`skill_market` | 4 | 待验 |
| E07 | 子智能体管理 | `/personalization/subagents` | `subagents` | 4 | 待验 |
| E08 | 工具配置、专家插件配置 | `/personalization/tools`、`/personalization/plugins` | `tools`、`agent_plugins` | 4 | 待验 |
| T01 | 定时任务创建、编辑、启停、执行与记录 | `/tasks` | `tasks`；可用专家/模型用于执行 | 4 | 待验 |
| T02 | 用量、统计与时间显示 | `/token-usage` | `token_usage`；遵循服务端时区 | 4 | 待验 |
| K01 | 知识库创建、共享、文档上传、检索与设置 | `/knowledge-bases` | `knowledge_bases`/`knowledge_settings`；Embedding/RAG 环境 | 4 | 待验 |
| K02 | 技能包创建、共享、复制与下载 | `/skill-packages` | `skill_packages`；原有共享/复制规则 | 4 | 待验 |
| X01 | 连接器、内置/自定义接入与 OAuth | `/connectors` | `connector_enabled`/`connector_builtin`/`connector_custom`；外部服务 | 4 | 待验 |
| X02 | IM 通道创建、配置与验证 | `/personalization/channels` | `channels`；相应 IM 账号与凭据 | 4 | 待验 |
| X03 | 跨实例桥接、专家浏览和对话 | `/bridge` | `bridge`；第二个可连接实例 | 4 | 待验 |
| X04 | ACP Runner 配置与委派 | `/acp` | 原页面管理员权限；后端安装对应 Runner | 4 | 待验 |
| R01 | 交互终端、会话、输入、快捷键、复制粘贴 | `/workbench/terminal` | `terminal`；后端终端环境 | 5 | 待验 |
| R02 | 远程浏览器画面、点击、滚动、输入和 AI 操作 | `/workbench/browser` | `browser`；后端 Chromium/CDP | 5 | 待验 |
| R03 | 远程桌面画面、鼠标/键盘、横屏与 AI 操作 | `/remote-desktop/desktop` | `desktop`；后端桌面会话 | 5 | 待验 |
| R04 | 远程 Android 屏幕、输入与 ADB Shell | `/remote-desktop/phone/screen`、`/remote-desktop/phone/shell` | `mobile`；服务器端 ADB 与设备支持 | 5 | 待验 |
| M01 | 用户、角色、组及外部身份配置 | `/admin/users` | `users`/`user_roles`/`user_groups`/各 SSO 权限 | 4 | 待验 |
| M02 | 模型、提供方、Ollama、Embedding、语音与检索 | `/admin/models` | `providers`/`ollama_models`/`onnx_models`/`voice` 等 | 4 | 待验 |
| M03 | 工作区存储后端 | `/admin/backend` | `storage_backends`；按存储类型配置环境 | 4 | 待验 |
| M04 | 全局插件安装、市场与管理 | `/admin/plugins` | `plugins`/`plugin_market` | 4 | 待验 |
| M05 | 工具审批策略、文件权限、PII、工具防护、扫描与审计 | `/admin/security` | 对应 `security_*`/`admin_console` 权限 | 4 | 待验 |
| M06 | 环境变量、观测、备份、HTTPS、更新与验证码 | `/admin/advanced` | `envs`/`observability`/`backup`/`tls`/`update`/`captcha` | 4 | 待验 |
| M07 | PWA 诊断 | `/pwa-debug` | 已登录；浏览器/WebView 支持情况 | 4 | 待验 |
| N01 | 服务地址设置、保存、修改及连接重试 | Android 连接页 | Android 客户端新增能力 | 1 | 通过：API 35 地址修改、保存、APK 更新后保留；本地延迟服务触发 30 秒超时，恢复后重试成功 |
| N02 | 系统返回、外部链接及页面状态 | Android 容器 | 正常导航、外部应用支持 | 1 | 部分通过：专家/应用设置导航、Android 返回；外链及复杂恢复待验 |
| N03 | 文件选择、下载、系统打开与分享 | Android 容器 | 文件类型及系统处理器 | 2/6 | 部分通过：系统选择器取消、重新打开、选中文本后网页读取；用户确认回形针可打开选择器、Tooltip 已移除、小型文件附件预览正常；2026-10-31 聊天内选择器取消、同一文件重新选择、多选与分别移除通过，单/多图片预览和图文混合选择、移除及草稿保留通过；0.1.3 助手真机验收系统另存为取消后重试、文本/中文空格文件名/PNG/PDF 保存与系统打开、约 2 MiB 分块文件哈希通过；另存为改名后的提示和通用 MIME 识别已修正并回归。普通 URL 下载监听和更多生命周期场景待验；分享待实现 |
| N04 | 网络切换、锁屏、后台与进程重建 | Android 容器 | 后端任务仍独立运行 | 6 | 部分通过：API 36 真机主题和已完成聊天后台返回、App 重开后登录及历史保留正常；0.1.3 在运行中分别后台 6 秒和 70 秒各 5 项通过，长后台断流后正文不完整的问题已修正并真机回归，完成状态一致且没有重发，见[验收记录](android-stage2-approval-recovery-acceptance.md)。网络切换、完整锁屏恢复和系统进程回收重建待验 |
| N05 | 完成/待审批通知 | Android + 后端事件渠道 | 需明确推送部署方案 | 6 | 待验 |
| N06 | APK 安装、升级与版本兼容 | Android 构建产物 | 正式签名、目标系统及服务版本 | 6 | 部分通过：API 36 真机覆盖安装 0.1.2-debug 成功，正式服务原登录状态保留；2026-10-31 助手覆盖安装 0.1.3-debug 并保留本机登录和历史；正式签名和其余系统/服务版本兼容待验 |

## 原路由及兼容入口

所有原路由继续由 React Router 处理，Android 不另建一套功能路由。别名应到达相同目标，以下分类纳入导航验收：

- 对话：`/`、`/sessions`。
- 工作区旧入口：`/workspace`。
- 工作台：`/workbench`、`/terminal`、`/remote-browser`。
- 个性化：`/personalization/*`、`/personalization/acp`、`/skills`、`/subagents`、`/mbti`、`/memory`、`/channels`。
- 手机远控：`/remote-desktop`、`/remote-desktop/phone`、`/remote-phone`、`/remote-android`。
- 管理兼容：`/admin/sso`、`/admin/shared-models`、`/models`、`/admin/audit`、`/admin/agents`、`/admin/voice`、`/admin/updates`、`/admin/storage`。
- 设置兼容：`/advanced-settings`、`/environments`、`/updates`、`/plugins`、`/cron-jobs`。
- 历史前缀：`/orca/cron`、`/orca/channels`、`/orca/admin/users`、`/orca/admin/audit`、`/octop/cron`、`/octop/channels`、`/octop/admin/users`、`/octop/admin/audit`。
- 未知路径：`*` 的未找到页面。

功能清单包含后端集成能力，不意味着将 Python、CLI、Docker、Chromium 或 ACP 运行时搬进手机。依赖外部服务的条目必须列明验证条件，不能用模拟结果替代实际集成验收。

## 验收记录方式

每条记录填写：设备与系统版本、App 构建版本、服务提交、用户角色、操作步骤、预期结果、实际结果、截图/日志、遗留问题。统一使用“待验 / 通过 / 未通过 / 缺少外部条件”状态。

已完成的 API 35 本机设备验证写入对应行；其余仍待验，不是全功能完成声明。阶段 1 的正式服务 SSO/二步验证已由用户确认成功返回 Octop 首页。过程及限制见 [环境与基线](android-development-baseline.md)。

## 依据

- [页面路由](../dashboard/src/routes/index.tsx)
- [登录、向导与邀请路由](../dashboard/src/App.tsx)
- [导航项目](../dashboard/src/layouts/sidebarNav.tsx)
- [权限及子页面权限](../dashboard/src/utils/permissions.ts)
- [API](api.md)
- [开发 Plan](android-client-plan.md)
