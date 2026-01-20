package dronefleet;

public class Drone {
    private String id;
    private String model;
    private String type; // transport/survey
    private String status; // activa, in_livrare, mentenanta, inactiva
    private double maxPayload;
    private double autonomy;
    
    // Timpi limita (milisecunde)
    private long missionEndTime = 0;
    private long maintenanceEndTime = 0;

    public Drone(String id, String model, String type, String status, double maxPayload, double autonomy) {
        this.id = id;
        this.model = model;
        this.type = type;
        this.status = status;
        this.maxPayload = maxPayload;
        this.autonomy = autonomy;
    }

    // --- GETTERS & SETTERS ---
    public String getId() { return id; }
    public String getModel() { return model; }
    public String getType() { return type; }
    public String getStatus() { 
        // Verificam automat daca a expirat timpul de cand o cerem
        checkAutoStatus();
        return status; 
    }
    public double getMaxPayload() { return maxPayload; }
    public double getAutonomy() { return autonomy; }

    public void setStatus(String status) { this.status = status; }
    public void setMissionEndTime(long time) { this.missionEndTime = time; }
    public void setMaintenanceEndTime(long time) { this.maintenanceEndTime = time; }

    // --- LOGICA AUTOMATA DE STATUS ---
    public void checkAutoStatus() {
        long now = System.currentTimeMillis();

        // 1. Verificare Misiune
        if ("in_livrare".equals(status) && missionEndTime > 0) {
            if (now >= missionEndTime) {
                status = "activa";
                missionEndTime = 0;
            }
        }
        
        // 2. Verificare Mentenanta
        if ("mentenanta".equals(status) && maintenanceEndTime > 0) {
            if (now >= maintenanceEndTime) {
                status = "activa";
                maintenanceEndTime = 0;
            }
        }
    }

    // Text pentru coloana "Timp Rămas"
    public String getTimeRemainingDisplay() {
        checkAutoStatus(); // Update inainte de afisare
        long now = System.currentTimeMillis();
        long end = 0;

        if ("in_livrare".equals(status)) end = missionEndTime;
        else if ("mentenanta".equals(status)) end = maintenanceEndTime;
        else return "-";

        long diff = end - now;
        if (diff <= 0) return "Finalizare...";

        long min = diff / 60000;
        long sec = (diff % 60000) / 1000;
        
        String prefix = "mentenanta".equals(status) ? "Ment: " : "Zbor: ";
        return prefix + String.format("%02d:%02d", min, sec);
    }
    
    @Override
    public String toString() { return model + " (" + type + ")"; }
}