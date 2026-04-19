package com.example.marketplace.controller;

import com.example.marketplace.dto.request.UpdateProfileRequest;
import com.example.marketplace.dto.response.ProfileResponse;
import com.example.marketplace.entity.User;
import com.example.marketplace.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/me")
    public ProfileResponse getProfile(@AuthenticationPrincipal User user) {
        return toResponse(user);
    }

    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse updateProfile(@AuthenticationPrincipal User user,
                                          @RequestBody UpdateProfileRequest request) {
        return toResponse(userService.updateProfile(user.getId(), request));
    }

    private ProfileResponse toResponse(User user) {
        ProfileResponse r = new ProfileResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setFullName(user.getFullName());
        r.setAddress(user.getAddress());
        r.setRole(user.getRole().name());
        r.setShopName(user.getShopName());
        r.setBalance(user.getBalance());
        return r;
    }
}
