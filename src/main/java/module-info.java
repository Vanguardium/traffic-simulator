module com.oblig.obj_oblig_2 {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.oblig.obj_oblig_2 to javafx.fxml;
    exports com.oblig.obj_oblig_2;
}