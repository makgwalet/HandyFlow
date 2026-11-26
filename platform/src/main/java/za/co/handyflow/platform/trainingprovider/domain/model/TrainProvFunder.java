package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.FundingRules;

import java.time.Instant;
import java.util.UUID;

/** Someone who pays for training: the learner, an employer, a SETA, a grant, government or another payer. Retired with a reason, never deleted. */
@Entity(name = "TrainProvFunder")
@Table(name = "trainprov_funders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvFunder {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "funder_no", nullable = false)
    private String funderNo;
    @Column(nullable = false)
    private String name;
    @Column(name = "funder_type", nullable = false)
    private String funderType;
    @Column(name = "client_id")
    private UUID clientId;
    @Column(name = "contact_name")
    private String contactName;
    @Column(name = "contact_email")
    private String contactEmail;
    @Column(name = "contact_phone")
    private String contactPhone;
    private String note;
    @Column(nullable = false)
    private String status = FundingRules.ACTIVE;
    @Column(name = "retire_reason")
    private String retireReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvFunder create(TenantId t, String no, String name, String type, UUID clientId, String contactName, String contactEmail, String contactPhone, String note, UUID by) {
        TrainProvFunder f = new TrainProvFunder();
        f.tenantId = t.getValue();
        f.funderNo = no;
        f.createdBy = by;
        f.createdAt = Instant.now();
        f.apply(name, type, clientId, contactName, contactEmail, contactPhone, note);
        return f;
    }

    public void change(String name, String type, UUID clientId, String contactName, String contactEmail, String contactPhone, String note) {
        String problem = FundingRules.editProblem(status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        apply(name, type, clientId, contactName, contactEmail, contactPhone, note);
    }

    private void apply(String name, String type, UUID clientId, String contactName, String contactEmail, String contactPhone, String note) {
        if (name == null || name.trim().length() < 2 || type == null || !FundingRules.TYPES.contains(type)) {
            throw new IllegalArgumentException("Give the funder's name and kind");
        }
        this.name = name.trim();
        this.funderType = type;
        this.clientId = clientId;
        this.contactName = blank(contactName);
        this.contactEmail = blank(contactEmail);
        this.contactPhone = blank(contactPhone);
        this.note = blank(note);
        this.updatedAt = Instant.now();
    }

    public void retire(String reason, int activeLinks, UUID by) {
        String problem = FundingRules.retireProblem(status, reason, activeLinks);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = FundingRules.RETIRED;
        this.retireReason = reason.trim();
        this.retiredBy = by;
        this.updatedAt = Instant.now();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
