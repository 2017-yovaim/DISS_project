package com.example.chat.controller;

import com.example.chat.model.Chat;
import com.example.chat.model.ChatMember;
import com.example.chat.model.Message;
import com.example.chat.model.User;
import com.example.chat.repository.ChatMemberRepository;
import com.example.chat.repository.ChatRepository;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.UserRepository; // Added this
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/chats")
public class ChatController {

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private UserRepository userRepository; // Now correctly injected

    @Autowired
    private MessageRepository messageRepository;

    @GetMapping("/user/{userId}")
    public List<Map<String, Object>> getUserChats(@PathVariable Long userId) {
        List<ChatMember> memberships = chatMemberRepository.findByUserId(userId);
        List<Map<String, Object>> response = new ArrayList<>();

        for (ChatMember member : memberships) {
            // Fetch chat details
            Chat chat = chatRepository.findById(member.getChatId())
                    .orElseThrow(() -> new RuntimeException("Chat not found"));

            // Find the OTHER member’s name in a private chat
            List<ChatMember> allMembers = chatMemberRepository.findByChatId(chat.getId());
            String displayName = "Unknown";

            for (ChatMember m : allMembers) {
                User otherUser = userRepository.findById(m.getUserId()).orElseThrow();
                displayName = otherUser.getUsername();
            }

            // Fetch the last message for this chat
            Optional<Message> lastMessageOpt = messageRepository.findFirstByChatIdOrderBySentAtDesc(chat.getId());
            String lastMessage = lastMessageOpt.isPresent()
                    ? lastMessageOpt.get().getContent()
                    : "No messages yet";

            // Prepare the response for the current chat
            Map<String, Object> map = new HashMap<>();
            map.put("id", chat.getId());
            map.put("chatName", displayName); // The name of the other user in the chat
            map.put("lastMessage", lastMessage); // Include the last message

            response.add(map);
        }

        return response;
    }

    @PostMapping("/create-private")
    public ResponseEntity<?> createPrivateChat(@RequestParam Long creatorId, @RequestParam String targetUsername) {
        // 1. Find the target user
        Optional<User> targetUser = userRepository.findByUsername(targetUsername);
        if (targetUser.isEmpty()) return ResponseEntity.badRequest().body("User not found");

        Long targetId = targetUser.get().getId();

        // 2. Check if a chat already exists between these two
        Optional<Long> existingChatId = chatRepository.findExistingChatBetweenUsers(creatorId, targetId);

        if (existingChatId.isPresent()) {
            // IMPORTANT: Fetch the full chat object to ensure 'id' is in the JSON
            Chat existingChat = chatRepository.findById(existingChatId.get()).orElseThrow();
            return ResponseEntity.ok(existingChat);
        }

        // 3. If no existing chat, create a new one (Existing Logic)
        Chat newChat = new Chat();
        newChat.setChatName(targetUsername);
        newChat.setCreator(userRepository.findById(creatorId).orElseThrow());
        Chat savedChat = chatRepository.save(newChat);

        chatMemberRepository.save(new ChatMember(savedChat.getId(), creatorId));
        chatMemberRepository.save(new ChatMember(savedChat.getId(), targetId));

        return ResponseEntity.ok(savedChat);
    }
}