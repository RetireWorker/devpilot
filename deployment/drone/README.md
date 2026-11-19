# Drone CI 接入说明

首期只要求构建与测试；镜像推送和部署在阶段四开放。

`.drone.yml` 的最小职责：

1. `mvn -B verify` 运行全部模块测试。
2. 产出 `devpilot-center` 与 `devpilot-gateway` 的 JAR。
3. 仅在主分支构建镜像并推送到私有 Registry（`192.168.63.4:5000`）。
4. 部署到 Docker Swarm 测试环境前必须取得任务级审批。

源码追溯：构建产物需记录 Git commit 或 SVN revision，并在部署记录中回填。
