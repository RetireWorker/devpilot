# devpilot-project

项目注册中心。所有被 AI 研发流程处理的项目必须在此登记，Agent 不自行猜测仓库地址和构建命令。

## 数据模型

表 `dev_project` 保存项目元数据：技术栈、仓库类型（Git / Git-SVN）、构建/测试命令键、审批策略，以及 BUG 自动匹配所需的服务名、别名、日志包名、代码路径关键字。

列表字段以 JSON 文本保存，兼容 PostgreSQL 与 H2。

## 项目匹配评分

`ProjectMatcher` 是确定性匹配器，不调用大模型，便于测试和回溯。评分按证据类别汇总，每类只取最强的一条：

| 证据 | 分值 | 说明 |
|---|---|---|
| 身份证据 | 0.40 | 项目 ID、名称、别名、服务名任一命中，ID 优先 |
| 日志包名完整匹配 | 0.45 | 异常日志中出现完整包名 |
| 日志包名前缀匹配 | 0.35 | 至少前两段包名命中 |
| 代码路径关键字 | 0.20 | `src/main/java/...` 等路径片段命中 |
| 疑似模块关联 | 0.15 | 分析报告的疑似模块与项目元数据重合 |

总分截断到 1.0。阈值策略：

| 置信度 | 决策 |
|---|---|
| `>= 0.80` | `AUTO_MATCHED`，进入任务拆分 |
| `0.60 ~ 0.80` | `NEEDS_CONFIRMATION`，等待人工确认 |
| `< 0.60` | `UNMATCHED`，转人工分派 |

用户明确指定项目且项目已注册时直接返回 1.0；指定项目未注册时返回 `UNMATCHED`，不会回退猜测其他项目。纯 ASCII 关键字使用词边界匹配，避免 `handle` 命中 `handling`。

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/projects` | 注册项目 |
| `GET` | `/api/projects/{id}` | 查询项目 |
| `GET` | `/api/projects` | 列出项目，`includeInactive=true` 含停用项目 |
| `PUT` | `/api/projects/{id}` | 更新项目，不允许改 ID |
| `DELETE` | `/api/projects/{id}` | 停用项目（软删除） |

Git 项目必须提供 `repositoryUrl`；Git-SVN 项目必须提供 `svnUrl`。
