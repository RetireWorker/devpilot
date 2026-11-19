package com.devpilot.project;

import com.devpilot.common.api.ProjectCatalog;
import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.domain.RepositoryType;
import com.devpilot.common.error.ConflictException;
import com.devpilot.common.error.NotFoundException;
import com.devpilot.common.error.ValidationException;
import com.devpilot.project.api.ProjectRequest;
import com.devpilot.project.domain.ProjectEntity;
import com.devpilot.project.domain.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProjectService implements ProjectCatalog {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectDescriptor> findById(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(projectId.trim()).map(this::toDescriptor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectDescriptor> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(this::toDescriptor).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDescriptor get(String projectId) {
        return findById(projectId).orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
    }

    @Transactional(readOnly = true)
    public List<ProjectDescriptor> list(boolean includeInactive) {
        var entities = includeInactive ? repository.findAll() : repository.findByActiveTrueOrderByNameAsc();
        return entities.stream().map(this::toDescriptor).toList();
    }

    public ProjectDescriptor create(ProjectRequest request) {
        var id = request.id().trim();
        if (repository.existsById(id)) {
            throw new ConflictException("项目已存在: " + id);
        }
        var entity = new ProjectEntity();
        entity.setId(id);
        apply(entity, request);
        return toDescriptor(repository.save(entity));
    }

    public ProjectDescriptor update(String projectId, ProjectRequest request) {
        var entity = repository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
        if (request.id() != null && !request.id().isBlank() && !request.id().trim().equals(projectId)) {
            throw new ValidationException("不允许修改项目 ID");
        }
        apply(entity, request);
        return toDescriptor(repository.save(entity));
    }

    public void deactivate(String projectId) {
        var entity = repository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("项目不存在: " + projectId));
        entity.setActive(false);
        repository.save(entity);
    }

    private void apply(ProjectEntity entity, ProjectRequest request) {
        entity.setName(request.name().trim());
        entity.setDescription(trimToNull(request.description()));
        entity.setAliases(normalizeList(request.aliases()));
        entity.setServiceNames(normalizeList(request.serviceNames()));
        entity.setLogPackages(normalizeList(request.logPackages()));
        entity.setCodePathKeywords(normalizeList(request.codePathKeywords()));
        entity.setTeam(trimToNull(request.team()));
        entity.setOwner(trimToNull(request.owner()));
        entity.setLanguage(trimToNull(request.language()));
        entity.setJavaVersion(trimToNull(request.javaVersion()));
        entity.setFramework(trimToNull(request.framework()));
        entity.setRepositoryType(request.repositoryType() == null ? RepositoryType.GIT : request.repositoryType());
        entity.setRepositoryUrl(trimToNull(request.repositoryUrl()));
        entity.setDefaultBranch(trimToNull(request.defaultBranch()));
        entity.setSvnUrl(trimToNull(request.svnUrl()));
        entity.setSvnLayout(trimToNull(request.svnLayout()));
        entity.setTargetRef(trimToNull(request.targetRef()));
        entity.setWorkspaceStrategy(trimToNull(request.workspaceStrategy()));
        entity.setWorkspaceRoot(trimToNull(request.workspaceRoot()));
        entity.setBuildCommandKey(trimToNull(request.buildCommandKey()));
        entity.setTestCommandKey(trimToNull(request.testCommandKey()));
        entity.setCreateMergeRequest(Boolean.TRUE.equals(request.createMergeRequest()));
        entity.setAutoPush(Boolean.TRUE.equals(request.autoPush()));
        entity.setRequireReview(request.requireReview() == null || request.requireReview());
        entity.setRequireApproval(request.requireApproval() == null || request.requireApproval());
        validateRepositoryConfig(entity);
    }

    private void validateRepositoryConfig(ProjectEntity entity) {
        if (entity.getRepositoryType() == RepositoryType.GIT && isBlank(entity.getRepositoryUrl())) {
            throw new ValidationException("Git 项目必须提供 repositoryUrl");
        }
        if (entity.getRepositoryType() == RepositoryType.GIT_SVN && isBlank(entity.getSvnUrl())) {
            throw new ValidationException("Git-SVN 项目必须提供 svnUrl");
        }
    }

    private ProjectDescriptor toDescriptor(ProjectEntity e) {
        return new ProjectDescriptor(
                e.getId(), e.getName(), e.getDescription(),
                List.copyOf(e.getAliases()), List.copyOf(e.getServiceNames()),
                List.copyOf(e.getLogPackages()), List.copyOf(e.getCodePathKeywords()),
                e.getTeam(), e.getOwner(), e.getLanguage(), e.getJavaVersion(), e.getFramework(),
                e.getRepositoryType(), e.getRepositoryUrl(), e.getDefaultBranch(),
                e.getSvnUrl(), e.getSvnLayout(), e.getTargetRef(),
                e.getWorkspaceStrategy(), e.getWorkspaceRoot(),
                e.getBuildCommandKey(), e.getTestCommandKey(),
                e.isCreateMergeRequest(), e.isAutoPush(), e.isRequireReview(), e.isRequireApproval(),
                e.isActive());
    }

    private static List<String> normalizeList(List<String> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
