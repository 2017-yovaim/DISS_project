package com.example.chat.model;

import java.io.Serializable;
import java.util.Objects;

public class ChatMemberId implements Serializable {

    private Long chatId;
    private Long userId;

    public ChatMemberId() {}

    public ChatMemberId(Long chatId, Long userId) {
        this.chatId = chatId;
        this.userId = userId;
    }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMemberId that = (ChatMemberId) o;
        return Objects.equals(chatId, that.chatId) &&
                Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, userId);
    }
}