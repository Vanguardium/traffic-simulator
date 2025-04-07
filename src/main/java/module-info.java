module com.oblig.obj_oblig_2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fasterxml.jackson.databind;


    opens com.oblig.obj_oblig_2 to javafx.fxml;
    exports com.oblig.obj_oblig_2;
}