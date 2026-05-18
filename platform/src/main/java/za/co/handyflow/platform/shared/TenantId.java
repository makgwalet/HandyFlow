package za.co.handyflow.platform.shared;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// WHY protected no-arg constructor?
// JPA needs it to reconstruct the object from the database via reflection.
// Protected prevents accidental direct instantiation from outside —
// callers must use the static factory methods below.
public class TenantId {

    private UUID value;

    private TenantId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("TenantId cannot be null");
        }
        this.value = value;
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    public static TenantId of(UUID uuid) {
        return new TenantId(uuid);
    }

    public static TenantId of(String uuidString) {
        return new TenantId(UUID.fromString(uuidString));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
