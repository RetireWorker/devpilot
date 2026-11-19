package com.devpilot.center.wiring;

import com.devpilot.common.domain.RepositoryType;
import com.devpilot.center.config.ProjectRegistryProperties;
import com.devpilot.project.ProjectService;
import com.devpilot.project.api.ProjectRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 启动时把 project-registry/*.yaml 导入项目注册表。按项目 ID 幂等，
 * 已存在则跳过，避免覆盖运行期人工修改。
 */
@Component
public class ProjectRegistryImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectRegistryImporter.class);

    private final ProjectRegistryProperties properties;
    private final ProjectService projectService;

    public ProjectRegistryImporter(ProjectRegistryProperties properties,
                                   ProjectService projectService) {
        this.properties = properties;
        this.projectService = projectService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String dir = properties.getImportDir();
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path root = Path.of(dir);
        if (!Files.isDirectory(root)) {
            log.warn("项目注册导入目录不存在，跳过: {}", root.toAbsolutePath());
            return;
        }
        try (var files = Files.walk(root, 2)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yaml") || path.toString().endsWith(".yml"))
                    .sorted()
                    .forEach(this::importFile);
        } catch (Exception e) {
            log.error("扫描项目注册目录失败: {}", root, e);
        }
    }

    private void importFile(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            Object loaded = new Yaml().load(input);
            if (!(loaded instanceof Map<?, ?> root)) {
                log.warn("跳过格式不正确的注册文件: {}", file);
                return;
            }
            ProjectRequest request = toRequest(root);
            if (request.id() == null || request.id().isBlank()) {
                log.warn("跳过缺少 project.id 的注册文件: {}", file);
                return;
            }
            if (projectService.findById(request.id()).isPresent()) {
                log.debug("项目已存在，跳过导入: {}", request.id());
                return;
            }
            projectService.create(request);
            log.info("已从 {} 导入项目 {}", file.getFileName(), request.id());
        } catch (Exception e) {
            // 单个文件失败不阻塞启动，避免一个格式错误导致整个平台不可用。
            log.error("导入项目注册文件失败: {}", file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private ProjectRequest toRequest(Map<?, ?> root) {
        Map<String, Object> project = section(root, "project");
        Map<String, Object> runtime = section(root, "runtime");
        Map<String, Object> repository = section(root, "repository");
        Map<String, Object> build = section(root, "build");
        Map<String, Object> delivery = section(root, "delivery");

        return new ProjectRequest(
                str(project, "id"),
                str(project, "name"),
                str(project, "description"),
                list(project, "aliases"),
                list(project, "serviceNames"),
                list(project, "logPackages"),
                list(project, "codePathKeywords"),
                str(project, "team"),
                str(project, "owner"),
                str(runtime, "language"),
                str(runtime, "javaVersion"),
                str(runtime, "framework"),
                repositoryType(str(repository, "type")),
                firstNonNull(str(repository, "url"), str(repository, "repositoryUrl")),
                str(repository, "defaultBranch"),
                str(repository, "svnUrl"),
                str(repository, "layout"),
                str(repository, "targetRef"),
                str(repository, "workspaceStrategy"),
                str(repository, "workspaceRoot"),
                str(build, "commandKey"),
                str(build, "testCommandKey"),
                bool(delivery, "createMergeRequest"),
                bool(delivery, "autoPush"),
                bool(delivery, "requireReview"),
                bool(delivery, "requireApproval"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<?, ?> root, String key) {
        Object value = root.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonNull(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(item -> item != null).map(String::valueOf).toList();
    }

    private static Boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private static RepositoryType repositoryType(String value) {
        if (value == null || value.isBlank()) {
            return RepositoryType.GIT;
        }
        return RepositoryType.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
