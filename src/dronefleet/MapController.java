package dronefleet;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapController {

    // --- Elemente UI ---
    @FXML private WebView webView;
    @FXML private TableView<Destination> destTable;
    @FXML private TableColumn<Destination, String> colName;
    @FXML private TableColumn<Destination, String> colAddress;
    
    @FXML private TextField weightField;
    @FXML private Label distanceLabel;
    @FXML private Label costLabel;
    @FXML private Label statusLabel;
    
    // Elemente noi pentru Meteo
    @FXML private Label weatherLabel;
    @FXML private Label windLabel;
    @FXML private Button confirmButton;
    @FXML private VBox weatherCard;

    // --- Logica Interna ---
    private WebEngine webEngine;
    private Drone selectedDrone;
    
    // Coordonate Baza (Ex: Piata Unirii Bucuresti)
    private final double BASE_LAT = 44.4268;
    private final double BASE_LON = 26.1025;

    // CHEIA API LOCATIONIQ (Adaugata Aici)
    private static final String LOCATION_API_KEY = "pk.158719224b8587f1e2f1cd81fed13147";

    // Primim drona selectata din Dashboard
    public void initData(Drone drone) {
        this.selectedDrone = drone;
    }

    @FXML
    public void initialize() {
        // 1. Configurare Tabel
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        loadDestinations();

        // 2. Configurare Harta (WebView)
        webEngine = webView.getEngine();
        webEngine.load(getClass().getResource("/map_view.html").toExternalForm());

        // Cand harta e gata incarcata, punem Baza
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                webEngine.executeScript("setDronePosition(" + BASE_LAT + ", " + BASE_LON + ", 'Fleet HQ')");
            }
        });

        // 3. Ascultam selectia din tabel
        destTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                processDestination(newVal);
            }
        });

        // --- FIX CRITIC PENTRU ZONELE GRI (RESIZE) ---
        webView.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                webEngine.executeScript("if(window.map) { map.invalidateSize(); }");
            }
        });

        webView.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                webEngine.executeScript("if(window.map) { map.invalidateSize(); }");
            }
        });
    }

    private void loadDestinations() {
        ObservableList<Destination> list = FXCollections.observableArrayList();
        list.add(new Destination("Compania A", "Palatul Parlamentului, Bucuresti"));
        list.add(new Destination("Client B", "Ateneul Roman, Bucuresti"));
        list.add(new Destination("Depozit Nord", "Mall Baneasa, Bucuresti"));
        list.add(new Destination("Universitate", "Piata Universitatii, Bucuresti"));
        destTable.setItems(list);
    }

    // Procesare selectie destinatie
    private void processDestination(Destination dest) {
        statusLabel.setText("Analiză rută & condiții meteo...");
        statusLabel.setTextFill(Color.GRAY);
        
        if(confirmButton != null) confirmButton.setDisable(true);
        
        // Rulam API call-ul pe un alt thread
        new Thread(() -> {
            double[] coords = getCoordinatesFromAPI(dest.getAddress());
            
            // Revenim pe thread-ul principal pentru UI
            Platform.runLater(() -> {
                if (coords != null) {
                    double destLat = coords[0];
                    double destLon = coords[1];
                    
                    // 1. Update Harta
                    webEngine.executeScript("setDestination(" + destLat + ", " + destLon + ", '" + dest.getAddress() + "')");
                    
                    // 2. Verificam Meteo si Calculam Trip-ul
                    checkWeatherAndTrip(destLat, destLon);
                } else {
                    statusLabel.setText("Eroare: Adresa nu a fost găsită!");
                    statusLabel.setTextFill(Color.RED);
                }
            });
        }).start();
    }

    // Logica Premium: Verificare Meteo + Siguranta
    private void checkWeatherAndTrip(double lat, double lon) {
        // Aflam vremea (simulata prin serviciul nostru sau API-ul real daca e configurat)
        WeatherService.WeatherData weather = WeatherService.getWeatherAt(lat, lon);
        
        // Actualizam Cardul Meteo
        if (weatherLabel != null) weatherLabel.setText(weather.temperature + "°C | " + weather.condition);
        if (windLabel != null) windLabel.setText("Vânt: " + weather.windSpeed + " km/h");
        
        // Decizie Business: Zburam sau nu?
        if (weather.isSafeToFly) {
            if (weatherCard != null) weatherCard.setStyle("-fx-background-color: #e8f6f3; -fx-border-color: #27ae60; -fx-border-width: 0 0 0 5;");
            if (confirmButton != null) confirmButton.setDisable(false);
            
            statusLabel.setText("Condiții optime. Calculare cost...");
            statusLabel.setTextFill(Color.GREEN);
            
            calculateTrip(lat, lon);
        } else {
            if (weatherCard != null) weatherCard.setStyle("-fx-background-color: #fdedec; -fx-border-color: #e74c3c; -fx-border-width: 0 0 0 5;");
            if (confirmButton != null) confirmButton.setDisable(true);
            
            statusLabel.setText("ZBOR INTERZIS: Condiții meteo periculoase!");
            statusLabel.setTextFill(Color.RED);
            
            distanceLabel.setText("---");
            costLabel.setText("ZBOR ANULAT");
            costLabel.setTextFill(Color.RED);
        }
    }

    // Calcul Matematic Distanta & Pret
    private void calculateTrip(double endLat, double endLon) {
        double distKm = haversine(BASE_LAT, BASE_LON, endLat, endLon);
        
        try {
            double weight = Double.parseDouble(weightField.getText());
            
            if(weight > selectedDrone.getMaxPayload()) {
                statusLabel.setText("EROARE: Pachet prea greu (" + weight + "kg) > Max " + selectedDrone.getMaxPayload() + "kg");
                statusLabel.setTextFill(Color.RED);
                costLabel.setText("Exces Greutate");
                if (confirmButton != null) confirmButton.setDisable(true);
                return;
            }

            double cost = (distKm * 2.5) + (weight * 5.0); 
            
            distanceLabel.setText(String.format("%.2f km", distKm));
            costLabel.setText(String.format("%.2f RON", cost));
            costLabel.setTextFill(Color.web("#f1c40f")); 
            
        } catch (NumberFormatException e) {
            statusLabel.setText("Introdu o greutate corectă!");
        }
    }

    // --- API LOCATIONIQ IMPLEMENTAT AICI ---

    private double[] getCoordinatesFromAPI(String address) {
        try {
            // Curatam adresa
            String encodedAddress = address.replace(" ", "%20").replace(",", "");
            
            // Construim URL-ul pentru LocationIQ folosind cheia ta
            String url = String.format(
                "https://us1.locationiq.com/v1/search.php?key=%s&q=%s&format=json",
                LOCATION_API_KEY, encodedAddress
            );
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            // Verificare erori API
            if (response.statusCode() != 200) {
                System.out.println("Eroare LocationIQ: " + response.statusCode());
                System.out.println("Raspuns: " + json);
                return null;
            }

            // Parsare JSON (formatul LocationIQ este compatibil cu logica existenta)
            if (json.contains("\"lat\"") && json.contains("\"lon\"")) {
                Pattern pLat = Pattern.compile("\"lat\":\"([^\"]+)\"");
                Pattern pLon = Pattern.compile("\"lon\":\"([^\"]+)\"");
                
                Matcher mLat = pLat.matcher(json);
                Matcher mLon = pLon.matcher(json);
                
                if (mLat.find() && mLon.find()) {
                    double lat = Double.parseDouble(mLat.group(1));
                    double lon = Double.parseDouble(mLon.group(1));
                    return new double[]{lat, lon};
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; 
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}