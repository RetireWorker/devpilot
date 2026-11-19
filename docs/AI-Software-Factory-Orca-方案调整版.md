# AI Software Factory：基于 Orca 的多项目自动化研发平台方案（完善版）

**版本：** 1.2  
**状态：** 方案完善稿  
**修订基础：** 在 1.1「方案调整版」基础上，融合「BUG 提交端到端流程」，补充 BUG 分析、项目自动匹配、任务拆分、Agent 分配、测试/审查回路、交付验收、状态机、接口、数据和验收指标。  
**目标：** 统一接收需求和 BUG，自动定位项目，调用 Orca/AI Agent 完成分析、开发、测试、代码审查，并按项目类型执行 Git 或 SVN 交付，最终接入现有 CI/CD。

---

## 0. 本次完善说明

本版本在 1.1 方案基础上重点补充：

1. **BUG 全链路自动化流程**：BUG 提交 → AI 分析 → 自动匹配项目 → 自动拆分任务 → 自动分配 Agent → 自动测试 → 代码审查 → 创建提交 → CI 构建 → 部署测试环境 → 验证测试 → 交付完成。
2. **项目自动匹配机制**：优先使用用户指定项目；未指定时基于项目注册信息、日志包名、错误关键字、代码路径、服务名、历史相似 BUG 等信号自动匹配，并设置置信度阈值和人工确认兜底。
3. **任务拆分与 Agent 编排**：将 BUG 处理拆分为代码分析、根因分析、测试分析等子任务，再分配开发、测试、Review Agent，明确输入、输出、允许修改路径和验收标准。
4. **测试与审查闭环**：测试失败返回开发 Agent 限次修复；审查失败返回修改；超过上限进入 `FAILED_NEEDS_HUMAN`，禁止通过删除测试、跳过测试、弱化断言等方式“修复”。
5. **状态机与事件完善**：增加 BUG 分析、项目匹配、计划确认、工作区准备、交付审批、SVN 回写、CI 构建、测试部署、验收等状态和事件。
6. **SVN 项目继续统一通过 `git-svn` 纳入 Git 工作流**：首次 `git svn clone` 建立本地镜像，后续使用 Git 分支/Worktree 开发，审批通过后由受控 Worker 执行 `git svn dcommit` 回写 SVN。
7. **Spring Boot 管理中心支持远程/本地 Orca 双模式**：通过 `AgentExecutor` 抽象屏蔽 Orca 部署位置差异，业务流程不感知 Orca 在本机还是远程节点。

核心原则保持不变：

> **Spring Boot 管理中心负责业务编排和状态管理，Orca 负责 Agent 执行；仓库适配层负责 Git/Git-SVN；CI/CD 负责构建和部署。**

---

## 1. 总体目标与范围

### 1.1 建设目标

- 统一接收需求和 BUG，形成标准化工单。
- 自动分析 BUG/需求，自动匹配项目，减少人工分派。
- 自动创建独立工作区，支持 Git 和 Git-SVN 项目。
- 调用本地或远程 Orca，执行多 Agent 协作。
- 自动测试、自动审查、限次修复，保留完整执行证据。
- 按项目类型交付：原生 Git 创建 MR/推送；SVN 项目审批后 `git svn dcommit`。
- 接入现有 Drone、私有 Registry、Docker Swarm。
- 生产发布保留人工审批，风险可控。

### 1.2 首期范围

首期建议只打通最小闭环：

```text
BUG 提交
  -> AI 分析
  -> 自动匹配项目
  -> 拆分任务
  -> 分配 Agent
  -> 创建独立工作区
  -> Orca 修改代码
  -> Maven 测试
  -> 代码审查
  -> 生成变更报告
  -> 人工审查
```

首期不自动推送 Git、不自动 `git svn dcommit`、不自动部署。确认稳定后再逐步开放交付和部署能力。

---

## 2. 总体架构

```text
                    需求 / BUG 统一入口
             Web 管理端 / API / Webhook / 人工录入
                              |
                              v
                 +---------------------------+
                 |   AI DevOps Center        |
                 |   Spring Boot（远程部署）  |
                 |                           |
                 | BUG 分析 / 项目匹配        |
                 | 任务拆分 / Agent 编排      |
                 | 状态机 / 调度 / 审批        |
                 | 执行记录 / 日志 / 报告      |
                 | 仓库配置 / CI/CD 管理      |
                 +-------------+-------------+
                               |
                     AgentExecutor 接口
                               |
             +-----------------+------------------+
             |                                    |
             v                                    v
 +---------------------------+       +---------------------------+
 | Remote Orca Executor      |       | Local Orca Executor       |
 | 远程 Orca 节点             |       | 与 Spring Boot 同机/本机   |
 |                           |       |                           |
 | Orca API/CLI Adapter      |       | Orca API/CLI Adapter      |
 | 任务提交 / 状态 / 日志     |       | 任务提交 / 状态 / 日志     |
 | 工作目录 / Agent 运行环境  |       | 工作目录 / Agent 运行环境  |
 +-------------+-------------+       +-------------+-------------+
               |                                   |
               +-----------------+-----------------+
                                 |
                                 v
                    项目工作空间 / Workspace
                                 |
                  +--------------+--------------+
                  |                             |
                  v                             v
             原生 Git 项目                  SVN 项目
             Git clone/fetch               git svn clone/fetch
             Git branch/worktree           Git branch/worktree
             Git commit/push               Git commit
             Merge Request                 git svn dcommit
                  |                             |
                  +--------------+--------------+
                                 |
                                 v
                       测试 / 代码审查
                                 |
                                 v
                         Drone CI / CI
                                 |
                                 v
                  Registry -> 测试环境部署
                                 |
                                 v
                       验证测试 / 人工验收
                                 |
                                 v
                       生产审批 / 交付完成
```

### 2.1 组件职责边界

