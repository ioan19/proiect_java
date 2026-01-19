package dronefleet;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void onLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();

        // login simplu
        if(user.equals("admin") && pass.equals("admin")) {
            openDashboard();
        } else {
            statusLabel.setText("Invalid credentials!");
        }
    }

    private void openDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dashboard.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

            Stage stage = new Stage();
            stage.setTitle("Drone Fleet Manager - Dashboard");
            stage.setScene(scene);
            stage.show();

            // închide fereastra de login
            Stage current = (Stage) usernameField.getScene().getWindow();
            current.close();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error loading dashboard!");
        }
    }
}
