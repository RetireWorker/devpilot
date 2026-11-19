# Orca Executor 适配

中心只依赖 `AgentExecutor`：

```java
ExecutionSubmitResult submit(AgentExecutionRequest request);
ExecutionState getStatus(String executionId);
ExecutionLogPage getLogs(String executionId, String cursor);
AgentExecutionResult getResult(String executionId);
void cancel(String executionId);
ExecutorHealth health();
```

Gateway 是唯一执行命令和 Agent 的进程；中心只传相对工作区路径。

## 模式

| 模式 | 场景 | 配置 |
|---|---|---|
| `local` | Orca 与中心同机 | `ai.executor.mode=local` |
| `remote` | Orca 在独立 AI 节点 | `ai.executor.mode=remote` + endpoint/token |
| `routing` | 多节点按项目/负载路由 | `ai.executor.mode=routing` + node 列表 |

## Gateway 契约

```http
POST /api/executions
GET  /api/executions/{id}
GET  /api/executions/{id}/logs
GET  /api/executions/{id}/result
POST /api/executions/{id}/cancel
GET  /health
```

工作区路径由 Gateway 在 `ai.gateway.workspace-root` 下解析，中心只传相对路径。

## 真实 Orca 集成（已按 Orca 1.4.263 校准）

早期占位模板 `orca run --agent ... --task ...` 并不存在。Gateway 现在通过
`ai.gateway.agent-runner=orca` 驱动受监督 worker：

| 动作 | Orca CLI |
|---|---|
| 启动 | `orca orchestration worker-start --spec-file <spec> --worktree path:<ws> --agent <id> --task-title <title> --json` |
| 状态 | `orca orchestration worker-show --dispatch <id> --json`（`state=succeeded/failed/stopped` 为终态） |
| 日志 | `orca orchestration worker-read --dispatch <id> --cursor <c> --json` |
| 取消 | `orca orchestration worker-stop --dispatch <id>` |
| 释放 | `orca orchestration worker-release --dispatch <id>` |

关键事实：

- **Orca 只允许在它认识的工作区里启动 Agent。** devpilot 创建的 git worktree
  对 Orca 是未知目录，直接 `--worktree path:` 会报 `selector_not_found`。
  Gateway 会先用 `orca repo add --kind folder --path <ws>` 注册，再启动；
  任务结束后按 `hide-workspace-when-done` 隐藏侧边栏条目。
- 注册父目录不能替代注册具体工作区：Orca 会把子目录解析到父目录，
  Agent 会跑在错误目录。
- 同一仓库的多个任务工作区并存时，`path:` 选择器可能命中错误条目并报
  `selector_ambiguous`；Gateway 用 `worktree current --json` 取精确
  `id:<repo>::<path>` 后再启动。
- 构建产物（`target/`、`build/`、`node_modules/` 等）在准备工作区时写入
  worktree 的 `info/exclude`，避免测试产物进入 Agent 提交或变更审查。
- `worker-start` 返回 `dispatchId`，不等待任务完成；Gateway 轮询
  `worker-show`，并用 `worker-read` 的游标增量落盘日志。
- 结构化 chat worker 在完成一轮后可能仍保持 `ready` 等待下一条输入。
  Gateway 在「活动状态为 idle、无待处理确认、且已出现结构化 JSON 终稿」
  持续 `worker-idle-settle`（默认 20s）后判定完成，并主动 stop 该 worker。
- 任务说明通过 `--spec-file` 传入。Gateway 从 `AgentExecutionRequest`
  生成自包含 spec：目标、允许/禁止路径、禁止提交/推送、以及最终输出
  必须包含的 JSON 块。任务文本永远不会进入命令行。
- Agent 的自我声明不被信任：变更文件来自 `git status` + `git diff`，
  越界路径直接判定失败；提交由 Gateway 完成（`auto-commit=true`）。
- Reviewer 的结构化结论从最终回复的 ```json 块提取，写入
  `structuredOutput.review`，供中心的确定性审查策略使用。
  Orca 的 transcript 单条消息约 1.2KB 会截断，因此 spec 要求 JSON 块放在
  终稿最前面并控制在 500 字符内。

### 配置

```yaml
ai:
  gateway:
    enabled: true
    workspace-root: /data/workspaces
    state-dir: /data/gateway
    orca-binary: orca
    agent-runner: orca
    default-agent-id: claude
    worker-model: ""            # 留空使用该 Agent 在 Orca 中的默认模型
    agent-ids:
      CODE_REVIEWER: claude
    worker-poll-interval: 5s
    worker-idle-settle: 20s
    max-concurrent-workers: 4
    register-workspace: true
    hide-workspace-when-done: true
    auto-commit: true
    commit-author-name: devpilot-agent
    commit-author-email: devpilot@localhost
```

`default-agent-id` 取自 `orca agent installed`（例如 `claude`、`codex`）。
如果该 Agent 在 Orca 中的默认模型不可用（例如宿主配置了它不支持的模型），
必须显式设置 `worker-model`。

中心侧：

```yaml
ai:
  executor:
    mode: local
    local:
      enabled: true
      endpoint: http://127.0.0.1:18080
      token: ${ORCA_GATEWAY_TOKEN}
```

中心与 Gateway 必须使用**同一个绝对工作区根目录**，否则相对路径解析不一致。

## 前置条件

- Gateway 主机可运行 `orca` CLI，且 Orca 运行时（app 或 `orca serve`）可达。
- 目标 Agent 已安装并在 Orca 中可用（`orca agent installed`）。
- 容器内运行 Gateway 只能走命令执行；Agent 执行需要宿主的 Orca 运行时，
  因此生产上把 Gateway 部署在 Orca 节点上。