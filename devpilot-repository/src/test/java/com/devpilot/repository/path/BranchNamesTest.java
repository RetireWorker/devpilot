package com.devpilot.repository.path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BranchNamesTest {

    @Test
    void buildsSafeBranchFromTaskId() {
        assertThat(BranchNames.taskBranch("BUG-10001")).isEqualTo("ai/bug-10001");
        assertThat(BranchNames.taskBranch("BUG 10001/fix"))
                .isEqualTo("ai/bug-10001-fix");
    }

    @Test
    void stripsLeadingAndTrailingSeparators() {
        assertThat(BranchNames.taskBranch("--REQ--")).isEqualTo("ai/req");
    }

    @Test
    void collapsesRepeatedDashes() {
        assertThat(BranchNames.taskBranch("a___b")).isEqualTo("ai/a-b");
        assertThat(BranchNames.taskBranch("a - - b")).isEqualTo("ai/a-b");
    }

    @Test
    void rejectsEmptyOrDotOnly() {
        assertThatThrownBy(() -> BranchNames.taskBranch("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BranchNames.taskBranch(".."))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BranchNames.taskBranch("@#$"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
