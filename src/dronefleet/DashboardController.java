package dronefleet;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

public class DashboardController {

    @FXML private TableView<Drone> droneTable;
    @FXML private TableColumn<Drone, String> colId;
    @FXML private TableColumn<Drone, String> colModel;
    @FXML private TableColumn<Drone, String> colStatus;
    @FXML private TableColumn<Drone, Integer> colBattery;
    @FXML private TableColumn<Drone, Double> colPayload;

    private ObservableList<Drone> droneList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colModel.setCellValueFactory(new PropertyValueFactory<>("model"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        
        // Asigură-te că ai adăugat aceste coloane și în dashboard.fxml dacă vrei să le vezi!
        if(colBattery != null) colBattery.setCellValueFactory(new PropertyValueFactory<>("batteryLevel"));
        if(colPayload != null) colPayload.setCellValueFactory(new PropertyValueFactory<>("maxPayload"));

        // Date actualizate cu noii parametri
        droneList.add(new Drone("DR-01", "DJI Mavic 3", "Disponibil", 100, 2.5));
        droneList.add(new Drone("DR-02", "Matrice 300", "In Zbor", 45, 10.0));
        droneList.add(new Drone("DR-03", "Phantom 4", "Mentenanta", 0, 1.5));
        
        droneTable.setItems(droneList);
    }

    @FXML
    private void openMap() {
        Drone selected = droneTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Atenție", "Selectează o dronă pentru a planifica zborul!");
            return;
        }

        if (selected.getStatus().equals("Mentenanta")) {
            showAlert("Eroare", "Drona este în mentenanță și nu poate zbura!");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/map.fxml"));
            Scene scene = new Scene(loader.load());
            
            // Trimitem drona selectată către controller-ul hărții
            MapController mapController = loader.getController();
            mapController.initData(selected);

            Stage mapStage = new Stage();
            mapStage.setTitle("Planificare Zbor - " + selected.getId());
            mapStage.setScene(scene);
            mapStage.initModality(Modality.APPLICATION_MODAL); // Blochează fereastra din spate
            mapStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Eroare", "Nu s-a putut deschide harta: " + e.getMessage());
        }
    }

    @FXML
    private void editDrone() {
        Drone selected = droneTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Atenție", "Selectează o dronă pentru editare.");
            return;
        }

        // Dialog custom pentru editare
        Dialog<Drone> dialog = new Dialog<>();
        dialog.setTitle("Editare Dronă");
        dialog.setHeaderText("Modifică detaliile pentru " + selected.getId());

        // Setam butoanele (Save / Cancel)
        ButtonType saveButtonType = new ButtonType("Salvează", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Grid pentru input-uri
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField modelField = new TextField(selected.getModel());
        TextField statusField = new TextField(selected.getStatus());
        TextField payloadField = new TextField(String.valueOf(selected.getMaxPayload()));

        grid.add(new Label("Model:"), 0, 0);
        grid.add(modelField, 1, 0);
        grid.add(new Label("Status:"), 0, 1);
        grid.add(statusField, 1, 1);
        grid.add(new Label("Capacitate Max (kg):"), 0, 2);
        grid.add(payloadField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Convertim rezultatul când se apasă Save
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                selected.setModel(modelField.getText());
                selected.setStatus(statusField.getText());
                try {
                    selected.setMaxPayload(Double.parseDouble(payloadField.getText()));
                } catch (NumberFormatException e) { /* Ignorăm eroarea momentan */ }
                return selected;
            }
            return null;
        });

        Optional<Drone> result = dialog.showAndWait();
        result.ifPresent(drone -> {
            droneTable.refresh(); // Refresh la tabel pentru a vedea modificările
            showAlert("Succes", "Drona a fost actualizată!");
        });
    }

    // Metodele vechi (add, remove, logout) raman la fel...
    @FXML private void addDrone() { droneList.add(new Drone("NEW", "Model Nou", "Disponibil", 100, 5.0)); }
    
    @FXML private void removeDrone() {
        Drone selected = droneTable.getSelectionModel().getSelectedItem();
        if(selected != null) droneList.remove(selected);
    }
    
    @FXML private void onLogout() {
        // ... (codul tau de logout existent)
        ((Stage) droneTable.getScene().getWindow()).close();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}