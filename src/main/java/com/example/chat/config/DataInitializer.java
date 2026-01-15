/// TEMPORAL, TO BE REMOVED
package com.example.chat.config;

import com.example.chat.model.Chat;
import com.example.chat.model.User;
import com.example.chat.repository.ChatRepository;
import com.example.chat.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;

    // Spring injects the repositories automatically
    public DataInitializer(UserRepository userRepository, ChatRepository chatRepository) {
        this.userRepository = userRepository;
        this.chatRepository = chatRepository;
    }

    @Override
    public void run(String... args) {
        // 1. Create a Default Test User if it doesn't exist
        String testUsername = "Admin";
        Optional<User> existingUser = userRepository.findByUsername(testUsername);

        User admin;
        if (existingUser.isEmpty()) {
            admin = new User();
            admin.setUsername(testUsername);
            admin.setPassword("admin123"); // In a real app, you'd encode this
            admin.setEmail("admin@example.com");
            userRepository.save(admin);
            System.out.println(">>> Created Test User: Admin (ID: " + admin.getId() + ")");
        } else {
            admin = existingUser.get();
            System.out.println(">>> Test User 'Admin' already exists.");
        }

        // 2. Create a Default Chat Room if none exist
        if (chatRepository.count() == 0) {
            Chat globalChat = new Chat();
            globalChat.setChatName("Global Lounge");
            globalChat.setCreator(admin);
            chatRepository.save(globalChat);
            System.out.println(">>> Created Global Chat Room (ID: " + globalChat.getId() + ")");
        } else {
            System.out.println(">>> Chat rooms already exist in database.");
        }
    }
}
