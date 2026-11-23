package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * One change to a group's head count: who, when, how many, before and after, and why. Append-only. Every way the count can change
 * writes one (manual increase or decrease, sale, transfer out, mortality), so the log adds up to the current count, which is
 * what a herd register needs.
 */
@Entity
@Table(name = "ag_group_count_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgGroupCountLog {

    public static final String INCREASE = "INCREASE";
    public static final String DECREASE = "DECREASE";
    public static final String SALE = "SALE";
    public static final String TRANSFER_OUT = "TRANSFER_OUT";
    public static final String MORTALITY = "MORTALITY";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    /** INCREASE | DECREASE | SALE | TRANSFER_OUT | MORTALITY */
    @Column(name = "change_type", nullable = false)
    private String changeType;

    /** Signed: negative when head leave. */
    @Column(nullable = false)
    private int delta;

    @Column(name = "count_before", nullable = false)
    private int countBefore;

    @Column(name = "count_after", nullable = false)
    private int countAfter;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    /** The record that caused it (a movement or mortality record), if any. */
    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AgGroupCountLog create(TenantId tenantId, UUID groupId, String changeType, int countBefore, int countAfter,
                                         String reason, String sourceType, UUID sourceId, UUID createdBy, String createdByName) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        if (groupId == null) throw new IllegalArgumentException("groupId is required");
        if (countAfter < 0 || countBefore < 0) throw new IllegalArgumentException("a head count cannot be negative");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("a reason is required");
        AgGroupCountLog l = new AgGroupCountLog();
        l.tenantId = tenantId;
        l.groupId = groupId;
        l.changeType = changeType;
        l.delta = countAfter - countBefore;
        l.countBefore = countBefore;
        l.countAfter = countAfter;
        l.reason = reason.trim();
        l.sourceType = sourceType;
        l.sourceId = sourceId;
        l.createdBy = createdBy;
        l.createdByName = createdByName;
        l.createdAt = Instant.now();
        return l;
    }
}