| 组件 | 主要职责 | 不负责的事情 |
|---|---|---|
| Spring Boot 管理中心 | BUG/需求、项目、任务、审批、状态机、调度、审计、项目匹配编排 | 不直接承担长时间代码执行 |
| Orca | Agent 编排、任务执行、工具调用、协作 | 不作为唯一业务数据库 |
| Orca Executor | 连接本地/远程 Orca，统一提交任务和获取结果 | 不决定业务审批规则 |
| Repository Manager | Git、Git-SVN 仓库初始化、分支、工作区、提交 | 不决定 AI 如何分析 BUG |
| CI/CD | 构建、测试、镜像、部署、回滚 | 不负责 BUG 拆解 |
| PostgreSQL | 持久化业务状态、执行记录、审计信息 | 不作为实时日志流的唯一通道 |

---

## 3. Spring Boot 远程/本地 Orca 双模式

### 3.1 设计要求

Spring Boot 可能部署在远程 Linux 服务器，而 Orca 可能运行在：

- **远程模式**：Orca 部署在专用 AI 节点，Spring Boot 通过内网 API 或受控 SSH/CLI 网关调用。
- **本地模式**：Orca 与 Spring Boot 部署在同一台服务器，或开发环境中与管理中心同机运行。
- **多执行节点模式**：多个 Orca 节点分别承载不同项目、不同 Agent 或不同权限级别。

不要在业务 Service 中写死 Orca 的 HTTP 地址或 CLI 命令。统一通过 `AgentExecutor` 接口调用。

### 3.2 执行器抽象

```java
/**
 * AI Agent 执行器统一接口。
 * 业务层只依赖该接口，不关心 Orca 位于本机还是远程节点。
 */
public interface AgentExecutor {

    /** 提交一次 Agent 执行任务，返回执行实例 ID。 */
    ExecutionSubmitResult submit(AgentExecutionRequest request);

    /** 查询执行状态。 */
    ExecutionStatus getStatus(String executionId);

    /** 获取执行日志或日志游标。 */
    ExecutionLogPage getLogs(String executionId, String cursor);

    /** 获取结构化执行结果。 */
    AgentExecutionResult getResult(String executionId);

    /** 请求停止执行。 */
    void cancel(String executionId);

    /** 检查执行器健康状态。 */
    ExecutorHealth health();
}
```

建议实现：

```text
AgentExecutor
  |
  +-- LocalOrcaExecutor
  |     +-- 调用本机 Orca API（优先）
  |     +-- 或通过受控 CLI Wrapper 调用本机 Orca CLI
  |
  +-- RemoteOrcaExecutor
  |     +-- 调用远程 Orca Gateway/API（优先）
  |     +-- 或调用远程执行节点上的 Agent Worker 服务
  |
  +-- RoutingAgentExecutor（可选）
        +-- 根据项目、任务标签、节点负载选择执行节点
```

优先采用 Orca 对外稳定 API。如果当前安装版本主要通过 CLI 暴露能力，则在本地或远程节点部署轻量级 `orca-agent-gateway`，由 Gateway 在受控工作目录内调用 CLI。不要让 Spring Boot 直接拼接任意 Shell 命令，也不要把 SSH 私钥和模型密钥散落在业务配置中。

### 3.3 配置示例

```yaml
ai:
  executor:
    # local / remote / routing
    mode: remote

    local:
      enabled: false
      endpoint: http://127.0.0.1:18080
      connect-timeout: 3s
      read-timeout: 30s

    remote:
      enabled: true
      endpoint: https://orca-node-01.internal:18080
      connect-timeout: 5s
      read-timeout: 30s
      token: ${ORCA_GATEWAY_TOKEN}

    callback:
      enabled: true
      public-base-url: https://devops.example.internal
      signing-secret: ${ORCA_CALLBACK_SECRET}
```

生产环境建议使用 TLS/mTLS 或短期令牌；远程节点只允许管理中心访问。若采用回调，必须验证签名、时间戳和任务 ID，防止伪造回调和重放。

### 3.4 远程执行节点职责

```text
orca-node/
├── orca-gateway
├── orca-runtime
├── workspaces/
├── logs/
├── artifacts/
└── node-config/
```

Gateway 对外只暴露有限操作：

- `POST /api/executions`：提交执行任务
- `GET /api/executions/{id}`：查询状态
- `GET /api/executions/{id}/logs`：分页/游标读取日志
- `GET /api/executions/{id}/result`：获取结构化结果
- `POST /api/executions/{id}/cancel`：取消任务
- `GET /health`：健康检查

请求中传递任务 ID、项目 ID、工作目录策略、Agent 角色、任务说明、超时和资源限制。**不要允许调用方传入任意绝对路径或任意 Shell 命令。**

### 3.5 执行模式选择建议

| 场景 | 建议 |
|---|---|
| Spring Boot 与 Orca 同机 | Local 模式 |
| Spring Boot 在业务服务器，Orca 在 AI 服务器 | Remote 模式 |
| 多个 Orca 节点 | Routing 模式 |
| 开发调试 | Local 模式 |
| 生产管理中心 | Remote 模式，独立 AI 执行节点 |

管理中心远程部署时，代码仓库和工作区最好放在 Orca 执行节点，避免大规模代码通过管理中心中转。管理中心只保存仓库元数据、任务状态、日志索引和产物引用。

---

## 4. SVN 项目通过 Git-SVN 统一工作流

### 4.1 目标

SVN 项目通过 `git-svn` 建立 Git 本地镜像，使 AI Agent 使用熟悉的 Git 分支、提交和差异工具进行开发，最终在审批后回写 SVN。

```text
SVN Repository
      |
      | git svn clone / fetch
      v
Git-SVN 本地仓库
      |
      +-- AI 任务分支 / Git Worktree
      |
      +-- git commit
      |
      +-- 测试与审查
      |
      +-- 受控提交钩子
              |
              v
         git svn dcommit
              |
              v
         SVN Repository
```

### 4.2 首次初始化

```bash
# 标准 trunk/branches/tags 布局
git svn clone \
  --stdlayout \
  --prefix=svn/ \
  https://svn.example.com/repos/tyjr-handle \
  tyjr-handle-gitsvn

cd tyjr-handle-gitsvn

git svn info
git branch -a
```

