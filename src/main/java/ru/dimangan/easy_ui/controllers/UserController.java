package ru.dimangan.easy_ui.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.dimangan.easy_ui.db.entities.User;
import ru.dimangan.easy_ui.services.UserService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    @PostMapping("/create")
    public User create(@RequestBody User user){
        return userService.createUser(user);
    }

    @DeleteMapping("/delete/{userId}")
    public void delete(@PathVariable Long userId){
        userService.deleteUser(userId);
    }

    @GetMapping("/list")
    public List<User> list(){
        return userService.list();
    }
}
