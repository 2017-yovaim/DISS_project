package com.example.chat.client;

import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class ChatClient extends Application {

    private Stage primaryStage;
    private WebSocket ws;
    private ListView<HBox> chatListView;
    private TextField input;

    private long currentUserId;
    private String currentUsername;
    private long currentChatId = -1;
    private boolean isInChat = false;
    private Timeline dashboardFilter;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        showLoginScreen();
    }

    // --- AUTHENTICATION ---
    private void showLoginScreen() {
        TabPane tabPane = new TabPane();
        Tab loginTab = new Tab("Login", createAuthForm(false));
        Tab regTab = new Tab("Register", createAuthForm(true));
        tabPane.getTabs().addAll(loginTab, regTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        primaryStage.setScene(new Scene(tabPane, 350, 400));
        primaryStage.setTitle("Chat Login");
        primaryStage.show();
    }

    private GridPane createAuthForm(boolean isRegister) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(20));
        grid.setVgap(10); grid.setHgap(10);

        TextField userField = new TextField();
        PasswordField passField = new PasswordField();
        Button actionBtn = new Button(isRegister ? "Sign Up" : "Log In");

        grid.add(new Label("Username:"), 0, 0); grid.add(userField, 1, 0);
        grid.add(new Label("Password:"), 0, 1); grid.add(passField, 1, 1);
        grid.add(actionBtn, 1, 2);

        actionBtn.setOnAction(e -> {
            String endpoint = isRegister ? "register" : "login";
            String json = String.format("{\"username\":\"%s\", \"password\":\"%s\"}",
                    userField.getText(), passField.getText());
            sendAuthRequest(endpoint, json, userField.getText());
        });
        return grid;
    }

    private void sendAuthRequest(String endpoint, String json, String username) {
        HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/auth/" + endpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                if (endpoint.equals("login")) {
                    this.currentUserId = Long.parseLong(response.body());
                    this.currentUsername = username;
                    Platform.runLater(() -> {
                        connectWebSocket();
                        showDashboard();
                    });
                } else {
                    Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, "Registered!").show());
                }
            }
        });
    }

    // --- DASHBOARD ---
    private void showDashboard() {
        this.isInChat = false;
        this.currentChatId = -1;

        if (dashboardFilter != null) dashboardFilter.stop();

        VBox dashboard = new VBox(15);
        dashboard.setPadding(new Insets(20));
        dashboard.setAlignment(Pos.TOP_CENTER);

        // 1. Change to ListView of ChatEntry objects
        ListView<ChatEntry> chatListViewObj = new ListView<>();

        // 2. Define the CellFactory for BOLD and "POPPING" style
        chatListViewObj.setCellFactory(lv -> new ListCell<ChatEntry>() {
            @Override
            protected void updateItem(ChatEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    VBox container = new VBox(2);
                    Label nameLabel = new Label(item.name);
                    Label msgLabel = new Label(item.lastMsg);

                    // Style the Name
                    nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

                    // Style the Message snippet
                    msgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
                    msgLabel.setEllipsisString("..."); // Truncate long messages

                    if (item.hasUnread) {
                        // Messenger style: Unread messages have a blue name or a blue dot
                        nameLabel.setStyle(nameLabel.getStyle() + "-fx-text-fill: #0078FF;");
                        msgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #000000; -fx-font-weight: bold;");
                    }

                    container.getChildren().addAll(nameLabel, msgLabel);
                    setGraphic(container);
                }
            }
        });

        fetchUserChats(chatListViewObj);

        // AUTO-REFRESH LOGIC
        dashboardFilter = new Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(5), e -> {
                    if (!isInChat) fetchUserChats(chatListViewObj);
                })
        );
        dashboardFilter.setCycleCount(Timeline.INDEFINITE);
        dashboardFilter.play();

        chatListViewObj.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ChatEntry selected = chatListViewObj.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dashboardFilter.stop();
                    showChatScreen(selected.id, selected.name);
                }
            }
        });

        Button startNewChatBtn = new Button("+ Start New Conversation");
        startNewChatBtn.setMaxWidth(Double.MAX_VALUE);
        startNewChatBtn.setOnAction(e -> showSearchUserWindow());

        dashboard.getChildren().addAll(new Label("Welcome, " + currentUsername), chatListViewObj, startNewChatBtn);
        primaryStage.setScene(new Scene(dashboard, 350, 500));
    }

    // --- CHAT SCREEN ---
    private void showChatScreen(long chatId, String chatName) {
        this.currentChatId = chatId;
        this.isInChat = true;

        // NOTIFY SERVER CHAT IS READ
        HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/chats/" + chatId + "/read/" + currentUserId))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()
        );

        if (dashboardFilter != null) dashboardFilter.stop();

        chatListView = new ListView<>();
        VBox.setVgrow(chatListView, Priority.ALWAYS);
        chatListView.setStyle("-fx-background-color: #F4F4F4; -fx-control-inner-background: #F4F4F4;");

        Label headerLabel = new Label(chatName);
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        input = new TextField();
        input.setPromptText("Message " + chatName + "...");
        input.setOnAction(e -> sendMessage());

        Button sendBtn = new Button("Send");
        sendBtn.setOnAction(e -> sendMessage());

        Button backBtn = new Button("← Back");
        backBtn.setOnAction(e -> showDashboard());

        HBox inputRow = new HBox(10, input, sendBtn);
        HBox.setHgrow(input, Priority.ALWAYS);

        VBox root = new VBox(10, backBtn, headerLabel, chatListView, inputRow);
        root.setPadding(new Insets(10));

        primaryStage.setScene(new Scene(root, 400, 500));
        loadChatHistory();
    }

    private void addMessageToUI(String time, String author, String content) {
        boolean isMe = author.equals(currentUsername);
        Label nameLabel = new Label(author + " • " + time);
        nameLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");

        Label msgLabel = new Label(content);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(250);
        msgLabel.setPadding(new Insets(8, 12, 8, 12));

        VBox bubbleContainer = new VBox(2, nameLabel, msgLabel);
        HBox row = new HBox(bubbleContainer);
        row.setPadding(new Insets(5));

        if (isMe) {
            row.setAlignment(Pos.CENTER_RIGHT);
            bubbleContainer.setAlignment(Pos.TOP_RIGHT);
            msgLabel.setStyle("-fx-background-color: #0078FF; -fx-text-fill: white; -fx-background-radius: 15 15 2 15;");
        } else {
            row.setAlignment(Pos.CENTER_LEFT);
            bubbleContainer.setAlignment(Pos.TOP_LEFT);
            msgLabel.setStyle("-fx-background-color: #E9E9EB; -fx-text-fill: black; -fx-background-radius: 15 15 15 2;");
        }

        Platform.runLater(() -> {
            chatListView.getItems().add(row);
            chatListView.scrollTo(row);
        });
    }

    // --- DATA FETCHING ---
    private void loadChatHistory() {
        final long loadingChatId = this.currentChatId;
        HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/api/messages/" + loadingChatId)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                String body = response.body();
                Platform.runLater(() -> {
                    if (chatListView == null || currentChatId != loadingChatId) return;
                    chatListView.getItems().clear();
                    if (body.equals("[]") || body.isEmpty()) return;

                    String content = body.trim();
                    content = content.substring(1, content.length() - 1);
                    String[] messages = content.split("(?<=\\}),(?=\\{)");
                    for (String msg : messages) {
                        addMessageToUI(extractValueFromJSON(msg, "time"), extractValueFromJSON(msg, "author"), extractValueFromJSON(msg, "content"));
                    }
                });
            }
        });
    }

    private void fetchUserChats(ListView<ChatEntry> listView) {
        HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8080/api/chats/user/" + currentUserId))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                String body = response.body();
                Platform.runLater(() -> {
                    List<ChatEntry> chatEntries = new ArrayList<>();

                    // Check if body is a valid JSON array and not empty
                    if (body != null && body.length() > 2 && !body.equals("[]")) {
                        try {
                            // Remove outer brackets [ ]
                            String content = body.substring(1, body.length() - 1);

                            // Split exactly between objects: } , {
                            String[] chats = content.split("(?<=\\}),(?=\\{)");

                            for (String chatJson : chats) {
                                String json = chatJson.trim();
                                // Ensure the string is treated as a full JSON object for extractors
                                if (!json.startsWith("{")) json = "{" + json;
                                if (!json.endsWith("}")) json = json + "}";

                                long id = extractIdFromJSON(json, "id");
                                String name = extractValueFromJSON(json, "chatName");
                                String lastMsg = extractValueFromJSON(json, "lastMessage");
                                boolean unread = json.contains("\"hasUnread\":true");
                                String lastTime = json.contains("\"lastMessageTime\":\"")
                                        ? json.replaceAll(".*\"lastMessageTime\":\"([^\"]+)\".*", "$1")
                                        : "";

                                if (id != -1) {
                                    chatEntries.add(new ChatEntry(id, name, lastMsg, unread, lastTime));
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Parsing error: " + e.getMessage());
                        }
                    }

                    // SORTING: Unread messages first
                    chatEntries.sort((a, b) -> b.lastTime.compareTo(a.lastTime));
                    listView.getItems().setAll(chatEntries);
                });
            }
        });
    }

    private void createNewConversation(String targetUsername, Stage windowToClose) {
        String url = String.format("http://localhost:8080/api/chats/create-private?creatorId=%d&targetUsername=%s",
                currentUserId, targetUsername);
        HttpClient.newHttpClient().sendAsync(
                HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() == 200) {
                long chatId = extractIdFromJSON(response.body(), "id");
                String chatName = extractValueFromJSON(response.body(), "chatName");
                Platform.runLater(() -> {
                    windowToClose.close();
                    showChatScreen(chatId, chatName);
                });
            } else {
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "User not found!").show());
            }
        });
    }

    // --- WEBSOCKET & UTILS ---
    private void connectWebSocket() {
        if (ws != null) return;
        HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:8080/chat"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        Platform.runLater(() -> {
                            String msg = data.toString();
                            long msgChatId = extractIdFromJSON(msg, "chatId");
                            if (isInChat && currentChatId == msgChatId) {
                                addMessageToUI(extractValueFromJSON(msg, "time"), extractValueFromJSON(msg, "author"), extractValueFromJSON(msg, "content"));
                            }
//                            else {
//                                showToastNotification("New message from " + extractValueFromJSON(msg, "author"));
//                            }
                        });
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                }).thenAccept(webSocket -> this.ws = webSocket);
    }

    private void sendMessage() {
        String text = input.getText().trim();
        if (ws != null && !text.isEmpty()) {
            String jsonMsg = String.format("{\"authorId\": %d, \"chatId\": %d, \"content\": \"%s\"}",
                    currentUserId, currentChatId, text.replace("\"", "\\\""));
            ws.sendText(jsonMsg, true);
            input.clear();
        }
    }

    private long extractIdFromJSON(String json, String key) {
        try {
            String pattern = "\"" + key + "\":";
            int start = json.indexOf(pattern) + pattern.length();
            int endComma = json.indexOf(",", start);
            int endBrace = json.indexOf("}", start);
            int end = (endComma != -1 && (endBrace == -1 || endComma < endBrace)) ? endComma : endBrace;
            return Long.parseLong(json.substring(start, end).trim());
        } catch (Exception e) { return -1; }
    }

    private String extractValueFromJSON(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int start = json.indexOf(pattern) + pattern.length();
            return json.substring(start, json.indexOf("\"", start));
        } catch (Exception e) { return "Unknown"; }
    }

    private long extractIdFromText(String text) {
        return Long.parseLong(text.substring(text.lastIndexOf(":") + 1).replace(")", "").trim());
    }

    private void showToastNotification(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
            alert.show();
        });
    }

    private void showSearchUserWindow() {
        Stage searchStage = new Stage();
        searchStage.setTitle("New Conversation");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #ffffff;");

        // 1. Group/Chat Name Field
        Label nameLabel = new Label("Chat or Group Name (Optional):");
        nameLabel.setStyle("-fx-font-weight: bold;");
        TextField chatNameField = new TextField();
        chatNameField.setPromptText("e.g. Project Team or Alice");

        // 2. User Search/Selection Area
        Label userLabel = new Label("Add Participants:");
        userLabel.setStyle("-fx-font-weight: bold;");

        TextField searchField = new TextField();
        searchField.setPromptText("Enter username...");

        // Future-proofing: A list to show added members
        ListView<String> selectedUsersList = new ListView<>();
        selectedUsersList.setPrefHeight(100);

        Button addMemberBtn = new Button("Add User");
        addMemberBtn.setMaxWidth(Double.MAX_VALUE);
        addMemberBtn.setOnAction(e -> {
            String user = searchField.getText().trim();
            if (!user.isEmpty() && !selectedUsersList.getItems().contains(user)) {
                selectedUsersList.getItems().add(user);
                searchField.clear();
            }
        });

        // 3. Action Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> searchStage.close());

        Button createBtn = new Button("Create Chat");
        createBtn.setStyle("-fx-background-color: #0078FF; -fx-text-fill: white; -fx-font-weight: bold;");

        // Current Logic Hook: Still uses the first user in the list to maintain 1-on-1 compatibility
        createBtn.setOnAction(e -> {
            if (!selectedUsersList.getItems().isEmpty()) {
                createNewConversation(selectedUsersList.getItems().get(0), searchStage);
            } else if (!searchField.getText().trim().isEmpty()) {
                createNewConversation(searchField.getText().trim(), searchStage);
            }
        });

        buttonBox.getChildren().addAll(cancelBtn, createBtn);

        // Assembly
        layout.getChildren().addAll(
                nameLabel, chatNameField,
                new Separator(),
                userLabel, searchField, addMemberBtn, selectedUsersList,
                buttonBox
        );

        searchStage.setScene(new Scene(layout, 300, 450));
        searchStage.show();
    }

    public static void main(String[] args) { launch(args); }

    private static class ChatEntry {
        long id;
        String name;
        String lastMsg;
        boolean hasUnread;
        String lastTime; // Add this

        ChatEntry(long id, String name, String lastMsg, boolean hasUnread, String lastTime) {
            this.id = id;
            this.name = name;
            this.lastMsg = lastMsg;
            this.hasUnread = hasUnread;
            this.lastTime = lastTime;
        }
    }
}