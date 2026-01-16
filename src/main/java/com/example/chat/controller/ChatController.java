package com.example.chat.controller;

import com.example.chat.model.Chat;
import com.example.chat.model.ChatMember;
import com.example.chat.model.ChatMemberId;
import com.example.chat.model.Message;
import com.example.chat.model.User;
import com.example.chat.repository.ChatMemberRepository;
import com.example.chat.repository.ChatRepository;
import com.example.chat.repository.MessageRepository;
import com.example.chat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private UserRepository userRepository;
    @Autowired
    private MessageRepository messageRepository;

    @GetMapping("/user/{userId}")
    public List<Map<String, Object>> getUserChats(@PathVariable Long userId) {
        List<ChatMember> memberships = chatMemberRepository.findByUserId(userId);
        List<Map<String, Object>> response = new ArrayList<>();

        for (ChatMember member : memberships) {
            Chat chat = chatRepository.findById(member.getChatId())
                    .orElseThrow(() -> new RuntimeException("Chat not found"));

            // Find the OTHER user's name
//            List<ChatMember> allMembers = chatMemberRepository.findByChatId(chat.getId());
            String displayName = chat.getChatName();

            // If it's a private chat (usually indicated by a generic name or null),
            // then use the "other user" logic. Otherwise, use the group name.
            if (displayName == null || displayName.equals("Private Chat") || displayName.isEmpty()) {
                List<ChatMember> allMembers = chatMemberRepository.findByChatId(chat.getId());
                for (ChatMember m : allMembers) {
                    if (!m.getUserId().equals(userId)) {
                        User otherUser = userRepository.findById(m.getUserId()).orElseThrow();
                        displayName = otherUser.getUsername();
                        break;
                    }
                }
            }

            Optional<Message> lastMessageOpt = messageRepository.findFirstByChatIdOrderBySentAtDesc(chat.getId());
            String lastMessageText = "No messages yet";

            // Use a default ISO string for chats with no messages
            String lastTimeStr = "1970-01-01T00:00:00";
            boolean hasUnread = false;

            if (lastMessageOpt.isPresent()) {
                Message msg = lastMessageOpt.get();
                lastTimeStr = msg.getSentAt().toString();

                String prefix = msg.getAuthor().getId().equals(userId) ? "You: " : msg.getAuthor().getUsername() + ": ";
                lastMessageText = prefix + msg.getContent();

                // Unread check
                if (member.getLastWatched() == null || msg.getSentAt().isAfter(member.getLastWatched())) {
                    if (!msg.getAuthor().getId().equals(userId)) {
                        hasUnread = true;
                    }
                }
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", chat.getId());
            map.put("chatName", displayName);
            map.put("lastMessage", lastMessageText);
            map.put("hasUnread", hasUnread);
            map.put("lastMessageTime", lastTimeStr);
            response.add(map);
        }

        // UPDATED SORTING LOGIC:
        response.sort((a, b) -> {
            // Tier 1: Unread status (Highest Priority)
            boolean unreadA = (boolean) a.get("hasUnread");
            boolean unreadB = (boolean) b.get("hasUnread");
            if (unreadA != unreadB) {
                return Boolean.compare(unreadB, unreadA);
            }

            // Tier 2: Time (Newest first)
            // String comparison works for ISO dates (YYYY-MM-DDTHH:mm:ss)
            return ((String) b.get("lastMessageTime")).compareTo((String) a.get("lastMessageTime"));
        });

        return response;
    }

    @PostMapping("/create-private")
    public ResponseEntity<?> createPrivateChat(@RequestParam Long creatorId, @RequestParam String targetUsername) {
        Optional<User> targetUser = userRepository.findByUsername(targetUsername.trim());
        if (targetUser.isEmpty()) return ResponseEntity.badRequest().body("User not found");

        Long targetId = targetUser.get().getId();
        if (creatorId.equals(targetId)) return ResponseEntity.badRequest().body("Cannot chat with yourself");

        Optional<Long> existingChatId = chatRepository.findExistingChatBetweenUsers(creatorId, targetId);

        Chat chatToReturn;
        if (existingChatId.isPresent()) {
            chatToReturn = chatRepository.findById(existingChatId.get()).get();
        } else {
            Chat newChat = new Chat();
            newChat.setChatName("Private Chat"); // Generic name in DB
            newChat.setCreator(userRepository.findById(creatorId).get());
            chatToReturn = chatRepository.save(newChat);

            chatMemberRepository.save(new ChatMember(chatToReturn.getId(), creatorId));
            chatMemberRepository.save(new ChatMember(chatToReturn.getId(), targetId));
        }

        // FIX: Instead of returning the Chat entity, return a Map with the CORRECT name
        Map<String, Object> response = new HashMap<>();
        response.put("id", chatToReturn.getId());
        response.put("chatName", targetUsername); // Ensure it shows the person you just searched for

        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-group")
    public ResponseEntity<?> createGroupChat(
            @RequestParam Long creatorId,
            @RequestParam String groupName,
            @RequestBody List<String> usernames) {

        // 1. Create the Chat Entity
        Chat newChat = new Chat();
        newChat.setChatName(groupName.isEmpty() ? "New Group" : groupName);
        newChat.setCreator(userRepository.findById(creatorId).get());
        Chat savedChat = chatRepository.save(newChat);

        // 2. Add the Creator as a member
        chatMemberRepository.save(new ChatMember(savedChat.getId(), creatorId));

        // 3. Add all other users by their usernames
        for (String username : usernames) {
            userRepository.findByUsername(username.trim()).ifPresent(user -> {
                if (!user.getId().equals(creatorId)) { // Don't add creator twice
                    chatMemberRepository.save(new ChatMember(savedChat.getId(), user.getId()));
                }
            });
        }

        // 4. Return the new chat info
        Map<String, Object> response = new HashMap<>();
        response.put("id", savedChat.getId());
        response.put("chatName", savedChat.getChatName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{chatId}/read/{userId}")
    public ResponseEntity<?> markAsRead(@PathVariable Long chatId, @PathVariable Long userId) {
        chatMemberRepository.findById(new ChatMemberId(chatId, userId)).ifPresent(m -> {
            m.setLastWatched(LocalDateTime.now());
            chatMemberRepository.save(m);
        });
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{chatId}")
    public ResponseEntity<?> deleteChat(@PathVariable Long chatId) {
        // 1. Remove all members from the chat first
        chatMemberRepository.deleteAll(chatMemberRepository.findByChatId(chatId));

        // 2. Remove all messages (Optional, depending on your DB cascade settings)
        messageRepository.deleteAll(messageRepository.findByChatId(chatId));

        // 3. Delete the chat itself
        chatRepository.deleteById(chatId);

        return ResponseEntity.ok().build();
    }
}