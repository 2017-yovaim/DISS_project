package com.example.chat.controller;

import com.example.chat.model.User;
import com.example.chat.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return service.save(user);
    }

    @GetMapping
    public List<User> getUsers() {
        return service.findAll();
    }
}
