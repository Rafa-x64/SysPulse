package com.rafa;

import com.rafa.view.DashboardView;
import com.rafa.viewmodel.DashboardViewModel;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private final DashboardViewModel viewModel = new DashboardViewModel();

    @Override
    public void start(Stage stage) {
        var view  = new DashboardView(viewModel);
        var scene = new Scene(view.root, 900, 600);
        scene.getStylesheets().add(
            getClass().getResource("/com/rafa/styles.css").toExternalForm()
        );

        stage.setTitle("SysPulse");
        stage.setMinWidth(480);
        stage.setMinHeight(320);
        stage.setScene(scene);
        stage.show();

        viewModel.start();
    }

    @Override
    public void stop() {
        viewModel.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}