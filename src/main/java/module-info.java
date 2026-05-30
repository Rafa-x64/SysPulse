module com.rafa {
    requires javafx.controls;
    requires javafx.graphics;
    exports com.rafa;
    opens com.rafa to javafx.graphics;
}