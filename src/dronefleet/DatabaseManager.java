package dronefleet;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static DataManager instance;

    private DataManager() {}

    public static DataManager getInstance() {
        if (instance == null) instance = new DataManager();
        return instance;
    }

    // --- METODE PENTRU DRONE (Tabelul: Drones) ---

    public List<Drone> getDrones() {
        List<Drone> list = new ArrayList<>();
        // Selectăm coloanele relevante din tabelul Drones
        String sql = "SELECT DroneID, Model, Type, Status, PayloadCapacity, AutonomyMin FROM Drones";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                // Convertim datele din DB in obiecte Java
                String id = String.valueOf(rs.getInt("DroneID")); // Int -> String
                String model = rs.getString("Model");
                String type = rs.getString("Type");
                String dbStatus = rs.getString("Status"); // activa, mentenanta, inactiva
                double payload = rs.getDouble("PayloadCapacity");
                double autonomy = rs.getInt("AutonomyMin"); // DB are int, Java are double

                // Mapping Status: DB nu are "in_livrare", deci o tratam ca "activa" in cod daca e nevoie, 
                // sau o lasam asa cum vine din DB.
                Drone d = new Drone(id, model, type, dbStatus, payload, autonomy);
                list.add(d);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addDrone(Drone d) {
        // Inseram in Drones. Nu setam DroneID pentru ca e AUTO_INCREMENT
        String sql = "INSERT INTO Drones (Model, Type, Status, PayloadCapacity, AutonomyMin, LastCheckDate) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, d.getModel());
            pstmt.setString(2, d.getType());
            // Asiguram ca statusul e unul din enum-ul SQL ('activa','mentenanta','inactiva')
            String statusToSend = d.getStatus().equals("in_livrare") ? "activa" : d.getStatus();
            pstmt.setString(3, statusToSend);
            pstmt.setDouble(4, d.getMaxPayload());
            pstmt.setInt(5, (int) d.getAutonomy());
            pstmt.setDate(6, new java.sql.Date(System.currentTimeMillis())); // LastCheckDate = azi
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteDrone(Drone d) {
        String sql = "DELETE FROM Drones WHERE DroneID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Parsam ID-ul inapoi in int
            pstmt.setInt(1, Integer.parseInt(d.getId()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateDrone(Drone d) {
        // Actualizam statusul
        String sql = "UPDATE Drones SET Status = ? WHERE DroneID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            String statusToSend = d.getStatus();
            // Enum-ul din DB nu suporta 'in_livrare', deci il salvam ca 'activa' in DB, 
            // dar aplicatia va sti ca zboara daca are o misiune activa (logic complex). 
            // Simplificare: salvam 'activa' sau 'mentenanta'.
            if(statusToSend.equals("in_livrare")) statusToSend = "activa";
            
            pstmt.setString(1, statusToSend);
            pstmt.setInt(2, Integer.parseInt(d.getId()));
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- METODE PENTRU ZBORURI (Tabelul: Missions) ---

    // Salvam un zbor nou in tabelul Missions
    public void saveFlight(Flight flight) {
        String sql = "INSERT INTO Missions (DroneID, StartCoord, EndCoord, StartTime, DurationMin, Type, MissionStatus) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // 1. DroneID
            pstmt.setInt(1, Integer.parseInt(flight.getDrone().getId()));
            
            // 2. Coordonate (Origin -> StartCoord)
            pstmt.setString(2, flight.getOrigin());
            pstmt.setString(3, flight.getDestination());
            
            // 4. StartTime (String -> DateTime)
            // Presupunem ca formatul din UI e "yyyy-MM-dd HH:mm" sau similar
            // Daca e simplu text, incercam sa il parsam sau punem data curenta
            // Pentru simplitate, folosim Now() daca stringul nu e parsabil usor, 
            // dar ideal convertim flight.getTime() la Timestamp.
            try {
                // Exemplu simplificat: Daca UI trimite ora, o salvam. Aici punem data curenta pt demo.
                pstmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            } catch (Exception e) {
                pstmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            }

            // 5. Duration (Hardcodat sau calculat)
            pstmt.setInt(5, 30); // Durata default 30 min
            
            // 6. Type
            pstmt.setString(6, "livrare"); // Sau 'transport' din enum
            
            // 7. Status
            pstmt.setString(7, "planificata");
            
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Citim zborurile pentru a le afisa in tabel (Istoric)
    public List<Flight> getFlights() {
        List<Flight> flights = new ArrayList<>();
        String sql = "SELECT m.DroneID, m.StartCoord, m.EndCoord, m.StartTime, d.Model, d.Type, d.Status, d.PayloadCapacity, d.AutonomyMin " +
                     "FROM Missions m JOIN Drones d ON m.DroneID = d.DroneID " +
                     "ORDER BY m.StartTime DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while(rs.next()) {
                // Reconstruim obiectul Drone sumar
                Drone drone = new Drone(
                    String.valueOf(rs.getInt("DroneID")),
                    rs.getString("Model"),
                    rs.getString("Type"),
                    rs.getString("Status"),
                    rs.getDouble("PayloadCapacity"),
                    rs.getInt("AutonomyMin")
                );
                
                String origin = rs.getString("StartCoord");
                String dest = rs.getString("EndCoord");
                String time = rs.getString("StartTime"); // Luam data ca String pt afisare
                
                flights.add(new Flight(drone, origin, dest, time));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return flights;
    }

    // Statistici simple
    public long countTotal() {
        return getDrones().size();
    }
    public long countActive() {
        return getDrones().stream().filter(d -> "activa".equals(d.getStatus())).count();
    }
}