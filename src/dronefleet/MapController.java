package dronefleet;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MapController {

    @FXML private WebView webView;
    @FXML private TableView<Destination> destTable;
    @FXML private TableColumn<Destination, String> colName;
    @FXML private TableColumn<Destination, String> colAddress;
    
    @FXML private ComboBox<String> missionTypeCombo;
    @FXML private TextField weightField;
    @FXML private VBox weightContainer;
    
    @FXML private Label weatherLabel;
    @FXML private Label windLabel;
    
    @FXML private Label resultTitleLabel;
    @FXML private Label distanceLabel;
    @FXML private Label costLabel;
    @FXML private Label droneLabel;
    @FXML private Label statusLabel;
    @FXML private Button confirmButton;
    @FXML private VBox resultCard;

    private WebEngine webEngine;
    private static final String LOCATION_API_KEY = "pk.158719224b8587f1e2f1cd81fed13147";
    
    private Drone selectedDroneForMission; 
    private double currentMissionDurationMin;

    @FXML
    public void initialize() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        loadDestinations();

        webEngine = webView.getEngine();
        webEngine.load(getClass().getResource("/map_view.html").toExternalForm());

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                window.setMember("javaApp", this);
            }
        });

        missionTypeCombo.setItems(FXCollections.observableArrayList("Livrare (Transport)", "Survey (Inspectie)"));
        missionTypeCombo.setValue("Livrare (Transport)");
        
        missionTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isSurvey = newVal.contains("Survey");
            weightContainer.setVisible(!isSurvey);
            weightContainer.setManaged(!isSurvey);
            webEngine.executeScript("if(window.resetMap) resetMap()");
            resetUI();
        });

        destTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) panMapToLocation(newVal);
        });
        
        webView.widthProperty().addListener((o, old, n) -> webEngine.executeScript("if(window.map) map.invalidateSize();"));

        confirmButton.setOnAction(this::startMissionSimulation);
    }

    // --- LOGICA TIMP REAL ---
    private void startMissionSimulation(ActionEvent event) {
        if (selectedDroneForMission == null) return;

        // 1. Calculam durata in MILISECUNDE REALE
        // currentMissionDurationMin vine din formula (ex: 20.5 minute)
        long durationMillis = (long) (currentMissionDurationMin * 60 * 1000);
        
        // 2. Setam datele in Drona
        selectedDroneForMission.setStatus("in_livrare");
        selectedDroneForMission.setMissionEndTime(System.currentTimeMillis() + durationMillis);
        
        System.out.println("Misiune REALA pornita: " + currentMissionDurationMin + " minute (" + durationMillis + "ms)");

        // 3. Pornim Thread-ul de asteptare
        final long sleepTime = durationMillis;
        final Drone droneRef = selectedDroneForMission;

        new Thread(() -> {
            try {
                Thread.sleep(sleepTime);
                
                // Cand se trezeste, misiunea e gata
                droneRef.setStatus("activa");
                droneRef.setMissionEndTime(0);
                
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        // 4. Inchidem fereastra
        Stage stage = (Stage) confirmButton.getScene().getWindow();
        stage.close();
    }

    public void receiveCoordinatesFromJS(double startLat, double startLon, double endLat, double endLon) {
        Platform.runLater(() -> {
            calculateMissionLogic(startLat, startLon, endLat, endLon);
        });
    }

    private void calculateMissionLogic(double startLat, double startLon, double destLat, double destLon) {
        String type = missionTypeCombo.getValue();
        boolean isDelivery = type.contains("Livrare");
        selectedDroneForMission = null;

        if (isDelivery) {
            double distKm = haversine(startLat, startLon, destLat, destLon);
            
            // Formula: (Distanta / 40km/h) * 60 + 10 minute setup
            currentMissionDurationMin = (distKm / 40.0) * 60 + 10; 

            double tempWeight = 0;
            try { tempWeight = Double.parseDouble(weightField.getText()); } catch(Exception e) {}
            final double weight = tempWeight;

         // ... in interiorul calculateMissionLogic ...

            // Filtrare Inteligenta
            // 1. Luam DOAR dronele active (ignoram mentenanta, inactiva, in_livrare)
            // 2. Luam DOAR dronele care pot duce greutatea
            // 3. Le sortam crescator dupa capacitate (ca sa nu folosim drona de 30kg pt colet de 1kg)
            List<Drone> candidates = DataManager.getInstance().getDrones().stream()
                .filter(d -> "transport".equals(d.getType()))
                .filter(d -> "activa".equals(d.getStatus())) // AICI E CHEIA: Ignoram mentenanta/ocupate
                .filter(d -> d.getMaxPayload() >= weight)
                .sorted(Comparator.comparingDouble(Drone::getMaxPayload))
                .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                // Verificam daca motivul e greutatea sau lipsa dronelor active
                boolean existsHeavyDrone = DataManager.getInstance().getDrones().stream()
                        .anyMatch(d -> d.getMaxPayload() >= weight && "transport".equals(d.getType()));
                
                if (existsHeavyDrone) {
                    showError("Drona potrivită există, dar este în service sau ocupată!");
                } else {
                    showError("Greutate prea mare (" + weight + "kg)! Nicio dronă din flotă nu o suportă.");
                }
                return;
            }

            // Daca ajungem aici, sistemul a "recomandat" automat cea mai buna drona
            // care nu e in service si duce greutatea
            selectedDroneForMission = candidates.get(0);

            selectedDroneForMission = candidates.get(0);
            double cost = 20 + (distKm * 2) + (weight * 5);
            
            showResult(selectedDroneForMission, cost, "Distanță / Timp Est.", 
                       String.format("%.2f km / %.0f min", distKm, currentMissionDurationMin));
            
            checkWeather(startLat, startLon);

        } else {
            double latDist = Math.abs(startLat - destLat) * 111;
            double lngDist = Math.abs(startLon - destLon) * 111 * Math.cos(Math.toRadians(startLat));
            double areaSqKm = latDist * lngDist;
            
            currentMissionDurationMin = (areaSqKm / 0.1) + 15;

            List<Drone> candidates = DataManager.getInstance().getDrones().stream()
                .filter(d -> "survey".equals(d.getType()) && "activa".equals(d.getStatus()) && d.getAutonomy() >= currentMissionDurationMin)
                .sorted((d1, d2) -> Double.compare(d2.getAutonomy(), d1.getAutonomy()))
                .collect(Collectors.toList());

            if (candidates.isEmpty()) {
                showError("Nicio dronă survey cu autonomie " + (int)currentMissionDurationMin + " min!");
                return;
            }

            selectedDroneForMission = candidates.get(0);
            double cost = 100 + (areaSqKm * 500);
            
            showResult(selectedDroneForMission, cost, "Arie / Timp Est.", 
                       String.format("%.3f km² / %.0f min", areaSqKm, currentMissionDurationMin));
            
            checkWeather(startLat, startLon);
        }
    }
    
    private void panMapToLocation(Destination dest) {
        statusLabel.setText("Căutare: " + dest.getAddress() + "...");
        new Thread(() -> {
            double[] coords = getCoordinatesFromAPI(dest.getAddress());
            Platform.runLater(() -> {
                if (coords != null) {
                    webEngine.executeScript("map.flyTo([" + coords[0] + ", " + coords[1] + "], 14)");
                    statusLabel.setText("Selectează START și STOP pe hartă.");
                    statusLabel.setTextFill(Color.BLUE);
                } else {
                    statusLabel.setText("Adresă negăsită!");
                }
            });
        }).start();
    }

    private void checkWeather(double lat, double lon) {
        WeatherService.WeatherData weather = WeatherService.getWeatherAt(lat, lon);
        if (weatherLabel != null) weatherLabel.setText(weather.temperature + "°C | " + weather.condition);
        if (windLabel != null) windLabel.setText("Vânt: " + weather.windSpeed + " km/h");
        
        if (!weather.isSafeToFly) {
            statusLabel.setText("ZBOR INTERZIS: Vreme rea!");
            statusLabel.setTextFill(Color.RED);
            confirmButton.setDisable(true);
            selectedDroneForMission = null;
        }
    }

    private void showResult(Drone d, double cost, String label, String value) {
        resultCard.setVisible(true);
        statusLabel.setText("Rută validată. Poți trimite drona.");
        statusLabel.setTextFill(Color.GREEN);
        
        droneLabel.setText(d.getModel());
        resultTitleLabel.setText(label);
        distanceLabel.setText(value);
        costLabel.setText(String.format("%.2f RON", cost));
        confirmButton.setDisable(false);
    }

    private void showError(String msg) {
        statusLabel.setText(msg);
        statusLabel.setTextFill(Color.RED);
        resultCard.setVisible(false);
        confirmButton.setDisable(true);
        selectedDroneForMission = null;
    }
    
    private void resetUI() {
        resultCard.setVisible(false);
        statusLabel.setText("Selectează punctele...");
        statusLabel.setTextFill(Color.GRAY);
        confirmButton.setDisable(true);
        selectedDroneForMission = null;
    }

    private void loadDestinations() {
        ObservableList<Destination> list = FXCollections.observableArrayList();
        list.add(new Destination("Bucuresti", "Bucuresti, Romania"));
        list.add(new Destination("Cluj-Napoca", "Cluj-Napoca, Romania"));
        list.add(new Destination("Timisoara", "Timisoara, Romania"));
        list.add(new Destination("Constanta", "Constanta, Romania"));
        destTable.setItems(list);
    }

    private double[] getCoordinatesFromAPI(String address) {
        try {
            String encoded = address.replace(" ", "%20").replace(",", "");
            String url = String.format("https://us1.locationiq.com/v1/search.php?key=%s&q=%s&format=json", LOCATION_API_KEY, encoded);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Pattern pLat = Pattern.compile("\"lat\":\"([^\"]+)\"");
                Pattern pLon = Pattern.compile("\"lon\":\"([^\"]+)\"");
                Matcher mLat = pLat.matcher(resp.body());
                Matcher mLon = pLon.matcher(resp.body());
                if (mLat.find() && mLon.find()) {
                    return new double[]{Double.parseDouble(mLat.group(1)), Double.parseDouble(mLon.group(1))};
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
    
}