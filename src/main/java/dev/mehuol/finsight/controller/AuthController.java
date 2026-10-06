package dev.mehuol.finsight.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.mehuol.finsight.dto.RegisterRequest;
import dev.mehuol.finsight.security.AppUserDetails;
import dev.mehuol.finsight.service.UserService;

/** Sign-up and "who am I". Login and logout are endpoints of Spring Security (see SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        String email = userService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("email", email));
    }

    /** 200 with the email when signed in, otherwise 401. The page calls this on load. */
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> me(@AuthenticationPrincipal AppUserDetails user) {
        return user == null
                ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not signed in"))
                : ResponseEntity.ok(Map.of("email", user.email()));
    }
}
