package com.devpilot.verification;

import com.devpilot.common.api.VerificationResultView;
import com.devpilot.verification.test.SurefireReportParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SurefireReportParserTest {

    @TempDir
    Path tempDir;

    private void writeReport(String name, String content) throws Exception {
        Files.writeString(tempDir.resolve(name), content);
    }

    @Test
    void parsesFailuresAndErrors() throws Exception {
        writeReport("TEST-a.xml", """
                <testsuite tests="3" failures="1" errors="0" skipped="1">
                  <testcase classname="A" name="ok"/>
                  <testcase classname="A" name="bad"><failure/></testcase>
                  <testcase classname="A" name="skip"><skipped/></testcase>
                </testsuite>
                """);

        var summary = SurefireReportParser.parse(tempDir, 1);
        assertThat(summary.tests()).isEqualTo(3);
        assertThat(summary.failures()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failedCases()).containsExactly("A#bad");
        assertThat(SurefireReportParser.toVerification(1, summary).passed()).isFalse();
    }

    @Test
    void zeroTestsIsNotAPass() {
        var summary = SurefireReportParser.parse(tempDir.resolve("missing"), 0);
        VerificationResultView result = SurefireReportParser.toVerification(0, summary);
        assertThat(result.passed()).isFalse();
        assertThat(result.blocking()).anyMatch(s -> s.contains("没有任何测试报告"));
    }

    @Test
    void greenReportPasses() throws Exception {
        writeReport("TEST-b.xml", """
                <testsuite tests="2" failures="0" errors="0" skipped="0">
                  <testcase classname="B" name="one"/>
                  <testcase classname="B" name="two"/>
                </testsuite>
                """);

        var summary = SurefireReportParser.parse(tempDir, 0);
        assertThat(SurefireReportParser.toVerification(0, summary).passed()).isTrue();
    }

    @Test
    void malformedReportIsSkippedNotCountedAsPass() throws Exception {
        writeReport("TEST-c.xml", "<broken");
        var summary = SurefireReportParser.parse(tempDir, 0);
        assertThat(summary.tests()).isZero();
        assertThat(SurefireReportParser.toVerification(0, summary).passed()).isFalse();
    }
}
