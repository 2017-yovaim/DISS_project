package com.example.chat.client;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URI;
import java.net.http.*;

public class AuthWindow extends Application {

    @Override
    public void start(Stage primaryStage) {
        TabPane tabPane = new TabPane();

        // Registration Tab
        Tab registerTab = new Tab("Register", createAuthForm(true));
        // Login Tab
        Tab loginTab = new Tab("Login", createAuthForm(false));

        tabPane.getTabs().addAll(registerTab, loginTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        primaryStage.setScene(new Scene(tabPane, 350, 400));
        primaryStage.setTitle("Chat Authentication");
        primaryStage.show();
    }

    private GridPane createAuthForm(boolean isRegister) {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10);
        grid.setHgap(10);

        TextField userField = new TextField();
        PasswordField passField = new PasswordField();
        TextField emailField = new TextField();
        Button actionBtn = new Button(isRegister ? "Sign Up" : "Log In");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(userField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passField, 1, 1);

        if (isRegister) {
            grid.add(new Label("Email:"), 0, 2);
            grid.add(emailField, 1, 2);
        }

        grid.add(actionBtn, 1, 3);

        actionBtn.setOnAction(e -> {
            if (isRegister) {
                performAuth("register", userField.getText(), passField.getText(), emailField.getText());
            } else {
                performAuth("login", userField.getText(), passField.getText(), null);
            }
        });

        return grid;
    }

    private void performAuth(String endpoint, String user, String pass, String email) {
        // Construct JSON manually for simplicity
        String json;
        if (email != null) {
            json = String.format("{\"username\":\"%s\", \"password\":\"%s\", \"email\":\"%s\"}", user, pass, email);
        } else {
            json = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", user, pass);
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/auth/" + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        System.out.println("Success: " + response.body());
                        // If login successful, response.body() will be the User ID
                        // You can then launch the ChatClient with this ID
                    } else {
                        System.err.println("Error: " + response.body());
                    }
                });
    }

    public static void main(String[] args) { launch(args); }
}