package com.rafa.model;

public record RawCpuTimes(
    long user, long nice, long system,
    long idle, long iowait, long irq, long softirq
) {
    public long total()    { return user + nice + system + idle + iowait + irq + softirq; }
    public long idleTime() { return idle + iowait; }
}
