package com.devpilot.center.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai.project-registry")
public class ProjectRegistryProperties {

    /**
     * 启动时导入的 YAML 目录。为空表示不导入；已存在的项目 ID 会被跳过。
     */
    private String importDir = "";

    public String getImportDir() {
        return importDir;
    }

    public void setImportDir(String importDir) {
        this.importDir = importDir;
    }
}
