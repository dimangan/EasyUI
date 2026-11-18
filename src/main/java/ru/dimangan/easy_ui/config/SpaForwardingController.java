package ru.dimangan.easy_ui.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards known browser routes to the Angular entry point without overlapping
 * REST, subscription, health, or static-resource paths.
 */
@Controller
public class SpaForwardingController {

    @GetMapping({"/", "/login", "/servers", "/servers/**", "/users", "/users/**"})
    public String forwardToAngularApplication() {
        return "forward:/index.html";
    }
}
