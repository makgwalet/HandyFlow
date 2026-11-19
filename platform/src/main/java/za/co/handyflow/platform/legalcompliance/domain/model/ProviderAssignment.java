package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/** A consultant assigned to a client. At most one active assignment per person per client and one active LEAD (also enforced by partial unique indexes). Ending keeps the history. */
@Entity
@Table(name = "legalcompliance_provider_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProviderAssignment extends AggregateRoot<ProviderAssignment> {

    @Column(name = "provider_client_id", nullable = false) private UUID providerClientId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "user_name", nullable = false, length = 255) private String userName;
    @Enumerated(EnumType.STRING) @Column(name = "assignment_role", nullable = false, length = 10) private AssignmentRole role;
    @Column(name = "assigned_by_name", nullable = false, length = 255) private String assignedByName;
    @Column(name = "assigned_on", nullable = false) private LocalDate assignedOn;
    @Column(name = "ended_on") private LocalDate endedOn;

    public static ProviderAssignment assign(TenantId t, UUID providerClientId, UUID userId, String userName, AssignmentRole role, String assignedBy, LocalDate today) {
        if (userId == null) throw new IllegalArgumentException("Choose a consultant");
        if (userName == null || userName.isBlank()) throw new IllegalArgumentException("The consultant's name is required");
        if (role == null) throw new IllegalArgumentException("A role is required");
        ProviderAssignment a = new ProviderAssignment();
        a.initTenantId(t);
        a.providerClientId = providerClientId;
        a.userId = userId;
        a.userName = userName.trim();
        a.role = role;
        a.assignedByName = assignedBy == null || assignedBy.isBlank() ? "Unknown" : assignedBy;
        a.assignedOn = today;
        return a;
    }

    public void end(LocalDate today) {
        if (endedOn != null) throw new IllegalStateException("This assignment has already ended");
        this.endedOn = today;
    }

    public boolean active() { return endedOn == null; }
}
