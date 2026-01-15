package com.example.chat.repository;

import com.example.chat.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {
    // Find all chats created by a specific user ID
    List<Chat> findByCreatorId(Long creatorId);

    // Finds a chat ID that is shared by exactly these two users
    @Query(value = """
        SELECT cm1.chat_id 
        FROM chat_members cm1 
        JOIN chat_members cm2 ON cm1.chat_id = cm2.chat_id 
        WHERE cm1.user_id = :user1Id AND cm2.user_id = :user2Id
        LIMIT 1
    """, nativeQuery = true)
    Optional<Long> findExistingChatBetweenUsers(Long user1Id, Long user2Id);
}