package za.co.handyflow.platform.secureaccess.application;

import za.co.handyflow.platform.shared.HandyFlowException;

/**
 * Result of a piece of access work done inside a transaction: a value, or the error to throw AFTER the
 * transaction has committed. Throwing inside would roll back the access-log rows that record the refusal.
 */
public final class AccessOutcome<T> {

    public final T value;
    public final HandyFlowException error;

    private AccessOutcome(T value, HandyFlowException error) {
        this.value = value;
        this.error = error;
    }

    public static <T> AccessOutcome<T> ok(T value) { return new AccessOutcome<>(value, null); }

    public static <T> AccessOutcome<T> fail(HandyFlowException error) { return new AccessOutcome<>(null, error); }
}
