package za.co.handyflow.platform.shared;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends HandyFlowException {

    public ResourceNotFoundException(String resourceName, String identifier) {
        super(
                "%s not found with identifier: %s".formatted(resourceName, identifier),
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND"
        );
    }
}
