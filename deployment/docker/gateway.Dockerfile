FROM eclipse-temurin:17-jre

ARG JAR_FILE=devpilot-gateway/target/devpilot-gateway-0.1.0-SNAPSHOT.jar

# Gateway 负责执行测试命令；镜像需提供 Maven 与基础工具，否则 maven-test
# 一类白名单命令在容器内无法运行。Orca CLI 仍通过挂载或独立层提供。
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       maven git curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY ${JAR_FILE} app.jar

# 该镜像只包含 Gateway。Maven/Node/Orca CLI 由基础镜像或宿主提供，
# 避免在运行镜像里执行不受控的包管理动作。
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
EXPOSE 18080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
