package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A person named on a case. Only active members can see the case at all. LEAD runs it, INVESTIGATOR works on it, OBSERVER can only read. */
@Entity(name = "AuditInvestigationMember")
@Table(name = "audit_investigation_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditInvestigationMember {
    public static final Set<String> ROLES = Set.of("LEAD", "INVESTIGATOR", "OBSERVER");

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "investigation_id", nullable = false) private UUID investigationId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "user_name", nullable = false) private String userName;
    @Column(name = "role", nullable = false) private String role;
    @Column(name = "added_by", nullable = false) private UUID addedBy;
    @Column(name = "added_at", nullable = false) private Instant addedAt = Instant.now();
    @Column(name = "removed_at") private Instant removedAt;

    public static AuditInvestigationMember of(UUID tenantId, UUID investigationId, UUID userId, String name, String role, UUID by) {
        String r = role == null ? "" : role.toUpperCase();
        if (!ROLES.contains(r)) throw new IllegalArgumentException("Role must be LEAD, INVESTIGATOR or OBSERVER");
        AuditInvestigationMember m = new AuditInvestigationMember();
        m.tenantId = tenantId; m.investigationId = investigationId; m.userId = userId; m.userName = name; m.role = r; m.addedBy = by;
        return m;
    }

    public boolean isActive() { return removedAt == null; }
    public boolean isLead() { return isActive() && "LEAD".equals(role); }
    public boolean canWrite() { return isActive() && ("LEAD".equals(role) || "INVESTIGATOR".equals(role)); }

    /** Removing this person must never leave a case with nobody in charge. */
    public void remove(List<AuditInvestigationMember> allMembers) {
        if (!isActive()) throw new IllegalStateException("This person is no longer on the case");
        if (isLead() && allMembers.stream().filter(m -> m.isLead() && !m.getUserId().equals(userId)).findAny().isEmpty())
            throw new IllegalStateException("The last lead cannot be removed; make someone else a lead first");
        this.removedAt = Instant.now();
    }
}
