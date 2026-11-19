package com.devpilot.center.wiring;

import com.devpilot.common.api.ProjectDescriptor;
import com.devpilot.common.domain.RepositoryType;
import com.devpilot.common.error.ValidationException;
import com.devpilot.repository.git.GitRepositoryAdapter;
import com.devpilot.repository.svn.GitSvnRepositoryAdapter;
import com.devpilot.task.port.ProjectRepositoryPort;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 镜像准备适配器：Git 项目 clone --mirror，Git-SVN 项目建立 git-svn 镜像。
 */
@Component
public class ProjectRepositoryAdapter implements ProjectRepositoryPort {

    private final GitRepositoryAdapter gitAdapter;
    private final GitSvnRepositoryAdapter svnAdapter;

    public ProjectRepositoryAdapter(GitRepositoryAdapter gitAdapter,
                                    GitSvnRepositoryAdapter svnAdapter) {
        this.gitAdapter = gitAdapter;
        this.svnAdapter = svnAdapter;
    }

    @Override
    public String ensureMirror(ProjectDescriptor project) {
        if (project.repositoryType() == RepositoryType.GIT_SVN) {
            Path mirror = svnAdapter.ensureMirror(project.id(), project.svnUrl(),
                    project.svnLayout(), "trunk", "branches", "tags");
            return mirror.toAbsolutePath().toString();
        }
        if (project.repositoryType() != RepositoryType.GIT) {
            throw new ValidationException("不支持的仓库类型: " + project.repositoryType());
        }
        Path mirror = gitAdapter.ensureMirror(project.id(), project.repositoryUrl(),
                project.defaultBranch());
        return mirror.toAbsolutePath().toString();
    }
}
