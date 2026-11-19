package com.devpilot.repository.svn;

import com.devpilot.common.error.ValidationException;

import java.nio.file.Path;

/**
 * dcommit 前置检查：测试与审查必须已通过，且工作区不能有未提交修改。
 * 任何一项不满足都拒绝回写，不允许“先推上去再说”。
 */
public class SvnDcommitGuard {

    private final boolean testsPassed;
    private final boolean reviewPassed;

    public SvnDcommitGuard(boolean testsPassed, boolean reviewPassed) {
        this.testsPassed = testsPassed;
        this.reviewPassed = reviewPassed;
    }

    public void assertReady(boolean dirtyWorkingTree) {
        if (!testsPassed) {
            throw new ValidationException("测试未通过，禁止 git svn dcommit");
        }
        if (!reviewPassed) {
            throw new ValidationException("代码审查未通过，禁止 git svn dcommit");
        }
        if (dirtyWorkingTree) {
            throw new ValidationException("工作区存在未提交修改，禁止 git svn dcommit");
        }
    }
}
