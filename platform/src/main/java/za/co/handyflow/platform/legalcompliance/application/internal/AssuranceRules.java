package za.co.handyflow.platform.legalcompliance.application.internal;

import za.co.handyflow.platform.legalcompliance.domain.model.AuditFindingType;
import za.co.handyflow.platform.legalcompliance.domain.model.CapaStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.FindingSeverity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Pure rules for audits, audit findings and CAPA. No Spring, no database. */
public final class AssuranceRules {

    private AssuranceRules() {}

    /** CAPAs that still stand between a finding and closure: anything not verified and not cancelled. */
    public static long blockingCapas(Collection<CapaStatus> statuses) {
        return statuses.stream().filter(s -> s != CapaStatus.VERIFIED && s != CapaStatus.CANCELLED).count();
    }

    /**
     * Why an audit finding cannot be closed yet, or null if it can. Observations may be closed with a note alone; everything else needs at
     * least one corrective/preventive action and every non-cancelled action verified.
     */
    public static String closeBlocker(AuditFindingType type, Collection<CapaStatus> capaStatuses) {
        long live = capaStatuses.stream().filter(s -> s != CapaStatus.CANCELLED).count();
        if (type == AuditFindingType.OBSERVATION && live == 0) return null;
        if (live == 0) return "A " + type.name().toLowerCase().replace('_', ' ') + " needs at least one corrective or preventive action before it can be closed";
        long blocking = blockingCapas(capaStatuses);
        return blocking == 0 ? null : blocking + " corrective/preventive action(s) have not been verified yet";
    }

    /** Independence: an internal auditor may not audit a control they own. Returns the conflicting owner ids that match the auditor. */
    public static boolean auditorOwnsScopeControl(UUID auditorId, Collection<UUID> scopeControlOwnerIds) {
        return auditorId != null && scopeControlOwnerIds.stream().anyMatch(auditorId::equals);
    }

    public static LocalDate defaultDue(FindingSeverity severity, LocalDate from) { return from.plusDays(ControlTestRules.remediationDays(severity)); }

    public static boolean isOverdue(LocalDate due, boolean open, LocalDate today) { return open && due != null && due.isBefore(today); }

    public static List<CapaStatus> open() { return List.of(CapaStatus.PROPOSED, CapaStatus.APPROVED, CapaStatus.IN_PROGRESS, CapaStatus.COMPLETED); }
}
