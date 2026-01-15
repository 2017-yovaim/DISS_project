package com.example.chat.repository;

import com.example.chat.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    // Loads the history for a specific chat, sorted by time
    List<Message> findByChatIdOrderBySentAtAsc(Long chatId);

    Optional<Message> findFirstByChatIdOrderBySentAtDesc(Long chatId);
}
