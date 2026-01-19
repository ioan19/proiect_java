package dronefleet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Flight implements Serializable {
    private static final long serialVersionUID = 1L;

    private Drone drone;
    private String origin;
    private String destination;
    private String time;
    private ArrayList<String> waypoints = new ArrayList<>();

    public Flight(Drone drone, String origin, String destination, String time) {
        this.drone = drone;
        this.origin = origin;
        this.destination = destination;
        this.time = time;
    }

    public Drone getDrone() { return drone; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public String getTime() { return time; }

    public void setOrigin(String origin) { this.origin = origin; }
    public void setDestination(String destination) { this.destination = destination; }
    public void setTime(String time) { this.time = time; }

    public void addWaypoint(String w) { if (w != null && !w.isEmpty()) waypoints.add(w); }
    public List<String> getWaypoints() { return Collections.unmodifiableList(waypoints); }
    public void clearWaypoints() { waypoints.clear(); }
}
