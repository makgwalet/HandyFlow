package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import za.co.handyflow.platform.agriculture.domain.rules.AgAuditRules;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Stamps who added and last changed a record. Runs inside the save, so every route that saves a record (screens, imports, jobs)
 * is covered without each service remembering. With no signed-in person (a scheduled job, a support session) it leaves the
 * names empty rather than inventing one.
 */
public class AgAuditListener {

    @PrePersist
    public void onCreate(Object entity) {
        if (!(entity instanceof AgAudited a)) return;
        Who w = who();
        stamp(a).recordCreated(w.id(), w.name());
    }

    @PreUpdate
    public void onUpdate(Object entity) {
        if (!(entity instanceof AgAudited a)) return;
        Who w = who();
        if (w.id() == null && w.name() == null) return;
        stamp(a).recordUpdated(w.id(), w.name());
    }

    /** Rows loaded from before this existed come back with no stamp, so one is made on first change. */
    private static AgAuditStamp stamp(AgAudited a) {
        if (a.getAudit() == null) a.setAudit(new AgAuditStamp());
        return a.getAudit();
    }

    private record Who(UUID id, String name) {}

    private static Who who() {
        UUID id;
        try { id = TenantContext.getCurrentUserId(); } catch (RuntimeException e) { id = null; }
        String name;
        try { name = TenantContext.getCurrentUserName(); } catch (RuntimeException e) { name = null; }
        return new Who(id, AgAuditRules.cleanName(id, name));
    }
}
