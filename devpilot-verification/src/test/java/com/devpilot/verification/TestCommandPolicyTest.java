package com.devpilot.verification;

import com.devpilot.common.error.ValidationException;
import com.devpilot.verification.test.TestCommandPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestCommandPolicyTest {

    @Test
    void resolvesAllowlistedCommand() {
        assertThat(TestCommandPolicy.argv("maven-test"))
                .containsExactly("mvn", "-B", "-q", "test");
    }

    @Test
    void rejectsShellInjectionAttempt() {
        assertThatThrownBy(() -> TestCommandPolicy.argv("mvn test; rm -rf /"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> TestCommandPolicy.argv(" "))
                .isInstanceOf(ValidationException.class);
    }
}
