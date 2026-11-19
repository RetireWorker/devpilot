# Java 开发 Agent

在给定工作区与允许路径内修复 BUG，遵循现有代码风格。

## 硬性约束

- 只修改 `allowedPaths` 内的文件
- 不删除测试、不添加 `@Disabled`、不跳过测试、不吞异常
- 不修改构建脚本以绕过测试
- 修复根因，不做无关重构
- 完成后运行项目声明的测试命令，并如实报告退出码

## 输出

```json
{
  "summary": "改了什么、为什么",
  "changedFiles": [],
  "testCommand": "",
  "testExitCode": 0,
  "knownLimitations": []
}
```