非标准布局应显式指定 `-T`、`-b`、`-t`：

```bash
git svn clone \
  -T trunk \
  -b branches \
  -t tags \
  --prefix=svn/ \
  https://svn.example.com/repos/tyjr-handle \
  tyjr-handle-gitsvn
```

注意：`git-svn` 对 SVN 分支/标签布局、历史量、忽略规则、`svn:externals`、大文件和特殊属性有兼容性边界。正式迁移前必须用代表性项目验证。

### 4.3 日常同步

```bash
git svn fetch
git rebase svn/trunk
```

对于团队共享的长期 Git-SVN 镜像仓库，建议由单独的同步任务执行 `git svn fetch`，避免多个 Agent 同时更新同一个仓库的 Git-SVN 元数据。

### 4.4 AI 任务分支与工作区

```bash
git svn fetch
git worktree add \
  -b ai/bug-10001 \
  .worktrees/bug-10001 \
  svn/trunk
```

AI 完成修改后：

```bash
git status --short
git diff --check
git diff
git add .
git commit -m "[BUG-10001] 修复企业信息同步重复数据问题"
```

提交信息建议带任务号：

```text
[BUG-10001] 修复企业信息同步重复数据问题
[REQ-10002] 增加企业信息批量导出功能
```

### 4.5 将 Git 提交回写 SVN

```bash
git status
git log --oneline svn/trunk..HEAD
git svn dcommit
```

执行前必须检查：

1. `git svn fetch` 已同步最新 SVN 提交。
2. 当前分支基于正确的 SVN 远端分支。
3. 本地提交不包含不应进入 SVN 的合并提交或不兼容提交结构。
4. `git diff --check`、构建、测试和审查通过。
5. 当前任务已获得 SVN 自动提交授权。

> **重要：`git svn dcommit` 会将本地提交逐个转换为 SVN 提交，不是普通 `git push`。** 它可能改写本地提交历史/提交 ID，并要求提交历史与 Git-SVN 映射保持一致。不要让多个 Agent 对同一个 Git-SVN 镜像仓库并发执行 `dcommit`。

### 4.6 自动提交钩子设计

推荐流程：

```text
AI 修改代码
   |
   v
本地 git commit
   |
   v
自动化流水线检查
   |
   +-- 测试失败 ------> 阻止回写
   |
   +-- 审查失败 ------> 阻止回写
   |
   +-- 未审批 --------> 等待人工审批
   |
   v
任务状态变为 SVN_COMMIT_APPROVED
   |
   v
受控 Hook / Commit Worker
   |
   +-- 获取项目级锁
   +-- git svn fetch
   +-- 检查分支与提交范围
   +-- git svn dcommit
   +-- 记录 SVN revision
   +-- 释放项目级锁
   |
   v
回写成功 / 失败通知
```

推荐把“钩子”实现为管理中心或仓库 Worker 的事件钩子，而不是让任意 Git commit 自动触发 SVN 提交。

可选实现方式：

- **首选：任务状态事件钩子。** 任务通过测试、审查和审批后，管理中心发出 `SVN_DCOMMIT_APPROVED` 事件，由 SVN Commit Worker 执行。
- **可选：自定义 Git hook。** 在受控仓库中安装 `post-commit`/自定义 hook，仅发送任务事件或写入待处理标记；真正 `dcommit` 仍由独立 Worker 执行。
- **不推荐：`post-commit` 直接执行 `git svn dcommit`。** 这会绕过测试、审查和审批，也容易造成并发冲突。

### 4.7 Git-SVN 并发和锁

建议锁粒度为**每个 SVN 仓库一个同步/回写锁**：

- `git svn fetch` 与 `git svn dcommit` 统一串行化。
- 同一个 SVN 仓库可以有多个独立任务工作区并行开发。
- 同一时刻只允许一个 Worker 对该仓库执行 Git-SVN 元数据更新和 `dcommit`。
- 回写前重新 fetch，并检查是否出现 SVN 新提交。
- 如果远端发生冲突，停止自动回写，重新 rebase/解决冲突并重新测试。

### 4.8 Git-SVN 项目注册配置示例

```yaml
project:
  id: tyjr-handle
  name: 业务处理服务

repository:
  type: git-svn
  svnUrl: https://svn.example.com/repos/tyjr-handle
  layout: standard
  trunk: trunk
  branches: branches
  tags: tags
  localMirror: /data/repositories/tyjr-handle-gitsvn
  targetRef: refs/remotes/svn/trunk
  dcommitEnabled: true
  dcommitMode: approval_required
  serializeFetchAndDcommit: true

workspace:
  strategy: git-worktree
  root: /data/workspaces/tyjr-handle

build:
  type: maven
  javaVersion: 8
  command: mvn clean verify

delivery:
  type: svn
  requireReview: true
  requireApproval: true
```

---

## 5. 项目注册中心

所有项目统一登记，不让 Agent 自行猜测仓库地址和构建方式。

### 5.1 项目类型

- `git`：原生 Git 项目。
- `git-svn`：通过 Git-SVN 管理的 SVN 项目。
- 后续可扩展 `svn`，但新接入项目优先评估 Git-SVN。

### 5.2 Git 项目配置示例

```yaml
project:
  id: tyjr-jcxxcj-service
  name: 企业信息采集服务

repository:
  type: git
  url: ssh://git@git.example.com/tyjr/jcxxcj-service.git
  defaultBranch: develop
  workspaceStrategy: git-worktree

runtime:
  language: java
  javaVersion: 8
  framework: spring-boot
  frameworkVersion: 2.3.12

build:
  command: mvn clean verify
  testCommand: mvn test

delivery:
  createMergeRequest: true
  autoPush: false
  deployEnvironment: test
```

### 5.3 项目元数据

项目元数据至少包含：

