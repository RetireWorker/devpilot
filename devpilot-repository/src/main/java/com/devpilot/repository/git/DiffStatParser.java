package com.devpilot.repository.git;

import com.devpilot.common.api.DiffStat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 解析 `git diff --numstat` 输出（制表符分隔：增 删 文件）。
 * 二进制文件显示为 “-  -”，按 0 处理但仍计入文件数。
 */
public final class DiffStatParser {

    private static final Pattern NUMSTAT = Pattern.compile("^\\s*(\\d+|-)\\s+(\\d+|-)\\s+(.+)$");

    private DiffStatParser() {
    }

    public static DiffStat parseNumstat(String output) {
        var files = new ArrayList<String>();
        int insertions = 0;
        int deletions = 0;
        if (output == null || output.isBlank()) {
            return new DiffStat(List.of(), 0, 0, 0);
        }
        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            var matcher = NUMSTAT.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            files.add(matcher.group(3).trim());
            insertions += parseCount(matcher.group(1));
            deletions += parseCount(matcher.group(2));
        }
        return new DiffStat(List.copyOf(files), insertions, deletions, files.size());
    }

    private static int parseCount(String value) {
        return "-".equals(value) ? 0 : Integer.parseInt(value);
    }
}
