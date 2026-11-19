package com.devpilot.verification.test;

import com.devpilot.common.api.VerificationResultView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Aggregates Surefire XML reports. Exit code remains the authority: a missing
 * or unparsable report must never be treated as a pass.
 */
public final class SurefireReportParser {

    private SurefireReportParser() {
    }

    public record TestSummary(int tests, int failures, int errors, int skipped,
                              List<String> failedCases, List<String> reportFiles) {

        public String describe() {
            return "tests=%d failures=%d errors=%d skipped=%d".formatted(tests, failures, errors, skipped);
        }
    }

    public static TestSummary parse(Path reportDir, int exitCode) {
        if (reportDir == null || !Files.isDirectory(reportDir)) {
            return new TestSummary(0, 0, 0, 0, List.of(), List.of());
        }
        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        List<String> failedCases = new ArrayList<>();
        List<String> files = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(reportDir)) {
            List<Path> reports = paths
                    .filter(p -> p.getFileName().toString().startsWith("TEST-"))
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .toList();
            for (Path report : reports) {
                Document document = parseXml(report);
                if (document == null) {
                    continue;
                }
                files.add(report.getFileName().toString());
                NodeList suites = document.getElementsByTagName("testsuite");
                for (int i = 0; i < suites.getLength(); i++) {
                    Element suite = (Element) suites.item(i);
                    tests += intAttr(suite, "tests");
                    failures += intAttr(suite, "failures");
                    errors += intAttr(suite, "errors");
                    skipped += intAttr(suite, "skipped");
                    NodeList cases = suite.getElementsByTagName("testcase");
                    for (int j = 0; j < cases.getLength(); j++) {
                        Element testCase = (Element) cases.item(j);
                        if (testCase.getElementsByTagName("failure").getLength() > 0
                                || testCase.getElementsByTagName("error").getLength() > 0) {
                            failedCases.add(testCase.getAttribute("classname") + "#"
                                    + testCase.getAttribute("name"));
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取测试报告失败: " + e.getMessage(), e);
        }
        return new TestSummary(tests, failures, errors, skipped, failedCases, files);
    }

    private static Document parseXml(Path report) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(report.toFile());
        } catch (Exception e) {
            return null;
        }
    }

    private static int intAttr(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static VerificationResultView toVerification(int exitCode, TestSummary summary) {
        List<String> blocking = new ArrayList<>();
        if (exitCode != 0) {
            blocking.add("测试进程退出码非 0: " + exitCode);
        }
        if (summary.failures() > 0 || summary.errors() > 0) {
            blocking.add("失败用例: " + summary.failedCases());
        }
        if (summary.tests() == 0 && exitCode == 0) {
            blocking.add("没有任何测试报告或测试用例，不能视为通过");
        }
        return new VerificationResultView("TEST", blocking.isEmpty(), blocking, List.of(), List.of(),
                summary.describe());
    }
}
