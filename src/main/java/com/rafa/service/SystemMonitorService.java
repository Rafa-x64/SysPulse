package com.rafa.service;

import com.rafa.bridge.SystemBridge;
import com.rafa.model.SystemMetrics;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class SystemMonitorService {

    private final List<Consumer<SystemMetrics>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    public void onSnapshot(Consumer<SystemMetrics> listener) {
        listeners.add(listener);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sample, 0, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void sample() {
        try {
            var cpuBefore = SystemBridge.sampleCpuTimes();
            long diskTicksBefore = SystemBridge.sampleDiskIoTicks();
            Thread.sleep(500);
            var cpuAfter  = SystemBridge.sampleCpuTimes();
            long diskTicksAfter = SystemBridge.sampleDiskIoTicks();

            long idleDelta  = cpuAfter.idleTime() - cpuBefore.idleTime();
            long totalDelta = cpuAfter.total()    - cpuBefore.total();
            double cpuUsage = 100.0 * (1.0 - (double) idleDelta / totalDelta);

            long diskDelta = diskTicksAfter - diskTicksBefore;
            // delta is in ms, we slept for 500ms
            double diskUsage = Math.min(100.0, Math.max(0.0, (diskDelta / 500.0) * 100.0));

            var memInfo = SystemBridge.sampleMemInfo();
            var gpuUsage = SystemBridge.sampleGpuUsage();
            var processes = SystemBridge.getProcesses();
            
            // Sort processes by memory usage descending
            processes.sort((a, b) -> Long.compare(b.memKb(), a.memKb()));

            var metrics = new SystemMetrics(
                cpuUsage,
                memInfo[0], memInfo[1],
                diskUsage,
                gpuUsage,
                processes,
                Instant.now()
            );

            listeners.forEach(l -> l.accept(metrics));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
