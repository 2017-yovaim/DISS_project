package com.example.chat.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_members")
@IdClass(ChatMemberId.class) // Links to the ID helper class below
public class ChatMember {
    @Id
    private Long chatId;
    @Id
    private Long userId;

    private boolean isModerator = false;
    private LocalDateTime joinedAt = LocalDateTime.now();

    // 1. Mandatory No-Args Constructor for JPA
    public ChatMember() {}

    // 2. The missing constructor causing your error
    public ChatMember(Long chatId, Long userId) {
        this.chatId = chatId;
        this.userId = userId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isModerator() {
        return isModerator;
    }

    public void setModerator(boolean moderator) {
        isModerator = moderator;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }
}

