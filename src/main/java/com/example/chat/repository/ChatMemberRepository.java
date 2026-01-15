package com.example.chat.repository;

import com.example.chat.model.ChatMember;
import com.example.chat.model.ChatMemberId; // The ID class we made
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {
    // Find all chats a specific user has joined
    List<ChatMember> findByUserId(Long userId);

    // Find all members of a specific chat
    List<ChatMember> findByChatId(Long chatId);
}