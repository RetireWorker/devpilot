# 首期闭环

```text
POST /api/bugs
  -> POST /api/bugs/{id}/analyze        BUG_ANALYZING -> PROJECT_MATCHING
  -> POST /api/bugs/{id}/match-project  -> PLANNING / WAITING_PROJECT_CONFIRM
  -> POST /api/intake/bugs/{id}         分析 + 匹配 + 建任务（推荐入口）
  -> POST /api/tasks/{id}/plan          PLAN_CREATED -> WAITING_CONFIRM
  -> POST /api/tasks/{id}/approve-plan  -> READY
  -> POST /api/tasks/{id}/prepare-workspace -> PREPARING_WORKSPACE
  -> POST /api/tasks/{id}/execute       -> DEVELOPING
  -> 开发结果 -> CODE_COMMITTED -> TESTING
  -> 测试通过 -> REVIEWING
  -> 审查通过 -> WAITING_APPROVAL
  -> 生成变更报告，等待人工审批
```

低置信匹配（0.60~0.80）进入 `WAITING_PROJECT_CONFIRM`，需调用
`POST /api/intake/tasks/{id}/confirm-project?projectId=...` 后才生成计划。

## 失败回路

- 测试失败：回到 `DEVELOPING`，`repairRound` 加一；超过 `ai.task.max-repair-rounds`
  （默认 2）后进入 `FAILED_NEEDS_HUMAN`。
- 审查失败：回到 `DEVELOPING` 并重新测试。
- 禁止通过删除测试、跳过测试、弱化断言或吞异常通过。
- 每轮保留失败日志、差异、Agent 说明与退出码。

## 幂等

- BUG 创建：同项目同标题 24 小时内拒绝重复。
- 任务事件：同一事件 + 载荷指纹重复提交返回当前状态。
- 执行提交：Gateway 使用 `executionKey` 幂等。
- 不可逆操作（push/dcommit/deploy）先查询结果再重试。
