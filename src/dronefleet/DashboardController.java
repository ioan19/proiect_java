package dronefleet;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Optional;

public class DashboardController {

    @FXML private TableView<Drone> droneTable;
    @FXML private TableColumn<Drone, String> colId;
    @FXML private TableColumn<Drone, String> colModel;
    @FXML private TableColumn<Drone, String> colStatus;
    @FXML private TableColumn<Drone, Double> colPayload;
    @FXML private TableColumn<Drone, String> colTime;

    @FXML private Label totalDronesLabel;
    @FXML private Label activeDronesLabel;
    @FXML private Label maintenanceLabel;

    private Timeline autoRefreshTimeline;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colModel.setCellValueFactory(new PropertyValueFactory<>("model"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colPayload.setCellValueFactory(new PropertyValueFactory<>("maxPayload"));
        colTime.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTimeRemainingDisplay()));

        loadData();

        // Refresh la secunda pentru cronometre
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> loadData()));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    private void loadData() {
        // Fortam verificarea timpului pentru fiecare drona
        for(Drone d : DataManager.getInstance().getDrones()) {
            d.checkAutoStatus();
        }

        if (droneTable.getItems().isEmpty()) {
            droneTable.setItems(FXCollections.observableArrayList(DataManager.getInstance().getDrones()));
        }
        
        // Update Carduri
        if(totalDronesLabel != null) {
            totalDronesLabel.setText(String.valueOf(DataManager.getInstance().countTotal()));
            activeDronesLabel.setText(String.valueOf(DataManager.getInstance().countActive()));
            
            long inMaint = DataManager.getInstance().getDrones().stream()
                    .filter(d -> "mentenanta".equals(d.getStatus())).count();
            maintenanceLabel.setText(String.valueOf(inMaint));
        }
        
        droneTable.refresh();
    }

    // --- 1. ADAUGARE DRONA (Nou) ---
    @FXML
    private void addDrone() {
        Dialog<Drone> dialog = new Dialog<>();
        dialog.setTitle("Adaugă Dronă Nouă");
        dialog.setHeaderText("Introduceți detaliile dronei");

        ButtonType loginButtonType = new ButtonType("Adaugă", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);

        TextField idField = new TextField(); idField.setPromptText("Ex: DR-99");
        TextField modelField = new TextField(); modelField.setPromptText("Ex: DJI Mavic");
        
        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("transport", "survey");
        typeCombo.setValue("transport");
        
        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("activa", "inactiva", "mentenanta");
        statusCombo.setValue("activa");

        TextField payloadField = new TextField("5.0");
        TextField autonomyField = new TextField("30");

        grid.add(new Label("ID Unic:"), 0, 0); grid.add(idField, 1, 0);
        grid.add(new Label("Model:"), 0, 1); grid.add(modelField, 1, 1);
        grid.add(new Label("Tip:"), 0, 2); grid.add(typeCombo, 1, 2);
        grid.add(new Label("Status:"), 0, 3); grid.add(statusCombo, 1, 3);
        grid.add(new Label("Payload (kg):"), 0, 4); grid.add(payloadField, 1, 4);
        grid.add(new Label("Autonomie (min):"), 0, 5); grid.add(autonomyField, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                try {
                    return new Drone(
                        idField.getText(),
                        modelField.getText(),
                        typeCombo.getValue(),
                        statusCombo.getValue(),
                        Double.parseDouble(payloadField.getText()),
                        Double.parseDouble(autonomyField.getText())
                    );
                } catch (Exception e) { return null; }
            }
            return null;
        });

        Optional<Drone> result = dialog.showAndWait();
        result.ifPresent(drone -> {
            DataManager.getInstance().getDrones().add(drone);
            // Reincarcam tabelul complet pentru a include noua drona
            droneTable.setItems(FXCollections.observableArrayList(DataManager.getInstance().getDrones()));
            loadData();
        });
    }

    // --- 2. SETARE MENTENANTA (Nou) ---
    @FXML
    private void setMaintenance() {
        Drone selected = droneTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Eroare", "Selectează o dronă din tabel!");
            return;
        }

        // Verificare logica stricta
        if ("in_livrare".equals(selected.getStatus())) {
            showAlert("Interzis", "Nu poți pune în mentenanță o dronă aflată în zbor!");
            return;
        }

        // Daca e deja in mentenanta, o scoatem (optional)
        if ("mentenanta".equals(selected.getStatus())) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Drona este deja în service. Vrei să o reactivezi acum?");
            confirm.showAndWait().ifPresent(resp -> {
                if (resp == ButtonType.OK) {
                    selected.setStatus("activa");
                    selected.setMaintenanceEndTime(0);
                    loadData();
                }
            });
            return;
        }

        // Input Dialog pentru Durata
        TextInputDialog td = new TextInputDialog("10");
        td.setTitle("Trimite în Service");
        td.setHeaderText("Cât timp va sta drona " + selected.getId() + " în mentenanță?");
        td.setContentText("Durata (minute):");

        td.showAndWait().ifPresent(val -> {
            try {
                int minutes = Integer.parseInt(val);
                
                // Setam statusul si timpul
                selected.setStatus("mentenanta");
                // Timp real: minute * 60000 ms.
                // PENTRU DEMO: 1 minut = 1 secunda (ca sa vezi efectul) -> minutes * 1000
                // Daca vrei real, schimba mai jos in: minutes * 60000
                long durationMs = minutes * 1000; 
                
                selected.setMaintenanceEndTime(System.currentTimeMillis() + durationMs);
                
                loadData();
                
            } catch (NumberFormatException e) {
                showAlert("Eroare", "Introdu un număr valid de minute!");
            }
        });
    }

    @FXML
    private void removeDrone() {
        Drone selected = droneTable.getSelectionModel().getSelectedItem();
        if(selected != null) {
            DataManager.getInstance().getDrones().remove(selected);
            // Reincarcam lista
            droneTable.setItems(FXCollections.observableArrayList(DataManager.getInstance().getDrones()));
            loadData();
        } else {
            showAlert("Atenție", "Selectează o dronă pentru ștergere.");
        }
    }

    @FXML
    private void openMap() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/map.fxml"));
            Scene scene = new Scene(loader.load());
            Stage stage = new Stage();
            stage.setTitle("Mission Planner - Enterprise");
            stage.setScene(scene);
            stage.showAndWait();
            loadData();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML private void onLogout() {
        if(autoRefreshTimeline != null) autoRefreshTimeline.stop();
        ((Stage) droneTable.getScene().getWindow()).close();
    }
    
    @FXML private void editDrone() {} // Placeholder

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}