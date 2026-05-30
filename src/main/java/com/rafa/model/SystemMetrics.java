package com.rafa.model;

import java.time.Instant;
import java.util.List;

public record SystemMetrics(
    double cpuUsagePercent,
    long ramTotalKb,
    long ramAvailableKb,
    double diskUsagePercent,
    double gpuUsagePercent,
    List<ProcessSnapshot> processes,
    Instant capturedAt
) {
    public double ramUsagePercent() {
        if (ramTotalKb == 0) return 0.0;
        return 100.0 * (1.0 - (double) ramAvailableKb / ramTotalKb);
    }
}
