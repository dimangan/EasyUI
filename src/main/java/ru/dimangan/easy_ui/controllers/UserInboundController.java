package ru.dimangan.easy_ui.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.dimangan.easy_ui.dto.UserInboundsRequest;
import ru.dimangan.easy_ui.services.UserInboundService;

@RestController
@RequiredArgsConstructor
@RequestMapping("user/inbound")
public class UserInboundController {
    private final UserInboundService userInboundService;

    @GetMapping("/list/{userId}")
    public UserInboundsRequest inboundsActive(@PathVariable Long userId){
        return userInboundService.getUserInbounds(userId);
    }

    @PostMapping("/change")
    public void setUserInbounds(@RequestBody UserInboundsRequest userInbounds){
        userInboundService.setUserInbounds(userInbounds);
    }
}
