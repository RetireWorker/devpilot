package com.devpilot.repository.svn;

import com.devpilot.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvnDcommitGuardTest {

    @Test
    void allowsOnlyWhenTestsReviewAndTreeAreClean() {
        assertThatCode(() -> new SvnDcommitGuard(true, true).assertReady(false))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFailedTests() {
        assertThatThrownBy(() -> new SvnDcommitGuard(false, true).assertReady(false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("测试未通过");
    }

    @Test
    void rejectsFailedReview() {
        assertThatThrownBy(() -> new SvnDcommitGuard(true, false).assertReady(false))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("代码审查未通过");
    }

    @Test
    void rejectsDirtyWorkingTree() {
        assertThatThrownBy(() -> new SvnDcommitGuard(true, true).assertReady(true))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("未提交");
    }
}
