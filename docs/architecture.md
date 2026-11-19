# 架构说明

## 部署单元

| 单元 | 职责 | 不允许 |
|---|---|---|
| `devpilot-center` | 业务状态、编排、审批、审计、项目匹配 | 执行任意 Agent 命令 |
| `devpilot-gateway` | 在受控工作区内执行 Agent 与命令 | 修改业务状态 |
| PostgreSQL | 业务数据与事件 | 承载实时日志正文 |

## 模块边界

```text
devpilot-common      跨模块 DTO、状态机、执行器契约
devpilot-project     项目注册、匹配
devpilot-bug         BUG 录入、分析结果、匹配记录
devpilot-ai          模型适配与确定性回退、任务拆分
devpilot-task        任务状态机、计划、事件
devpilot-executor    Local/Remote/Routing AgentExecutor
devpilot-repository  Git / Git-SVN / Worktree
devpilot-verification 测试与审查回路
devpilot-gateway     唯一执行命令的进程
devpilot-center      Spring Boot 宿主与编排 API
```

## 关键约束

1. Agent 只能返回执行结果，任务状态只由中心的状态机变更。
2. `AgentExecutor` 屏蔽 Orca 部署位置；业务代码不拼接 HTTP 地址或 CLI 命令。
3. Gateway 只接受命令白名单与相对工作区路径，拒绝任意 Shell。
4. Git-SVN 的 `fetch` 与 `dcommit` 按仓库粒度串行，回写必须先 fetch 再检查提交范围。
5. 首期不自动推送、不自动 dcommit、不自动部署。
