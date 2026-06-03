package za.co.handyflow.platform.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource does not exist — returns HTTP 404.
 * Alias for ResourceNotFoundException; use whichever name is consistent
 * in the module you are working in.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
