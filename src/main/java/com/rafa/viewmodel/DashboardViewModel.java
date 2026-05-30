package com.rafa.viewmodel;

import com.rafa.model.ProcessSnapshot;
import com.rafa.model.SystemMetrics;
import com.rafa.service.SystemMonitorService;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class DashboardViewModel {

    private Runnable onTick;
    public void setOnTick(Runnable onTick) { this.onTick = onTick; }

    public final DoubleProperty cpuUsage = new SimpleDoubleProperty(-1.0);
    public final DoubleProperty ramUsage = new SimpleDoubleProperty(-1.0);
    public final DoubleProperty gpuUsage = new SimpleDoubleProperty(-1.0);
    public final DoubleProperty diskUsage = new SimpleDoubleProperty(-1.0);
    
    public final ObservableList<ProcessSnapshot> processes = FXCollections.observableArrayList();

    private final SystemMonitorService monitorService = new SystemMonitorService();

    public void start() {
        monitorService.onSnapshot(metrics ->
            Platform.runLater(() -> {
                cpuUsage.set(metrics.cpuUsagePercent());
                ramUsage.set(metrics.ramUsagePercent());
                gpuUsage.set(metrics.gpuUsagePercent());
                diskUsage.set(metrics.diskUsagePercent());
                
                // Update processes efficiently
                processes.setAll(metrics.processes());
                if (onTick != null) onTick.run();
            })
        );
        monitorService.start();
    }

    public void stop() {
        monitorService.stop();
    }
}
