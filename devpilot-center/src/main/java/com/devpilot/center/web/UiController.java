package com.devpilot.center.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forward the SPA entry for any non-API path so deep links keep working.
 */
@Controller
public class UiController {

    @GetMapping({"/", "/projects", "/bugs", "/bugs/{bugId}", "/tasks", "/tasks/{taskId}", "/executor"})
    public String index() {
        return "forward:/index.html";
    }
}
