package za.co.handyflow.platform.shared;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * ConflictException — thrown when a business uniqueness rule is violated.
 *
 * WHY ConflictException instead of IllegalArgumentException?
 *
 * The original code threw IllegalArgumentException for duplicate email.
 * The problem: IllegalArgumentException is a Java standard library exception.
 * Your global exception handler has to guess what HTTP status to return.
 * Most exception handlers map it to 500 (Internal Server Error) by default.
 *
 * A duplicate email is NOT a server error — it's a client error (the client
 * sent data that conflicts with existing state).  The correct HTTP status is
 * 409 Conflict, which tells the frontend exactly what went wrong.
 *
 * @ResponseStatus(HttpStatus.CONFLICT) means Spring MVC automatically returns
 * 409 when this exception is thrown, even without a global handler entry.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
