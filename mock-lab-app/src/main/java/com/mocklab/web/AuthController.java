package com.mocklab.web;

import com.mocklab.service.AuthService;
import com.mocklab.web.form.LoginForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Hand-rolled, session-based authentication (no Spring Security), per the
 * tech spec's explicit "no production-grade auth" trade-off. On successful
 * login the authenticated username is stored under the
 * {@link #AUTHENTICATED_USER_SESSION_ATTRIBUTE} session attribute, which a
 * later task's SessionAuthInterceptor reads to guard other screens.
 */
@Controller
public class AuthController {

    /** Session attribute name a later SessionAuthInterceptor will check. */
    public static final String AUTHENTICATED_USER_SESSION_ATTRIBUTE = "authenticatedUser";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute LoginForm loginForm, Model model, HttpSession session) {
        if (!StringUtils.hasText(loginForm.getUsername()) || !StringUtils.hasText(loginForm.getPassword())) {
            model.addAttribute("errorMessage", "Username and password are required.");
            return "login";
        }

        return authService.authenticate(loginForm.getUsername(), loginForm.getPassword())
                .map(user -> {
                    session.setAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE, user.username());
                    return "redirect:/samples";
                })
                .orElseGet(() -> {
                    model.addAttribute("errorMessage", "Invalid username or password.");
                    return "login";
                });
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/")
    public String root(HttpSession session) {
        if (session.getAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE) != null) {
            return "redirect:/samples";
        }
        return "redirect:/login";
    }
}
