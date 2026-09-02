package za.co.handyflow.platform.collectionsagency.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InvitePortalUserRequest(@NotBlank @Email String email) {}
