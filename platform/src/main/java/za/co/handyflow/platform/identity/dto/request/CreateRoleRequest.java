package za.co.handyflow.platform.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;

public record CreateRoleRequest(
        @NotBlank String name,
                  String description,
                  Set<UUID> permissionIds   // permissions to assign immediately
) {}
