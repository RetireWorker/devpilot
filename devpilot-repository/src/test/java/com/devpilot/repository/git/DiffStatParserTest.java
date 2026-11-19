package com.devpilot.repository.git;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiffStatParserTest {

    @Test
    void parsesNumstatOutput() {
        var output = "12\t3\tsrc/main/java/com/example/Foo.java\n"
                + "0\t7\tsrc/test/java/com/example/FooTest.java\n"
                + "-\t-\tassets/logo.png\n";

        var stat = DiffStatParser.parseNumstat(output);

        assertThat(stat.fileCount()).isEqualTo(3);
        assertThat(stat.insertions()).isEqualTo(12);
        assertThat(stat.deletions()).isEqualTo(10);
        assertThat(stat.files()).containsExactly(
                "src/main/java/com/example/Foo.java",
                "src/test/java/com/example/FooTest.java",
                "assets/logo.png");
    }

    @Test
    void handlesBlankAndMalformedLines() {
        var stat = DiffStatParser.parseNumstat("\nnot a numstat line\n");

        assertThat(stat.fileCount()).isZero();
        assertThat(stat.files()).isEmpty();
    }

    @Test
    void handlesEmptyOutput() {
        assertThat(DiffStatParser.parseNumstat(null).fileCount()).isZero();
        assertThat(DiffStatParser.parseNumstat("").insertions()).isZero();
    }
}
