package dronefleet;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void onLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();

        if (validateLogin(user, pass)) {
            openDashboard();
        } else {
            statusLabel.setText("Utilizator sau parolă incorectă!");
        }
    }

    private boolean validateLogin(String username, String password) {
        // Interogare pe tabelul Users din schema ta
        String sql = "SELECT UserID FROM Users WHERE Username = ? AND PasswordHash = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, password); // Compară cu coloana PasswordHash
            
            try (ResultSet rs = pstmt.executeQuery()) {
                // Dacă returnează un rând, login-ul e valid
                return rs.next(); 
            }
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Eroare conexiune DB!");
            return false;
        }
    }

    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dashboard.fxml"));
            Scene scene = new Scene(loader.load());
            // Verificăm dacă fișierul CSS există înainte de a-l adăuga, pentru a evita erori
            if (getClass().getResource("/style.css") != null) {
                scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            }

            Stage stage = new Stage();
            stage.setTitle("Drone Fleet Manager - Dashboard");
            stage.setScene(scene);
            stage.show();

            Stage current = (Stage) usernameField.getScene().getWindow();
            current.close();
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Eroare la încărcarea Dashboard-ului!");
        }
    }
}