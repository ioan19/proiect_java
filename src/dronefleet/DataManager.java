package dronefleet;

import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static DataManager instance;
    private List<Drone> drones;

    private DataManager() {
        drones = new ArrayList<>();
        // Date similare cu cele din proiectul Web
        drones.add(new Drone("DR-01", "DJI Mavic 3", "transport", "activa", 5.0, 45));
        drones.add(new Drone("DR-02", "Matrice 300", "survey", "activa", 2.5, 55));
        drones.add(new Drone("DR-03", "Phantom 4", "transport", "mentenanta", 1.5, 30));
        drones.add(new Drone("DR-04", "Agras T30", "transport", "activa", 20.0, 25));
        drones.add(new Drone("DR-05", "Mavic Air 2", "survey", "activa", 0.5, 30));
    }

    public static DataManager getInstance() {
        if (instance == null) instance = new DataManager();
        return instance;
    }

    public List<Drone> getDrones() { return drones; }
    
    // Statistici pentru Dashboard
    public long countTotal() { return drones.size(); }
    public long countActive() { return drones.stream().filter(d -> "activa".equals(d.getStatus())).count(); }
    public long countMaintenance() { return drones.stream().filter(d -> "mentenanta".equals(d.getStatus())).count(); }
}