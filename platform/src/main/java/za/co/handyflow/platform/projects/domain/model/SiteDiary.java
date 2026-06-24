package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "site_diaries")
@Getter
@NoArgsConstructor
public class SiteDiary {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID       tenantId;
    @Column(name = "project_id", nullable = false) UUID       projectId;
    @Column(name = "diary_date", nullable = false) LocalDate  diaryDate;
    // weather: CLEAR | CLOUDY | RAIN | STORM | WIND
    @Column(length = 50) String weather;
    @Column(name = "temp_celsius") BigDecimal tempCelsius;
    @Column(name = "workers_present",  nullable = false) int    workersPresent = 0;
    @Column(name = "workers_planned")                    Integer workersPlanned;
    @Column(name = "work_description") String workDescription;
    @Column(name = "progress_notes")   String progressNotes;
    String issues;
    @Column(name = "visitor_names")    String visitorNames;
    String incidents;
    @Column(name = "toolbox_topic")    String toolboxTopic;
    @Column(name = "equipment_notes")  String equipmentNotes;
    @Column(name = "submitted_by")      UUID   submittedBy;
    @Column(name = "submitted_by_name") String submittedByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    // Unique constraint: (project_id, diary_date) — enforced in DB

    public static SiteDiary create(UUID tenantId, UUID projectId, LocalDate diaryDate,
                                   String weather, BigDecimal tempCelsius,
                                   int workersPresent, Integer workersPlanned,
                                   String workDescription, String progressNotes,
                                   String issues, String toolboxTopic, String equipmentNotes,
                                   String incidents, String visitorNames,
                                   UUID submittedBy, String submittedByName) {
        SiteDiary d         = new SiteDiary();
        d.id                = UUID.randomUUID();
        d.tenantId          = tenantId;
        d.projectId         = projectId;
        d.diaryDate         = diaryDate;
        d.weather           = weather;
        d.tempCelsius       = tempCelsius;
        d.workersPresent    = workersPresent;
        d.workersPlanned    = workersPlanned;
        d.workDescription   = workDescription;
        d.progressNotes     = progressNotes;
        d.issues            = issues;
        d.toolboxTopic      = toolboxTopic;
        d.equipmentNotes    = equipmentNotes;
        d.incidents         = incidents;
        d.visitorNames      = visitorNames;
        d.submittedBy       = submittedBy;
        d.submittedByName   = submittedByName;
        d.createdAt         = Instant.now();
        d.updatedAt         = Instant.now();
        return d;
    }

    public void setWeather(String v)          { this.weather          = v; touch(); }
    public void setWorkDescription(String v)  { this.workDescription  = v; touch(); }
    public void setProgressNotes(String v)    { this.progressNotes    = v; touch(); }
    public void setIssues(String v)           { this.issues           = v; touch(); }
    public void setIncidents(String v)        { this.incidents        = v; touch(); }
    public void setToolboxTopic(String v)     { this.toolboxTopic     = v; touch(); }
    public void setEquipmentNotes(String v)   { this.equipmentNotes   = v; touch(); }
    public void setWorkersPresent(int v)      { this.workersPresent   = v; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }
}
