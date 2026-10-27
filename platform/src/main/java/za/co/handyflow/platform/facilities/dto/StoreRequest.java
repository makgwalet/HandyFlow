package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** {@code code} is fixed once the store is created; {@code siteId} (optional) says where it physically is. */
public record StoreRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 100) String name, UUID siteId) {}