- 技术栈、JDK、Maven/Gradle/Node 版本。
- 构建、测试、静态检查命令。
- 项目启动方式、健康检查地址。
- 依赖服务和测试数据库要求。
- 分支策略、提交规范、代码风格。
- 部署方式、目标环境和回滚策略。
- Agent 可读写目录、禁止访问目录。
- 代码推送、SVN dcommit、部署所需审批策略。
- 用于 BUG 自动匹配的别名、服务名、日志包名、代码路径关键字、负责人/团队。

---

## 6. Agent 团队与任务分工

采用“一个主控 + 按需分配专业 Agent”的模式。

| Agent 角色 | 职责 |
|---|---|
| 主控/需求分析 | 理解需求或 BUG、检查项目知识、拆解任务、协调子任务 |
| BUG 分析 Agent | 判断问题类型、影响范围、严重度、复现线索、分析错误日志 |
| 项目匹配 Agent | 根据 BUG 信息、日志、包名、路径、服务名和历史记录匹配项目 |
| 代码分析 Agent | 定位相关模块、类、方法、调用链和可疑变更 |
| 根因分析 Agent | 结合日志、代码、测试和历史提交分析根因 |
| 测试分析 Agent | 设计复现/回归测试用例，分析测试失败 |
| Java 开发 Agent | Java/Spring Boot 代码实现和 BUG 修复 |
| 前端开发 Agent | Vue/前端页面和接口联调 |
| 测试工程师 Agent | 执行测试、分析失败、生成测试报告 |
| 代码审查 Agent | 独立审查差异、安全性、兼容性和可维护性 |
| 发布工程师 Agent | 生成构建/部署计划，执行获批的发布动作 |

工具选择可按实际模型和 Orca 版本配置 Claude Code、Codex、CodeBuddy、OpenCode 等。不要将具体厂商工具写死在业务流程里；Agent 角色与底层运行器分离，便于替换。

并行开发约束：

- 不同 Agent 尽量修改不同文件或模块。
- 同一个工作区不允许多个写入型 Agent 同时工作。
- 每个子任务必须声明输入、输出、允许修改路径和验收标准。
- 主控 Agent 汇总结果，不应把未验证的 Agent 自述视为测试通过。
- 代码分析、根因分析、测试分析可并行；写代码、测试、审查按依赖串行。

---

## 7. 通用端到端研发流程

```text
提交需求 / BUG
      |
      v
校验字段、附件、重复任务
      |
      v
AI 分析 BUG / 需求
      |
      v
识别项目（优先使用用户指定项目）
      |
      v
读取项目配置和项目知识
      |
      v
主控 Agent 分析并生成开发计划
      |
      v
人工确认计划（高风险/复杂需求/低置信匹配）
      |
      v
创建独立工作空间
  |                       |
  v                       v
Git clone/worktree     Git-SVN mirror/worktree
  |                       |
  +-----------+-----------+
              |
              v
         AI 开发修改
              |
              v
         本地差异检查
              |
              v
         Maven/前端测试
              |
        +-----+------+
        |            |
      失败           通过
        |            |
        v            v
   限次修复       独立代码审查
        |            |
        +----重测----+
                     |
                     v
                人工审批
                     |
          +----------+----------+
          |                     |
        Git 项目              Git-SVN 项目
          |                     |
     创建 MR/推送           git svn dcommit
          |                     |
          +----------+----------+
                     |
                     v
                  CI 构建
                     |
                     v
               镜像仓库/制品库
                     |
                     v
                测试环境部署
                     |
                     v
             健康检查/接口回归
                     |
                     v
                人工验收
                     |
                     v
               生产发布审批
```

### 7.1 自动修复策略

- 测试失败允许自动修复 2～3 轮。
- 每轮保留失败日志、修改差异和 Agent 说明。
- 超过上限后进入 `FAILED_NEEDS_HUMAN`。
- 不允许 Agent 通过删除测试、跳过测试或弱化断言来“修复”失败。
- 测试结果必须由实际测试进程产生，并保存退出码和报告。
- 自动重试不能重复执行不可逆操作；提交、dcommit、部署前先查询结果。

---

## 8. BUG 提交端到端自动化流程

本节为 1.2 新增核心章节，直接对应 BUG 提交处理流程。

### 8.1 流程总览

```text
BUG 提交
   |
   v
① AI 分析 BUG
   判断问题类型
   判断影响范围
   分析错误日志
   |
   v
② 自动匹配项目
   识别 Java 项目
   定位相关模块
   查找相关代码
   |
   v
③ 自动拆分任务
   +----------+----------+
   |          |          |
   v          v          v
代码分析    根因分析    测试分析
   |          |          |
   +----------+----------+
   |
   v
④ 自动分配 Agent
   +----------+----------+
   |          |          |
   v          v          v
开发 Agent  测试 Agent  Review Agent
   |          |          |
   v          |          |
修改代码     |          |
   |          |          |
   +----------+----------+
   |
   v
⑤ 自动执行测试
   Maven Test
   单元测试
   集成测试
   |
   测试是否通过？
    /       \
   否         是
   |           |
   v           v
返回开发 Agent ⑥ 代码审查
   |           |
   +---->      v
         审查是否通过？
          /       \
        否         是
        |           |
        v           v
     修改代码    ⑦ 创建提交
        |           |
        +---->      v
               ⑧ CI 构建
                   |
              Drone Pipeline
                   |
                   v
              ⑨ 部署测试环境
                   |
                   v
              ⑩ 验证测试
                   |
                   v
              ⑪ 交付完成
```

### 8.2 步骤说明

