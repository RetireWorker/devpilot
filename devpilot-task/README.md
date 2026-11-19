# devpilot-task

任务聚合、状态机、计划拆分、执行事件、测试/审查记录与审批。

## 状态机

`TaskStateMachine` 是唯一的状态写入口。所有迁移必须存在于
`devpilot-common` 的 `TaskTransition` 表中，非法迁移返回 409。

外部触发的迁移必须携带 `idempotencyKey`：重复事件不会追加第二条记录，而是
直接返回当前状态。这用于防止回调重放造成重复执行。

## 自动修复预算

`RepairLoopPolicy` 限制测试/审查失败后的自动修复轮次（默认 2 轮）。
超出预算后任务进入 `FAILED_NEEDS_HUMAN`，不会无限循环。

## 端口（由 center 提供实现）

| 端口 | 用途 |
|---|---|
| `TaskPlanPort` | 调用 AI 模块生成子任务计划 |
| `TaskWorkspacePort` | 调用仓库模块创建 worktree |
| `ProjectLookupPort` | 读取项目注册信息 |

任务模块不直接依赖 `devpilot-ai`、`devpilot-repository`、`devpilot-project` 的
实现，避免模块间循环依赖。

## 第一阶段交付策略

`ai.task.delivery-enabled` 默认 `false`。审批接口会记录审批意见，但不会执行
`git push` / `git svn dcommit`，并明确返回"未启用"。开启后由 center 的交付
Worker 执行。
