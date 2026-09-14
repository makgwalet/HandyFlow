package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A specific test procedure performed against one SampleItem, and its
 * result. PASS/FAIL/EXCEPTION are the meaningful outcomes — a FAIL or
 * EXCEPTION result is what a real AuditException (see that entity's
 * own class comment) gets raised against.
 */
@Entity
@Table(name = "audit_tests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditTest {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "sample_item_id", nullable = false) private UUID sampleItemId;
    @Column(name = "procedure", nullable = false) private String procedure;
    @Column(name = "result", nullable = false) private String result = "PENDING"; // PENDING | PASS | FAIL | EXCEPTION
    @Column(name = "notes") private String notes;
    @Column(name = "tested_by") private UUID testedBy;
    @Column(name = "tested_at") private Instant testedAt;

    public static AuditTest create(UUID tenantId, UUID sampleItemId, String procedure) {
        AuditTest t = new AuditTest();
        t.tenantId = tenantId;
        t.sampleItemId = sampleItemId;
        t.procedure = procedure;
        return t;
    }

    public void recordResult(String result, String notes, UUID testedBy) {
        this.result = result;
        this.notes = notes;
        this.testedBy = testedBy;
        this.testedAt = Instant.now();
    }
}