| 步骤 | 动作 | 主要产出 | 失败/异常处理 |
|---|---|---|---|
| ① BUG 提交与 AI 分析 | 校验必填字段、附件、日志；AI 判断问题类型、影响范围、严重度、复现线索；分析错误日志 | `BugAnalysisReport`：类型、严重度、影响模块、疑似服务、缺失信息 | 信息不足进入 `WAITING_CONFIRM` 或人工补充 |
| ② 自动匹配项目 | 优先用户指定；否则基于服务名、日志包名、代码路径、项目别名、历史相似 BUG 匹配 | `ProjectMatchResult`：项目 ID、置信度、匹配依据、候选项目 | 高置信自动；中置信人工确认；低置信转人工 |
| ③ 自动拆分任务 | 拆为代码分析、根因分析、测试分析等子任务；主控汇总修复计划 | `TaskPlan`：子任务、输入输出、允许路径、验收标准 | 高风险/复杂计划需人工确认 |
| ④ 自动分配 Agent | 按角色分配开发、测试、Review Agent；建立依赖和写锁 | `AgentAssignment` | 节点不可用则等待/重试/切节点 |
| ⑤ 自动执行测试 | 执行 Maven Test、单元测试、集成测试；保存退出码和报告 | `TestResult` | 失败返回开发 Agent，限 2～3 轮；超限 `FAILED_NEEDS_HUMAN` |
| ⑥ 代码审查 | 独立 Review Agent 审查差异、安全性、兼容性、可维护性、测试充分性 | `ReviewResult` | 不通过返回修改；记录审查意见 |
| ⑦ 创建提交 | 本地 commit；Git 项目按策略创建 MR/推送；Git-SVN 项目进入审批 | `CodeChange`、commit、MR 或待 dcommit 记录 | 推送/dcommit 默认需审批 |
| ⑧ CI 构建 | Drone 执行 Maven、测试、Docker Build、镜像安全检查 | `PipelineRecord` | 失败回开发或进入人工处理 |
| ⑨ 部署测试环境 | 推送 Registry 并部署测试环境 | `DeploymentRecord`、镜像 digest | 失败回滚并告警 |
| ⑩ 验证测试 | 健康检查、API 回归、人工验收 | `AcceptanceRecord` | 失败回开发/回滚 |
| ⑪ 交付完成 | 记录任务、commit/revision、镜像、CI、部署、审批；关闭 BUG | 交付报告 | 异常保留审计和重试入口 |

### 8.3 BUG 分析要求

BUG 提交时必须尽量包含：

- 项目或服务名称（可选，用户指定优先）。
- BUG 标题、描述、复现步骤、期望结果、实际结果。
- 环境信息、版本、时间范围。
- 错误日志、堆栈、截图、接口请求/响应。
- 严重度、影响范围、是否生产问题。
- 关联需求、历史 BUG、负责人。

AI 分析输出至少包含：

```json
{
  "bugId": "BUG-10001",
  "problemType": "DATA_DUPLICATION",
  "severity": "HIGH",
  "impactScope": ["企业信息同步", "定时任务"],
  "errorLogSummary": "唯一键冲突/重复插入",
  "suspectedModules": ["sync-service", "enterprise-repository"],
  "reproducibility": "HIGH",
  "missingInfo": [],
  "recommendedProjectCandidates": ["tyjr-jcxxcj-service"]
}
```

### 8.4 项目自动匹配规则

匹配优先级：

1. 用户明确指定项目。
2. 项目注册表中的服务名、别名、日志包名、代码路径关键字。
3. BUG 日志中的类名、包名、接口路径、异常关键字。
4. 历史相似 BUG 的项目归属。
5. 代码检索结果和模块映射。

置信度策略建议：

| 置信度 | 处理 |
|---|---|
| `>= 0.80` | 自动匹配，进入任务拆分 |
| `0.60 ~ 0.80` | 进入 `WAITING_PROJECT_CONFIRM`，人工确认 |
| `< 0.60` | 不自动匹配，转人工分派 |

匹配结果示例：

```json
{
  "bugId": "BUG-10001",
  "projectId": "tyjr-jcxxcj-service",
  "confidence": 0.91,
  "signals": [
    "日志包名 com.example.jcxxcj",
    "接口路径 /api/enterprise/sync",
    "历史相似 BUG-9821"
  ],
  "candidates": [
    {"projectId": "tyjr-jcxxcj-service", "score": 0.91},
    {"projectId": "tyjr-handle", "score": 0.42}
  ]
}
```

### 8.5 任务拆分模板

```yaml
taskPlan:
  taskId: BUG-10001
  projectId: tyjr-jcxxcj-service
  objective: 修复企业信息同步重复数据问题
  subtasks:
    - id: analyze-code
      role: code-analyzer
      type: parallel
      input: [bugAnalysisReport, repositorySnapshot]
      output: [codeLocationReport, changeHistory]
      allowedPaths: ["src/main/java/**"]
      acceptance: "定位相关类/方法/调用链并给出证据"
    - id: analyze-root-cause
      role: root-cause-analyzer
      type: parallel
      input: [bugAnalysisReport, errorLogs]
      output: [rootCauseReport]
      acceptance: "给出根因假设、证据和验证方法"
    - id: analyze-test
      role: test-analyst
      type: parallel
      input: [bugAnalysisReport, existingTests]
      output: [testPlan, reproductionCase]
      acceptance: "提供可复现或回归测试方案"
    - id: fix-code
      role: java-developer
      type: serial
      dependsOn: [analyze-code, analyze-root-cause, analyze-test]
      output: [codeDiff, commit]
      allowedPaths: ["src/main/java/**", "src/test/java/**"]
      acceptance: "编译通过、测试通过、不删除/跳过测试"
    - id: review-code
      role: code-reviewer
      type: serial
      dependsOn: [fix-code]
      output: [reviewResult]
      acceptance: "独立审查通过并记录意见"
```

### 8.6 Agent 分配与并发约束

- 代码分析、根因分析、测试分析可并行。
- 开发 Agent 写入代码时，同一工作区只允许一个写入型 Agent。
- 测试 Agent 在开发提交后执行测试。
- Review Agent 必须独立于开发 Agent，不能只复述开发结论。
- 主控 Agent 负责汇总，不替代测试进程和审查结论。
- 每个 Agent 执行实例必须绑定 `taskId`、`subtaskId`、`projectId`、`workspaceId`。

### 8.7 测试与审查回路

