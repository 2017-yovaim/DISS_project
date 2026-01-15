package com.example.chat.config;

import com.example.chat.model.*;
import com.example.chat.repository.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository; // New injection
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Key: WebSocketSession, Value: User ID (to know who is who)
    private final Map<WebSocketSession, Long> sessionUserMap = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(MessageRepository messageRepository,
                                UserRepository userRepository,
                                ChatRepository chatRepository,
                                ChatMemberRepository chatMemberRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 1. Parse incoming JSON from client
        Map<String, Object> data = objectMapper.readValue(message.getPayload(), Map.class);

        // Track/Update the user ID for this session
        Long authorId = Long.valueOf(data.get("authorId").toString());
        sessionUserMap.put(session, authorId); // Use put to update if user relogs

        Long chatId = Long.valueOf(data.get("chatId").toString());
        String content = (String) data.get("content");

        // 2. Fetch Entities from Database
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found"));

        // 3. Save Message to H2
        Message newMessage = new Message(content, author, chat);
        messageRepository.save(newMessage);

        // 4. Create the JSON payload for the broadcast
        // We send this so the client's 'onText' listener can parse chatId and author
        String currentTime = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        Map<String, Object> responseData = Map.of(
                "chatId", chatId,
                "author", author.getUsername(),
                "content", content,
                "time", currentTime // Add this
        );
        String jsonResponse = objectMapper.writeValueAsString(responseData);

        // 5. Targeted Broadcast: Only send to members of THIS chat
        List<Long> allowedUserIds = chatMemberRepository.findByChatId(chatId)
                .stream()
                .map(ChatMember::getUserId)
                .toList();

        for (Map.Entry<WebSocketSession, Long> entry : sessionUserMap.entrySet()) {
            WebSocketSession s = entry.getKey();
            Long userIdInSession = entry.getValue();

            // Check if session is open AND if that specific user is a member of the chat
            if (s.isOpen() && allowedUserIds.contains(userIdInSession)) {
                s.sendMessage(new TextMessage(jsonResponse));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionUserMap.remove(session);
    }
}