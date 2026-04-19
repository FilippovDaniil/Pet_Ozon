package com.example.marketplace.controller;

import com.example.marketplace.dto.request.LoginRequest;
import com.example.marketplace.dto.request.RegisterRequest;
import com.example.marketplace.dto.response.AuthResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.security.JwtUtil;
import com.example.marketplace.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = (User) userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtUtil.generateToken(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name(), user.getFullName(), user.getShopName()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerClient(request.getEmail(), request.getPassword(), request.getFullName());
        String token = jwtUtil.generateToken(user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new AuthResponse(token, user.getId(), user.getEmail(), user.getRole().name(), user.getFullName(), user.getShopName()));
    }
}
