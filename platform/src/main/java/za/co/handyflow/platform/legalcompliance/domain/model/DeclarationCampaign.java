package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A request that everyone (or one department) declares their interests, or says they have none. Closed with a reason; nothing is answered after that. */
@Entity
@Table(name = "legalcompliance_declaration_campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeclarationCampaign extends AggregateRoot<DeclarationCampaign> {

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String instructions;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "department_filter", length = 100) private String departmentFilter;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 6) private CampaignStatus status = CampaignStatus.OPEN;
    @Column(name = "launched_by") private UUID launchedBy;
    @Column(name = "launched_by_name", length = 255) private String launchedByName;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "closed_by_name", length = 255) private String closedByName;
    @Column(name = "close_reason", columnDefinition = "TEXT") private String closeReason;

    public static DeclarationCampaign launch(TenantId t, String reference, String title, String instructions, LocalDate due, String department, LocalDate today, UUID by, String byName) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (due == null || due.isBefore(today)) throw new IllegalArgumentException("The due date cannot be in the past");
        DeclarationCampaign c = new DeclarationCampaign();
        c.initTenantId(t);
        c.reference = reference;
        c.title = title.trim();
        c.instructions = instructions == null || instructions.isBlank() ? null : instructions.trim();
        c.dueDate = due;
        c.departmentFilter = department == null || department.isBlank() ? null : department.trim();
        c.launchedBy = by;
        c.launchedByName = byName;
        return c;
    }

    public boolean isOpen() { return status == CampaignStatus.OPEN; }

    public boolean isOverdue(LocalDate today) { return isOpen() && dueDate.isBefore(today); }

    public void close(String byName, String reason) {
        if (!isOpen()) throw new IllegalStateException("The campaign is already closed");
        if (reason == null || reason.trim().length() < 15) throw new IllegalArgumentException("A reason of at least 15 characters is required");
        this.status = CampaignStatus.CLOSED;
        this.closedAt = Instant.now();
        this.closedByName = byName;
        this.closeReason = reason.trim();
    }
}
