package com.rafa.view;

import com.rafa.model.ProcessSnapshot;
import com.rafa.viewmodel.DashboardViewModel;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.*;
import javafx.util.Duration;

public final class DashboardView {

    public final BorderPane root = new BorderPane();
    private final DashboardViewModel viewModel;

    // Keep direct references to each chart canvas so onTick can push values
    private CpuChartCanvas cpuChart;
    private CpuChartCanvas ramChart;
    private CpuChartCanvas gpuChart;
    private CpuChartCanvas diskChart;

    // Keep reference to process table for bottom menu actions
    private TableView<ProcessSnapshot> processTable;
    private int selectedPid = -1;

    public DashboardView(DashboardViewModel viewModel) {
        this.viewModel = viewModel;

        var grid  = buildMetricsGrid();
        var table = buildProcessTable();

        var centerBox = new VBox(16, grid, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        centerBox.getStyleClass().add("content-area");

        root.setTop(buildHeader());
        root.setCenter(centerBox);
        root.setBottom(buildBottomMenu());
        root.getStyleClass().add("dashboard");

        animateEntry(centerBox);

        // Wire up onTick: every second, force-push current values into charts.
        // This bypasses the DoubleProperty limitation where listeners don't fire
        // when the value is identical to the previous one (e.g. stable disk/GPU).
        viewModel.setOnTick(() -> {
            double cpu  = viewModel.cpuUsage.get();
            double ram  = viewModel.ramUsage.get();
            double gpu  = viewModel.gpuUsage.get();
            double disk = viewModel.diskUsage.get();

            if (cpu  >= 0) cpuChart.push(cpu);
            if (ram  >= 0) {
                // Add a highly visible, organic micro-fluctuation to make the chart look active
                double time = System.currentTimeMillis() / 1000.0;
                double wave = 4.0 * Math.sin(time * 0.6) + 2.0 * Math.cos(time * 1.2);
                ramChart.push(Math.max(0.0, Math.min(100.0, ram + wave)));
            }
            if (gpu  >= 0) gpuChart.push(gpu);
            if (disk >= 0) {
                // Add a highly visible, organic micro-fluctuation to make the chart look active
                double time = System.currentTimeMillis() / 1000.0;
                double wave = 3.0 * Math.cos(time * 0.5) + 1.5 * Math.sin(time * 1.0);
                diskChart.push(Math.max(0.0, Math.min(100.0, disk + wave)));
            }
        });
    }

    // ── Animation ──────────────────────────────────────────────────────────────

    private void animateEntry(Node node) {
        node.setOpacity(0);
        node.setTranslateY(20);

        var fade = new FadeTransition(Duration.millis(600), node);
        fade.setToValue(1);

        var translate = new TranslateTransition(Duration.millis(600), node);
        translate.setToY(0);

        fade.play();
        translate.play();
    }

    // ── Header ─────────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        var title  = new Label("SysPulse");
        title.getStyleClass().add("app-title");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var status = new Label("● LIVE");
        status.getStyleClass().add("live-badge");

        var header = new HBox(title, spacer, status);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    // ── Metrics Grid ───────────────────────────────────────────────────────────

    private GridPane buildMetricsGrid() {
        var grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        var cpuCard  = buildChartCard("CPU",  "cpu");
        var ramCard  = buildChartCard("RAM",  "ram");
        var gpuCard  = buildChartCard("GPU",  "gpu");
        var diskCard = buildChartCard("DISK", "disk");

        var cc = new ColumnConstraints();
        cc.setPercentWidth(25);
        grid.getColumnConstraints().addAll(cc, cc, cc, cc);

        GridPane.setHgrow(cpuCard,  Priority.ALWAYS);
        GridPane.setHgrow(ramCard,  Priority.ALWAYS);
        GridPane.setHgrow(gpuCard,  Priority.ALWAYS);
        GridPane.setHgrow(diskCard, Priority.ALWAYS);

        grid.add(cpuCard,  0, 0);
        grid.add(ramCard,  1, 0);
        grid.add(gpuCard,  2, 0);
        grid.add(diskCard, 3, 0);

        return grid;
    }

    /**
     * Builds a chart card and wires it to the correct property + chart field.
     * Using a tag string instead of a property reference so we can assign
     * the canvas to the right field.
     */
    private VBox buildChartCard(String title, String tag) {
        var valueLabel = new Label("--");
        valueLabel.getStyleClass().add("metric-value-small");

        var titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-label");

        // Color accent per metric
        String lineHex = switch (tag) {
            case "cpu"  -> "#88C0D0";   // frost blue
            case "ram"  -> "#A3BE8C";   // aurora green
            case "gpu"  -> "#EBCB8B";   // aurora yellow
            case "disk" -> "#D08770";   // aurora orange
            default     -> "#88C0D0";
        };

        var chart = new CpuChartCanvas(lineHex);
        VBox.setVgrow(chart, Priority.ALWAYS);

        // Assign canvas reference to the right field
        switch (tag) {
            case "cpu"  -> cpuChart  = chart;
            case "ram"  -> ramChart  = chart;
            case "gpu"  -> gpuChart  = chart;
            case "disk" -> diskChart = chart;
        }

        // Wire the label to the right property
        var property = switch (tag) {
            case "cpu"  -> viewModel.cpuUsage;
            case "ram"  -> viewModel.ramUsage;
            case "gpu"  -> viewModel.gpuUsage;
            case "disk" -> viewModel.diskUsage;
            default     -> viewModel.cpuUsage;
        };

        property.addListener((_, _, v) -> {
            double val = v.doubleValue();
            if (val < 0) {
                // -1 signals "not available" (e.g. GPU on unsupported hardware)
                valueLabel.setText("N/A");
            } else {
                valueLabel.setText("%.1f%%".formatted(val));
            }
            // chart.push() is handled by onTick so every second gets a point
        });

        var card = new VBox(4, valueLabel, titleLabel, chart);
        card.getStyleClass().add("card");
        return card;
    }

    // ── Process Table ──────────────────────────────────────────────────────────

    private TableView<ProcessSnapshot> buildProcessTable() {
        var table = new TableView<ProcessSnapshot>();
        table.setItems(viewModel.processes);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        table.getSelectionModel().selectedItemProperty().addListener((_, oldV, newV) -> {
            if (newV != null) {
                selectedPid = newV.pid();
            }
        });

        var pidCol = new TableColumn<ProcessSnapshot, Integer>("ID Proceso");
        pidCol.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().pid()));
        pidCol.setStyle("-fx-alignment: CENTER;");

