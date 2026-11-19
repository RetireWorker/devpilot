# devpilot

基于 Orca 的多项目自动化研发平台：统一接收 BUG/需求，自动分析、匹配项目、拆分任务、调用 Agent 完成修改、测试与审查，最终按项目类型交付。

## 当前实现范围

阶段一最小闭环：

```text
BUG 提交 -> AI 分析 -> 项目匹配 -> 任务拆分 -> 独立工作区
        -> Agent 修改 -> 测试 -> 审查 -> 变更报告
```

推送 Git、`git svn dcommit`、CI 部署保留审批与接口，首期不自动执行。

## 模块

| 模块 | 职责 |
|---|---|
| `devpilot-common` | 状态机、跨模块 DTO、`AgentExecutor` 契约 |
| `devpilot-project` | 项目注册与 BUG→项目匹配 |
| `devpilot-bug` | BUG 录入、分析记录、匹配结果 |
| `devpilot-ai` | 模型适配、确定性回退、任务拆分 |
| `devpilot-task` | 状态机、计划、子任务、执行事件 |
| `devpilot-executor` | Local/Remote/Routing 执行器 |
| `devpilot-repository` | Git / Git-SVN / Worktree |
| `devpilot-verification` | 测试与审查回路 |
| `devpilot-gateway` | 唯一执行命令的受控节点 |
| `devpilot-center` | Spring Boot 宿主与编排 API |

## 本地运行

两个进程，必须先起 Gateway（中心通过它执行命令和 Agent）：

```bash
mvn -B clean verify

# 终端 1：执行节点。Agent 执行要求本机有可用的 Orca 运行时。
DEVPILOT_GATEWAY_ENABLED=true \
DEVPILOT_GATEWAY_TOKEN=change-me \
DEVPILOT_WORKSPACE_ROOT=$PWD/data/workspaces \
DEVPILOT_GATEWAY_STATE=$PWD/data/gateway \
DEVPILOT_AGENT_RUNNER=orca \
DEVPILOT_AGENT_ID=claude \
DEVPILOT_AGENT_MODEL=claude-sonnet-5-5 \
java -jar devpilot-gateway/target/devpilot-gateway-0.1.0-SNAPSHOT.jar

# 终端 2：管理中心 + UI（http://localhost:8080）
DEVPILOT_DB_URL=jdbc:postgresql://localhost:5432/devpilot \
DEVPILOT_DB_USER=devpilot \
DEVPILOT_DB_PASSWORD=*** \
DEVPILOT_GATEWAY_CALL_ENABLED=true \
ORCA_GATEWAY_ENDPOINT=http://127.0.0.1:18080 \
ORCA_GATEWAY_TOKEN=change-me \
DEVPILOT_WORKSPACE_ROOT=$PWD/data/workspaces \
DEVPILOT_MIRROR_ROOT=$PWD/data/mirrors \
java -jar devpilot-center/target/devpilot-center-0.1.0-SNAPSHOT.jar
```

关键约束：

- 中心与 Gateway 必须使用**同一个绝对工作区根目录**，相对路径才会解析到同一处。
- `DEVPILOT_AGENT_ID` 取自 `orca agent installed`；如果该 Agent 在 Orca 中的
  默认模型不可用，必须用 `DEVPILOT_AGENT_MODEL` 显式指定。
- Gateway 会为每个任务工作区自动注册 Orca folder workspace（Orca 只允许在它
  认识的工作区启动 Agent），任务结束后隐藏。
- 默认连接 PostgreSQL 并执行 Flyway 迁移。

仅做本地冒烟时可临时切 H2（H2 在 center 是 test scope）：

```bash
mvn spring-boot:run -pl devpilot-center -Dspring-boot.run.useTestClasspath=true \
  -Dspring-boot.run.arguments="--spring.jpa.hibernate.ddl-auto=validate \
  --spring.datasource.url=jdbc:h2:mem:dev;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1 \
  --spring.datasource.driver-class-name=org.h2.Driver --spring.datasource.username=sa"
```

启动时导入示例项目注册（默认关闭）：

```bash
--ai.project-registry.import-dir=$PWD/project-registry
```

```bash
docker compose -f deployment/docker/docker-compose.yml up --build
```

> 容器内的 Gateway 只能执行命令；Agent 执行需要宿主机的 Orca 运行时，
> 生产上把 Gateway 部署在 Orca 节点上。

## 文档

- `docs/architecture.md`
- `docs/workflow.md`
- `docs/bug-workflow.md`
- `docs/git-svn-guide.md`
- `docs/orca-executor.md`
- `docs/security.md`

## 硬性约束

- 业务状态只由中心状态机变更，Agent 不能直接改库。
- 只有 Gateway 执行命令，且只执行白名单命令。
- 不允许通过删除/跳过测试或弱化断言让流水线变绿。
- 首期不自动推送、不自动 dcommit、不自动部署。
