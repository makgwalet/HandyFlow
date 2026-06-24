package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_phases")
@Getter
@NoArgsConstructor
public class ProjectPhase {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID      tenantId;
    @Column(name = "project_id", nullable = false) UUID      projectId;
    @Column(nullable = false, length = 100)        String    name;
    String description;
    @Column(name = "sort_order", nullable = false) int       sortOrder = 0;
    @Column(nullable = false, length = 20)         String    status    = "NOT_STARTED";
    @Column(name = "start_date") LocalDate startDate;
    @Column(name = "end_date")   LocalDate endDate;
    @Column(name = "created_at", nullable = false) Instant   createdAt;

    // status: NOT_STARTED | IN_PROGRESS | COMPLETED | SKIPPED

    public static ProjectPhase create(UUID tenantId, UUID projectId, String name,
                                      String description, int sortOrder,
                                      LocalDate startDate, LocalDate endDate) {
        ProjectPhase p = new ProjectPhase();
        p.id          = UUID.randomUUID();
        p.tenantId    = tenantId;
        p.projectId   = projectId;
        p.name        = name;
        p.description = description;
        p.sortOrder   = sortOrder;
        p.startDate   = startDate;
        p.endDate     = endDate;
        p.status      = "NOT_STARTED";
        p.createdAt   = Instant.now();
        return p;
    }

    public void start()    { this.status = "IN_PROGRESS"; }
    public void complete() { this.status = "COMPLETED"; }
    public void skip()     { this.status = "SKIPPED"; }

    public void setName(String v)        { this.name = v; }
    public void setDescription(String v) { this.description = v; }
    public void setSortOrder(int v)      { this.sortOrder = v; }
    public void setStartDate(LocalDate v){ this.startDate = v; }
    public void setEndDate(LocalDate v)  { this.endDate = v; }
}