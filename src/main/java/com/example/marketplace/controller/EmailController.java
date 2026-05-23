package com.example.marketplace.controller;

import com.example.marketplace.dto.request.AdminEmailRequest;
import com.example.marketplace.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> send(@Valid @RequestBody AdminEmailRequest req) {
        emailService.sendCustomEmail(req.getTo(), req.getSubject(), req.getText());
        return ResponseEntity.ok().build();
    }
}
