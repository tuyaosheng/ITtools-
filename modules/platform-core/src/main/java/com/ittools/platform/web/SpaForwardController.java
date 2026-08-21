package com.ittools.platform.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the built Vue SPA shell for client-side routes. Spring MVC's
 * DispatcherServlet only reaches this controller for paths that don't match
 * a static resource under classpath:/static (so requests for the actual
 * hashed JS/CSS/asset files still resolve to the real files, not this
 * forward). Deep links like /admin/users forward to index.html so vue-router
 * can take over and render the client-side route. /api/** is intentionally
 * excluded so unmatched API paths still 404/401 instead of returning HTML.
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/", "/login", "/admin/**", "/teacher/**", "/student/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
