package com.example.marketplace.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminEmailRequest {

    @NotBlank
    @Email
    private String to;

    @NotBlank
    private String subject;

    @NotBlank
    private String text;
}
