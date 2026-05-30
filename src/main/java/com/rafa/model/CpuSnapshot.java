package com.rafa.model;

import java.time.Instant;

public record CpuSnapshot(double usagePercent, Instant capturedAt) {}