```text
开发提交
  -> Maven Test / 单元测试 / 集成测试
  -> 失败？ -> 返回开发 Agent，最多 2～3 轮
  -> 通过？ -> Review Agent 审查
  -> 审查失败？ -> 返回开发 Agent 修改并重测
  -> 审查通过？ -> 进入交付审批
```

失败处理要求：

- 保存每轮失败日志、测试报告、代码差异和 Agent 说明。
- 超过修复轮次进入 `FAILED_NEEDS_HUMAN`。
- 审查意见必须结构化记录：阻断项、建议项、风险项。
- 不允许通过删除测试、跳过测试、弱化断言、吞异常等方式通过。

### 8.8 创建提交与交付

- **Git 项目**：默认进入 `WAITING_APPROVAL`，审批后创建 MR 或推送；是否自动推送按项目策略。
- **Git-SVN 项目**：审批后进入 `SVN_DCOMMITTING`，由 SVN Commit Worker 获取仓库锁，执行 `git svn fetch`、检查、`git svn dcommit`，记录 SVN revision。
- 交付记录必须包含：任务 ID、项目 ID、Git commit、SVN revision（如有）、MR 链接、审批人、时间。

### 8.9 验证测试与交付完成

验证测试至少包括：

- 测试环境健康检查。
- 接口回归测试。
- BUG 复现用例回归。
- 关键日志检查。
- 人工验收（按项目策略）。

交付完成后记录：

- BUG 状态、任务状态、交付报告。
- 源代码追溯信息：Git commit / SVN revision。
- 镜像名称、不可变 tag/digest。
- CI 构建编号、测试报告。
- 部署环境、时间、执行人/审批人。
- 上一可用版本和回滚结果。

---

## 9. 任务状态机与事件

状态由 Spring Boot 统一维护，Agent 只能提交执行结果，不能直接更新业务数据库。

| 状态 | 含义 |
|---|---|
| CREATED | 新建 |
| BUG_ANALYZING | BUG AI 分析 |
| PROJECT_MATCHING | 项目自动匹配 |
| WAITING_PROJECT_CONFIRM | 等待项目确认 |
| ANALYZING | 需求分析 |
| WAITING_CONFIRM | 等待需求/计划确认 |
| PLANNING / TASK_SPLITTING | 任务拆解 |
| READY | 等待执行 |
| PREPARING_WORKSPACE | 准备仓库和工作区 |
| DEVELOPING | AI 开发/修复 |
| TESTING | 自动测试 |
| REVIEWING | 代码审查 |
| FAILED_NEEDS_HUMAN | 需要人工处理 |
| WAITING_APPROVAL | 等待代码交付审批 |
| GIT_DELIVERING | Git 推送/MR |
| SVN_DCOMMITTING | Git-SVN 回写 SVN |
| BUILDING | CI 构建 |
| DEPLOYING_TEST | 部署测试环境 |
| ACCEPTANCE | 等待验收 |
| WAITING_PROD_APPROVAL | 等待生产发布审批 |
| COMPLETED | 完成 |
| CANCELLED | 已取消 |

关键事件：

```text
BUG_ANALYZED
PROJECT_MATCHED
PROJECT_CONFIRMED
PLAN_CONFIRMED
WORKSPACE_READY
CODE_COMMITTED
TEST_PASSED / TEST_FAILED
REVIEW_PASSED / REVIEW_FAILED
DELIVERY_APPROVED
GIT_PUSHED / MR_CREATED
SVN_DCOMMIT_STARTED / SVN_DCOMMIT_SUCCEEDED / SVN_DCOMMIT_FAILED
CI_SUCCEEDED / CI_FAILED
TEST_ENV_DEPLOYED
ACCEPTED
PROD_APPROVED
COMPLETED
```

状态变化应通过事件和事务控制，保证任务不会重复提交、重复部署。执行请求使用幂等键，防止重复创建 Orca Run。

---

## 10. CI/CD 与现有基础设施集成

优先复用已有 Drone、私有 Registry 和 Docker Swarm。

```text
Git MR / SVN Revision
        |
        v
      Drone CI
        |
        +-- Maven clean verify
        +-- 单元/集成测试
        +-- 静态代码检查（可选 SonarQube）
        +-- Docker Build
        +-- 镜像安全检查
        |
        v
192.168.63.4:5000（现有私有 Registry）
        |
        v
测试环境部署
        |
        +-- 健康检查
        +-- API 回归测试
        +-- 部署结果记录
        |
        v
人工验收 -> 生产审批 -> 生产部署
```

每次部署至少记录：

- 任务 ID、项目 ID、BUG ID。
- Git commit 或 SVN revision。
- 镜像名称和不可变 tag/digest。
- CI 构建编号和测试报告。
- 部署环境、时间、执行人/审批人。
- 上一可用版本和回滚结果。

对于 SVN 项目，建议在构建产物元数据中同时记录 SVN revision 和 Git-SVN 对应提交信息，确保能追溯源代码。

---

## 11. 数据库设计

推荐 PostgreSQL 保存业务数据。

| 表 | 说明 |
|---|---|
| `dev_project` | 项目注册 |
| `dev_repository` | Git/Git-SVN 仓库配置 |
| `dev_executor_node` | 本地/远程 Orca 节点 |
| `dev_requirement` | 需求 |
| `dev_bug` | BUG 主表 |
| `dev_bug_analysis` | BUG AI 分析结果 |
| `dev_bug_log` | BUG 日志/附件索引 |
| `dev_project_match` | BUG 项目匹配结果和置信度 |
| `dev_task` | 研发任务和当前状态 |
| `dev_task_plan` | 任务拆分计划 |
| `dev_task_subtask` | Agent 子任务 |
| `dev_agent_profile` | Agent 角色和能力 |
| `dev_agent_execution` | Orca 执行实例 |
| `dev_execution_event` | 执行事件 |
| `dev_execution_log` | 日志索引/摘要 |
| `dev_workspace` | 工作区记录 |
| `dev_code_change` | 文件差异、提交和 revision |
| `dev_test_result` | 测试结果 |
| `dev_review` | 代码审查结果 |
| `dev_approval` | 人工审批 |
| `dev_pipeline` | CI 构建记录 |
| `dev_deployment` | 部署记录 |
| `dev_acceptance` | 验收记录 |

