package com.cricketscore.controller;

import com.cricketscore.model.AuthRequest;
import com.cricketscore.model.LoginRequest;
import com.cricketscore.model.ProfileRequest;
import com.cricketscore.model.UserProfile;
import com.cricketscore.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String USER_EMAIL_ATTRIBUTE = AuthController.class.getName() + ".userEmail";
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public UserProfile register(@Valid @RequestBody AuthRequest request, HttpServletRequest servletRequest) {
        return startSession(servletRequest, authService.register(request));
    }

    @PostMapping("/login")
    public UserProfile login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return startSession(servletRequest, authService.authenticate(request.email(), request.password()));
    }

    @GetMapping("/session")
    public UserProfile currentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        Object email = session.getAttribute(USER_EMAIL_ATTRIBUTE);
        if (!(email instanceof String userEmail)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        return authService.findByEmail(userEmail).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue"));
    }

    @PatchMapping("/profile")
    public UserProfile updateProfile(@Valid @RequestBody ProfileRequest profile, HttpServletRequest request) {
        return authService.updateName(currentUserEmail(request), profile.name());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("message", "Signed out");
    }

    private UserProfile startSession(HttpServletRequest request, UserProfile user) {
        HttpSession previousSession = request.getSession(false);
        if (previousSession != null) {
            previousSession.invalidate();
        }
        request.getSession(true).setAttribute(USER_EMAIL_ATTRIBUTE, user.email());
        return user;
    }

    private String currentUserEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        Object email = session.getAttribute(USER_EMAIL_ATTRIBUTE);
        if (!(email instanceof String userEmail)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        }
        return userEmail;
    }
}
