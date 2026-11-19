package com.devpilot.common.api;

import java.util.List;

public record DiffStat(List<String> files, int insertions, int deletions, int fileCount) {
}
