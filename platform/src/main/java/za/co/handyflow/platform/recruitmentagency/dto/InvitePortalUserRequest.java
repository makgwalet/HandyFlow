package za.co.handyflow.platform.recruitmentagency.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InvitePortalUserRequest(@NotBlank @Email String email) {}