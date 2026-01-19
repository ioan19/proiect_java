package dronefleet;

import java.io.Serializable;

public class Drone implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String model;
    private String status;
    private int batteryLevel; // Nou: % baterie
    private double maxPayload; // Nou: kg suportate

    public Drone(String id, String model, String status, int batteryLevel, double maxPayload) {
        this.id = id;
        this.model = model;
        this.status = status;
        this.batteryLevel = batteryLevel;
        this.maxPayload = maxPayload;
    }

    // Getters
    public String getId() { return id; }
    public String getModel() { return model; }
    public String getStatus() { return status; }
    public int getBatteryLevel() { return batteryLevel; }
    public double getMaxPayload() { return maxPayload; }

    // Setters pentru editare
    public void setModel(String model) { this.model = model; }
    public void setStatus(String status) { this.status = status; }
    public void setBatteryLevel(int level) { this.batteryLevel = level; }
    public void setMaxPayload(double payload) { this.maxPayload = payload; }
}