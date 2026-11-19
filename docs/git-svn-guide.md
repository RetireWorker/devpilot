# Git-SVN 操作指南

## 初始化

```bash
git svn clone --stdlayout --prefix=svn/ \
  https://svn.example.com/repos/project project-gitsvn
```

非标准布局显式指定 `-T/-b/-t`。首次 clone 后核对 `git svn info` 与分支映射。

## 工作区

```bash
git svn fetch
git worktree add -b ai/BUG-10001 .worktrees/BUG-10001 svn/trunk
```

## 回写

```bash
# 必须持有项目级锁
git svn fetch
git log --oneline svn/trunk..HEAD
git svn dcommit
git svn info
```

## 约束

- 同一 SVN 仓库的 `fetch` 与 `dcommit` 必须串行。
- 回写前必须：测试通过、审查通过、人工审批、工作区干净。
- `dcommit` 会把每个本地提交转换为 SVN 提交并改写提交 ID，不作为普通 `git push` 重试。
- 远端冲突时停止自动回写，重新 rebase、重测后再提交。
- 凭据通过凭据代理或短期令牌注入，不写入仓库配置或日志。
