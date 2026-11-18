package ru.dimangan.easy_ui.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.dimangan.easy_ui.services.SubscriptionService;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    @GetMapping("/link/{userId}")
    public Map<String, String> subLink(@PathVariable Long userId, HttpServletRequest request) {
        return subscriptionService.getSubscriptionLink(userId, request);
    }

    //Рудимент из старого приложения накиданного на коленке, оставляю, чтобы не рассылать новые подписки
    @GetMapping("/api/private/subscription/get/{userId}/{subName}")
    public String oldSub(@PathVariable Long userId, @PathVariable String subName, @RequestParam(name = "servers", defaultValue = "()") String servers){
        return subscriptionService.getSubscriptionForUser(userId);
    }

    @GetMapping("/sub/{subId}")
    public String sub(@PathVariable String subId){
        return subscriptionService.getSubscriptionById(subId);
    }
}
