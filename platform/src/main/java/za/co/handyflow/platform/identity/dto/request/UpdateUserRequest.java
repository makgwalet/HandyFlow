package za.co.handyflow.platform.identity.dto.request;

public record UpdateUserRequest(
        String firstName,
        String lastName,
        String phone,
        String jobTitle,
        String department,
        java.util.UUID roleId   // null = don't change role
) {}