日志正文和大体积测试报告可以放文件存储或对象存储，数据库保存引用、摘要、校验值和保留策略。

---

## 12. 关键 API 设计

### 12.1 BUG 与任务

```http
POST /api/bugs
GET  /api/bugs/{bugId}
POST /api/bugs/{bugId}/analyze
POST /api/bugs/{bugId}/match-project
POST /api/bugs/{bugId}/create-task

POST /api/tasks/{taskId}/plan
POST /api/tasks/{taskId}/approve-plan
POST /api/tasks/{taskId}/prepare-workspace
POST /api/tasks/{taskId}/execute
POST /api/tasks/{taskId}/cancel
GET  /api/tasks/{taskId}/status
GET  /api/tasks/{taskId}/report
```

### 12.2 Orca Gateway

```http
POST /api/executions
GET  /api/executions/{id}
GET  /api/executions/{id}/logs
GET  /api/executions/{id}/result
POST /api/executions/{id}/cancel
GET  /health
```

### 12.3 审批与交付

```http
POST /api/tasks/{taskId}/approvals
POST /api/tasks/{taskId}/deliver/git
POST /api/tasks/{taskId}/deliver/svn-dcommit
POST /api/tasks/{taskId}/deploy/test
POST /api/tasks/{taskId}/acceptance
```

所有写接口必须认证、授权、审计，并使用幂等键防止重复执行。

---

## 13. 安全、权限和可靠性

### 13.1 权限建议

| 操作 | 默认策略 |
|---|---|
| 拉取代码 | 自动 |
| 读取代码/文档 | 自动，限项目授权范围 |
| 修改任务工作区 | 自动 |
| 执行测试 | 自动，受容器/沙箱限制 |
| 本地 Git commit | 自动，可配置 |
| Git push / 创建 MR | 默认审批 |
| `git svn dcommit` | 必须审批，支持项目级策略 |
| 测试环境部署 | 可自动，按项目授权 |
| 生产部署 | 必须人工审批 |
| 生产数据库操作 | 默认禁止 Agent 直接执行 |

### 13.2 运行隔离

- 每个任务使用独立工作目录或容器。
- Agent 不得读取其他项目仓库、密钥目录和宿主机敏感文件。
- 构建脚本执行需限制网络、CPU、内存、磁盘和运行时长。
- 仓库凭据使用最小权限、短期凭据或凭据代理。
- 生产密钥不下发给 Agent。
- 所有远程执行接口必须认证、授权、审计。
- 远程回调必须验签并防重放。
- BUG 附件和日志入库前进行病毒扫描、大小限制和敏感信息脱敏。

### 13.3 可靠性

- 任务调度采用持久化队列或数据库任务表，避免只依赖内存队列。
- 执行请求使用幂等键，防止重复创建 Orca Run。
- 每个执行实例设置超时、心跳和取消机制。
- 远程 Orca 暂时不可用时，任务进入等待或失败重试状态。
- 管理中心重启后可以根据数据库和执行器状态恢复任务。
- Git-SVN 的 fetch/dcommit 必须有仓库级互斥锁。
- 自动重试不能重复执行不可逆的提交或部署操作；这类操作必须先查询结果再决定是否重试。

---

## 14. 推荐项目结构

```text
ai-software-factory/
├── ai-devops-center/
│   ├── devops-web/                 # 管理界面 API
│   ├── devops-project/             # 项目注册
│   ├── devops-bug/                 # BUG 管理、分析、匹配
│   ├── devops-requirement/         # 需求
│   ├── devops-task/                # 状态机、任务拆分和调度
│   ├── devops-agent/               # Agent 抽象和策略
│   ├── devops-executor/            # Local/Remote Orca
│   ├── devops-repository/          # Git/Git-SVN 适配
│   ├── devops-workspace/           # Worktree/工作区
│   ├── devops-test/                # 测试结果
│   ├── devops-review/              # 代码审查
│   ├── devops-pipeline/            # CI/CD
│   ├── devops-deployment/          # 部署与回滚
│   └── devops-audit/               # 审计
│
├── orca-node/
│   ├── orca-gateway/               # 远程执行网关
│   └── node-config/
│
├── project-registry/
│   ├── git/
│   └── git-svn/
│
├── agent-prompts/
│   ├── bug-analysis.md
│   ├── project-matcher.md
│   ├── requirement-analysis.md
│   ├── code-analyzer.md
│   ├── root-cause-analyzer.md
│   ├── test-analyst.md
│   ├── java-developer.md
│   ├── bug-fixer.md
│   ├── test-engineer.md
│   └── code-reviewer.md
│
├── deployment/
│   ├── docker/
│   ├── drone/
│   └── swarm/
│
└── docs/
    ├── architecture.md
    ├── workflow.md
    ├── bug-workflow.md
    ├── git-svn-guide.md
    ├── orca-executor.md
    └── security.md
```

---

## 15. 分阶段落地计划

### 阶段一：打通 BUG 最小闭环

范围：

1. 项目注册中心。
2. BUG 提交、AI 分析、项目自动匹配。
3. Git 项目和 Git-SVN 项目初始化。
4. Spring Boot 任务管理、任务拆分、Agent 分配。
5. Local/Remote Orca Executor 二选一先跑通，接口同时预留。
6. 一个 BUG 从提交到 AI 修改、Maven 测试、代码审查、差异报告。
7. 先不自动推送、不自动 dcommit、不自动部署。

验收标准：提交一个指定项目的 BUG，系统能自动分析、匹配项目、准备独立工作区、调用 Orca、完成代码修改、测试和审查，并生成可审查的变更报告。

### 阶段二：接入 Git-SVN 自动回写

1. 选取一个结构简单、风险低的 SVN 项目试点。
2. 建立 Git-SVN 镜像。
3. 验证 fetch、分支、Worktree、提交和 dcommit。
4. 实现仓库级锁和提交前检查。
5. 接入审批事件钩子。
6. 记录 SVN revision 和失败恢复信息。

