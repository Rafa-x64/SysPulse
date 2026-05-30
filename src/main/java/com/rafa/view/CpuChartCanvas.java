package com.rafa.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.*;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A scrolling time-series chart canvas.
 *
 * Uses dynamic auto-scaling: the Y-axis adapts to the actual data range,
 * ensuring that even small fluctuations are visible. A minimum window of
 * MIN_WINDOW_PCT (10%) is enforced so a perfectly flat signal still looks
 * reasonable and centred.
 */
public final class CpuChartCanvas extends Pane {

    private static final int    MAX_POINTS     = 60;
    /** Minimum vertical range shown (%), so flat signals are centred nicely. */
    private static final double MIN_WINDOW_PCT = 10.0;
    /** Extra padding above/below the data range (fraction of range). */
    private static final double PADDING_FRAC   = 0.20;

    private final Color lineColor;
    private final Color fillTop;
    private final Color fillBot;
    private final Color glowColor;

    private final Canvas        canvas  = new Canvas();
    private final Deque<Double> history = new ArrayDeque<>(MAX_POINTS + 1);

    public CpuChartCanvas() {
        this("#88C0D0");
    }

    public CpuChartCanvas(String hexColor) {
        this.lineColor = Color.web(hexColor);
        this.fillTop   = Color.web(hexColor, 0.30);
        this.fillBot   = Color.web(hexColor, 0.00);
        this.glowColor = Color.web(hexColor, 0.15);

        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(__ -> render());
        canvas.heightProperty().addListener(__ -> render());
    }

    /** Append a new value (0-100) and redraw. */
    public void push(double value) {
        if (history.isEmpty()) {
            for (int i = 0; i < MAX_POINTS; i++) {
                history.addLast(value);
            }
        } else {
            if (history.size() >= MAX_POINTS) history.pollFirst();
            history.addLast(value);
        }
        render();
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        gc.clearRect(0, 0, w, h);

        // Fixed Y range from 0% to 100% for system metrics
        double yMin = 0.0, yMax = 100.0;

        drawGridLines(gc, w, h, yMin, yMax);

        if (history.size() < 2) return;

        double[] v    = history.stream().mapToDouble(Double::doubleValue).toArray();
        double   step = w / (MAX_POINTS - 1);

        drawFill(gc, v, step, w, h, yMin, yMax);
        drawLine(gc, v, step, h, yMin, yMax);
    }

    private double[] computeRange() {
        return new double[]{0.0, 100.0};
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private void drawLine(GraphicsContext gc, double[] v, double step,
                          double h, double yMin, double yMax) {
        gc.setStroke(lineColor);
        gc.setLineWidth(2.0);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.setEffect(new javafx.scene.effect.DropShadow(4, 0, 0, glowColor));
        gc.beginPath();
        for (int i = 0; i < v.length; i++) {
            double x = i * step;
            double y = toCanvasY(v[i], h, yMin, yMax);
            if (i == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
        }
        gc.stroke();
        gc.setEffect(null);
    }

    private void drawFill(GraphicsContext gc, double[] v, double step,
                          double w, double h, double yMin, double yMax) {
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, fillTop), new Stop(1, fillBot)));
        gc.beginPath();
        gc.moveTo(0, h);                             // bottom-left
        for (int i = 0; i < v.length; i++)
            gc.lineTo(i * step, toCanvasY(v[i], h, yMin, yMax));
        gc.lineTo((v.length - 1) * step, h);        // bottom-right
        gc.closePath();
        gc.fill();
    }

    private void drawGridLines(GraphicsContext gc, double w, double h,
                               double yMin, double yMax) {
        gc.setStroke(Color.web("#434C5E", 0.45));
        gc.setLineWidth(0.5);
        gc.setLineDashes(3, 4);
        // Draw 3 evenly-spaced grid lines within the visible range
        double span = yMax - yMin;
        for (int i = 1; i <= 3; i++) {
            double pct = yMin + span * (i / 4.0);
            double y   = toCanvasY(pct, h, yMin, yMax);
            gc.strokeLine(0, y, w, y);
        }
        gc.setLineDashes();
    }

    /**
     * Map a percentage value to a canvas Y coordinate using the current
     * auto-scaled [yMin, yMax] window with 5% top/bottom padding.
     */
    private double toCanvasY(double pct, double h, double yMin, double yMax) {
        double normalised = (pct - yMin) / (yMax - yMin);
        // normalised=1 → top (with 5% margin), normalised=0 → bottom (with 5% margin)
        return h - normalised * h * 0.90 - h * 0.05;
    }
}
