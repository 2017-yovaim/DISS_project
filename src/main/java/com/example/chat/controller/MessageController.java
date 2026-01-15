package com.example.chat.controller;

import com.example.chat.model.Message;
import com.example.chat.repository.MessageRepository;
import com.example.chat.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    @Autowired
    private MessageRepository messageRepository;

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping("/{chatId}")
    public List<Map<String, String>> getHistory(@PathVariable Long chatId) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        return messageRepository.findByChatIdOrderBySentAtAsc(chatId).stream()
                .map(m -> {
                    Map<String, String> map = new java.util.LinkedHashMap<>();
                    map.put("time", m.getSentAt().format(formatter));
                    map.put("author", m.getAuthor().getUsername());
                    map.put("content", m.getContent());
                    return map;
                })
                .toList();
    }

    @PostMapping
    public Message sendMessage(@RequestBody Message message) {
        return service.save(message);
    }

    @GetMapping
    public List<Message> getMessages() {
        return service.findAll();
    }
}