验收标准：任务通过测试和审查、获得审批后，系统能安全执行 `git svn dcommit`，并记录 SVN revision。

### 阶段三：远程 Orca 节点与多 Agent

1. 部署 Orca Gateway。
2. 接入远程认证、日志、状态查询和取消。
3. 支持 Local/Remote 模式切换。
4. 增加多 Agent 角色和复杂任务拆解。
5. 增加节点健康检查和任务路由。

### 阶段四：CI/CD 自动交付

1. 接入 Drone。
2. 自动构建和测试。
3. 自动推送 Registry。
4. 自动部署测试环境。
5. 健康检查、回滚和验收。
6. 生产发布保留人工审批。

### 阶段五：多项目规模化

- 对接现有需求/BUG 系统。
- 自动项目识别和相似 BUG 检索。
- 项目知识库和历史变更分析。
- 多执行节点资源调度。
- 项目级成本、成功率、返工率和交付周期统计。
- BUG 自动匹配准确率和修复成功率持续优化。

---

## 16. 关键技术决策总结

| 决策点 | 推荐方案 |
|---|---|
| 研发管理中心 | Spring Boot，独立部署 |
| 管理中心与 Orca 通信 | `AgentExecutor` 抽象，Local/Remote 双实现 |
| 远程 Orca 调用 | 优先稳定 API；否则使用受控 Orca Gateway |
| BUG 分析 | 独立 BUG 分析 Agent，输出结构化报告 |
| 项目匹配 | 用户指定优先 + 多信号匹配 + 置信度阈值 + 人工兜底 |
| 任务拆分 | 代码分析、根因分析、测试分析并行，开发/测试/审查串行 |
| 原生 Git 项目 | Git + Worktree |
| SVN 项目 | Git-SVN 镜像 + Git 分支/Worktree |
| SVN 回写 | 审批通过后由 Worker 执行 `git svn dcommit` |
| 自动提交钩子 | 任务状态事件钩子，不在每次本地 commit 后无条件 dcommit |
| 任务管理 | Spring Boot 状态机 + PostgreSQL |
| 执行日志 | 日志流/文件存储，数据库保存索引 |
| CI/CD | 复用 Drone |
| 镜像仓库 | 复用现有私有 Registry |
| 部署 | 复用 Docker Swarm |
| 生产发布 | 人工审批 |

---

## 17. 实施前需要验证的技术事项

正式开发前建议先做小型技术验证（POC）：

1. **Orca 接口验证**：确认当前 Orca 版本实际支持的 API、CLI 命令、任务状态查询、日志获取、取消能力和多用户运行方式。不要仅根据概念接口直接编码。
2. **BUG 项目匹配验证**：用真实 BUG 日志和项目注册信息验证匹配准确率，确定置信度阈值。
3. **Git-SVN 验证**：选一个真实 SVN 项目验证首次 clone、历史同步、分支映射、Worktree、dcommit、提交作者映射、`svn:externals` 和特殊属性。
4. **Git-SVN 并发验证**：验证多个工作区并行开发、单仓库串行 fetch/dcommit 的锁策略。
5. **远程执行验证**：验证网络中断、执行超时、服务重启、回调重放和任务恢复。
6. **安全验证**：验证 Agent 无法越权访问其他项目、生产凭据和宿主机敏感目录。
7. **CI 验证**：验证 Java 8、现有 Maven 项目和当前 Drone 流水线能否无改造或少量改造接入。

> 版本说明：Orca 的 API/CLI 能力可能随版本变化。最终以当前安装版本的官方文档和实际 POC 为准；业务系统应通过适配层隔离变化。

---

## 18. 验收指标

首期建议设置以下验收指标：

| 指标 | 目标 |
|---|---|
| BUG 自动分析成功率 | 可结构化输出问题类型、影响范围、疑似模块 |
| 项目自动匹配准确率 | 高置信自动匹配准确率 >= 90%，中低置信可人工确认 |
| 工作区创建成功率 | 指定项目下可稳定创建 Git/Git-SVN 工作区 |
| Orca 执行成功率 | 本地/远程执行器可提交、查询、获取日志和取消 |
| 测试闭环 | 测试失败可自动返回修复，保留每轮证据 |
| 审查闭环 | Review 独立执行，阻断项结构化记录 |
| 变更报告 | 每个任务生成可审查的代码差异、测试和审查报告 |
| 安全隔离 | Agent 无法越权访问其他项目和生产凭据 |
| Git-SVN 回写 | 审批后 dcommit 成功并记录 SVN revision |

---

## 19. 结论

这套平台建议采用 **Spring Boot 管理中心 + Orca 执行节点 + Git/Git-SVN 仓库适配 + Drone CI/CD** 的组合，并以 BUG 端到端自动化为首期闭环。

两个关键点是：

1. **SVN 不再单独维护一套 AI 开发流程**，通过 Git-SVN 将开发体验统一到 Git 分支和 Worktree，最后在审批后 `git svn dcommit` 回写 SVN。
2. **Spring Boot 不绑定 Orca 的部署位置**，通过 Local/Remote Executor 抽象，既能调用本地 Orca，也能调用远程 Orca；以后增加多个执行节点也不需要改造业务流程。

实施节奏建议：

```text
第一阶段：BUG 提交 -> AI 分析 -> 项目匹配 -> 工作区 -> Orca 修改 -> 测试 -> 审查 -> 变更报告
第二阶段：Git-SVN 审批后自动回写
第三阶段：远程 Orca 节点与多 Agent
第四阶段：Drone CI/CD、测试环境部署、验收
第五阶段：多项目规模化与指标优化
```

第一阶段只打通“BUG → 项目工作区 → Orca 修改 → 自动测试 → 代码审查 → 变更报告”。确认稳定后，再逐步开放 Git 推送、SVN dcommit、测试部署和生产发布。这样风险可控，也能尽早得到可用成果。