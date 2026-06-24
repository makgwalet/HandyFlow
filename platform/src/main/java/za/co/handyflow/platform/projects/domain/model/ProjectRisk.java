package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_risks")
@Getter
@NoArgsConstructor
public class ProjectRisk {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID   tenantId;
    @Column(name = "project_id", nullable = false) UUID   projectId;
    @Column(name = "risk_number")                  String riskNumber;
    @Column(nullable = false)                      String title;
    String description;
    String category;
    @Column(nullable = false) int probability = 3;  // 1–5
    @Column(nullable = false) int impact      = 3;  // 1–5
    // risk_score GENERATED in DB — read-only here
    @Column(name = "rating",     nullable = false) String rating = "AMBER";
    @Column(nullable = false)                      String status = "OPEN";
    String mitigation;
    @Column(name = "owner_id")   UUID   ownerId;
    @Column(name = "owner_name") String ownerName;
    @Column(name = "review_date") LocalDate reviewDate;
    @Column(name = "is_ohsa",    nullable = false) boolean isOhsa = false;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ProjectRisk create(UUID tenantId, UUID projectId, String title,
                                     int probability, int impact, String category,
                                     String mitigation, UUID ownerId, String ownerName) {
        ProjectRisk r = new ProjectRisk();
        r.id          = UUID.randomUUID();
        r.tenantId    = tenantId;
        r.projectId   = projectId;
        r.title       = title;
        r.probability = probability;
        r.impact      = impact;
        r.category    = category;
        r.mitigation  = mitigation;
        r.ownerId     = ownerId;
        r.ownerName   = ownerName;
        r.status      = "OPEN";
        r.updateRating();
        r.createdAt   = Instant.now();
        r.updatedAt   = Instant.now();
        return r;
    }

    public void updateRating() {
        int score = probability * impact;
        if (score >= 15)     this.rating = "RED";
        else if (score >= 9) this.rating = "AMBER";
        else                 this.rating = "GREEN";
    }

    public void mitigate(String mitigationNote) { this.mitigation = mitigationNote; this.status = "MITIGATED"; touch(); }
    public void close()  { this.status = "CLOSED"; touch(); }
    public void accept() { this.status = "ACCEPTED"; touch(); }

    public void setProbability(int v)       { this.probability = v; updateRating(); }
    public void setImpact(int v)            { this.impact      = v; updateRating(); }
    public void setOwnerId(UUID v)          { this.ownerId     = v; }
    public void setOwnerName(String v)      { this.ownerName   = v; }
    public void setReviewDate(LocalDate v)  { this.reviewDate  = v; }
    public void setMitigation(String v)     { this.mitigation  = v; }
    public void setIsOhsa(boolean v)        { this.isOhsa      = v; }

    private void touch() { this.updatedAt = Instant.now(); }
}