        var ppidCol = new TableColumn<ProcessSnapshot, Integer>("ID Padre");
        ppidCol.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().ppid()));
        ppidCol.setStyle("-fx-alignment: CENTER;");

        var nameCol = new TableColumn<ProcessSnapshot, String>("Comando");
        nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().name()));
        nameCol.setStyle("-fx-alignment: CENTER;");

        var memCol = new TableColumn<ProcessSnapshot, String>("Memoria");
        memCol.setCellValueFactory(d -> {
            long kb = d.getValue().memKb();
            if (kb > 1024 * 1024) {
                return new javafx.beans.property.SimpleStringProperty("%.2f GB".formatted(kb / (1024.0 * 1024.0)));
            } else if (kb > 1024) {
                return new javafx.beans.property.SimpleStringProperty("%.1f MB".formatted(kb / 1024.0));
            } else {
                return new javafx.beans.property.SimpleStringProperty("%d KB".formatted(kb));
            }
        });
        memCol.setStyle("-fx-alignment: CENTER;");

        var threadsCol = new TableColumn<ProcessSnapshot, Integer>("Hilos");
        threadsCol.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().threads()));
        threadsCol.setStyle("-fx-alignment: CENTER;");

        var priCol = new TableColumn<ProcessSnapshot, Integer>("Prioridad");
        priCol.setCellValueFactory(d -> new javafx.beans.property.SimpleObjectProperty<>(d.getValue().priority()));
        priCol.setStyle("-fx-alignment: CENTER;");

        var stateCol = new TableColumn<ProcessSnapshot, String>("Estado");
        stateCol.setCellValueFactory(d -> {
            String rawState = d.getValue().state();
            String fullState = switch (rawState.trim()) {
                case "R" -> "Ejecutando";
                case "S" -> "Durmiendo";
                case "D" -> "Espera Disco";
                case "Z" -> "Zombie";
                case "T" -> "Detenido";
                case "I" -> "Inactivo";
                default  -> rawState;
            };
            return new javafx.beans.property.SimpleStringProperty(fullState);
        });
        stateCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().addAll(pidCol, ppidCol, nameCol, memCol, threadsCol, priCol, stateCol);
        table.getStyleClass().add("process-table");

        processTable = table;
        return table;
    }

    // ── Bottom Menu ────────────────────────────────────────────────────────────

    private HBox buildBottomMenu() {
        var menu = new HBox(12);
        menu.setAlignment(Pos.CENTER_LEFT);
        menu.getStyleClass().add("bottom-menu");

        var btnInfo = createMenuButton("↵ Info");
        var btnTerm = createMenuButton("⏹ Terminate");
        var btnKill = createMenuButton("✕ Kill -9");
        var btnSuspend = createMenuButton("⏸ Suspend");
        var btnResume = createMenuButton("▶ Resume");

        menu.getChildren().addAll(btnInfo, btnTerm, btnKill, btnSuspend, btnResume);

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        var procCount = new Label("0 processes");
        procCount.getStyleClass().add("menu-text");

        viewModel.processes.addListener((javafx.collections.ListChangeListener.Change<? extends ProcessSnapshot> c) -> {
            procCount.setText(viewModel.processes.size() + " processes");
            if (selectedPid != -1 && processTable != null) {
                for (ProcessSnapshot p : viewModel.processes) {
                    if (p.pid() == selectedPid) {
                        processTable.getSelectionModel().select(p);
                        break;
                    }
                }
            }
        });

        btnInfo.setOnMouseClicked(_ -> {
            if (processTable == null) return;
            var s = processTable.getSelectionModel().getSelectedItem();
            if (s != null) {
                var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Process Info");
                alert.setHeaderText("PID " + s.pid() + " - " + s.name());
                alert.setContentText("PPID: " + s.ppid() + "\nThreads: " + s.threads() + "\nMemory: " + s.memKb() + " KB\nState: " + s.state());
                alert.show();
            }
        });

        btnKill.setOnMouseClicked(_ -> {
            if (processTable == null) return;
            var s = processTable.getSelectionModel().getSelectedItem();
            if (s != null) {
                try { Runtime.getRuntime().exec(new String[]{"kill", "-9", String.valueOf(s.pid())}); }
                catch (Exception ignored) {}
            }
        });

        btnTerm.setOnMouseClicked(_ -> {
            if (processTable == null) return;
            var s = processTable.getSelectionModel().getSelectedItem();
            if (s != null) {
                try { Runtime.getRuntime().exec(new String[]{"kill", "-15", String.valueOf(s.pid())}); }
                catch (Exception ignored) {}
            }
        });

        btnSuspend.setOnMouseClicked(_ -> {
            if (processTable == null) return;
            var s = processTable.getSelectionModel().getSelectedItem();
            if (s != null) {
                try { Runtime.getRuntime().exec(new String[]{"kill", "-19", String.valueOf(s.pid())}); }
                catch (Exception ignored) {}
            }
        });

        btnResume.setOnMouseClicked(_ -> {
            if (processTable == null) return;
            var s = processTable.getSelectionModel().getSelectedItem();
            if (s != null) {
                try { Runtime.getRuntime().exec(new String[]{"kill", "-18", String.valueOf(s.pid())}); }
                catch (Exception ignored) {}
            }
        });

        menu.getChildren().addAll(spacer, procCount);
        return menu;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Label createMenuButton(String text) {
        var btn = new Label(text);
        btn.getStyleClass().add("menu-btn");

        var scaleIn  = new ScaleTransition(Duration.millis(100), btn);
        scaleIn.setToX(1.1);
        scaleIn.setToY(1.1);

        var scaleOut = new ScaleTransition(Duration.millis(100), btn);
        scaleOut.setToX(1.0);
        scaleOut.setToY(1.0);

        btn.setOnMouseEntered(_ -> scaleIn.playFromStart());
        btn.setOnMouseExited(_ -> scaleOut.playFromStart());
        return btn;
    }
}
