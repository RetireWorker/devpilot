# devpilot-repository

Git 与 Git-SVN 适配层。负责镜像仓库、任务 worktree、差异统计、提交，以及 Git-SVN 的 fetch/dcommit。

## 安全边界

- 所有命令通过 `ProcessBuilder` 以 **argv 列表**执行，禁止拼接 shell 字符串，杜绝命令注入。
- 所有落盘路径经 `PathGuard` 校验：必须是配置根目录下的相对路径，拒绝绝对路径与 `..` 逃逸。
- 任务分支固定为 `ai/<taskId>`，`BranchNames` 只保留 `[a-z0-9._-]`，并拒绝空值与纯点值。
- 命令统一设置超时（默认 10 分钟），超时强制终止并返回明确的超时错误。

## 原生 Git 流程

```text
ensureMirror(projectId, repositoryUrl, defaultBranch)   # git clone --mirror / git fetch
        |
createWorktree(..., mirror, baseRef, "ai/<taskId>")     # git worktree add -b
        |
Agent 在 worktree 内修改代码
        |
diffStat / hasUncommittedChanges / currentHead
        |
commitAll(worktree, ...)                                # git add -A + git commit
```

同一仓库多个任务并行时每个任务一个 worktree，互不污染；镜像仓库只存放引用。

## Git-SVN 流程与并发规则

```text
ensureMirror(projectId, svnUrl, layout, trunk, branches, tags)
        |
createWorktree(..., mirror, targetRef, "ai/<taskId>")
        |
测试 + 审查 + 审批均通过
        |
dcommit(worktree, mirror, svnRemoteRef, new SvnDcommitGuard(testsPassed, reviewPassed))
        |
        +-- 获取仓库级锁（tryAcquire，失败即报“仓库忙”）
        +-- git svn fetch
        +-- 校验提交范围（非空、无 merge 提交）
        +-- git svn dcommit
        +-- git svn info 读取 revision
        +-- finally 释放锁
```

关键约束：

1. **同一 SVN 仓库的 fetch 与 dcommit 必须串行**，`InMemoryRepositoryLock` 按镜像绝对路径加锁。
2. 锁是**非重入**的：同一线程重复 `tryAcquire` 会失败，避免把锁误当可重入锁。
3. `SvnDcommitGuard` 在取锁前就校验测试、审查与工作区干净状态，任何一项不满足都不执行 dcommit。
4. dcommit 失败或抛异常时，`finally` 保证释放锁，不会把仓库永久锁死。
5. 待回写提交包含 merge 提交时拒绝 dcommit，要求先整理提交历史。

> 多实例部署时，进程内锁不足以保证互斥，`RepositoryLock` 接口预留了替换为数据库锁或分布式锁的空间。

## 配置

```yaml
ai:
  repository:
    root: data/workspaces
    mirror-root: data/mirrors
    git-bin: git
    svn-bin: svn
    command-timeout: PT10M
```

## 测试

`GitCommandRunner` 可注入，测试使用假执行器与临时目录，不访问网络或真实 SVN：

- 路径穿越拒绝、分支清洗、numstat 解析
- dcommit 前置守卫、提交范围校验
- 并发 dcommit 互斥、失败时锁释放、revision 解析
