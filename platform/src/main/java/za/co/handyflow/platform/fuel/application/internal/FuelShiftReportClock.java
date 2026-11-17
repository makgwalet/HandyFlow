package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.fuel.domain.repository.FuelPosImportRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.ShiftBasis;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which time dates a shift in the shifts-by-date reports, and which timezone decides the day. The company chooses in Settings: the system's own closing
 * time (as always), or, for imported shifts, the closing time the pump/POS provider reported. The scheduled/system times are never overwritten. A shift
 * that came from an import but has no provider closing time falls back to the system time and is counted, so the report can say so.
 */
@Component
@RequiredArgsConstructor
public class FuelShiftReportClock {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Johannesburg");
    public static final Basis DEFAULT_BASIS = new Basis(false, DEFAULT_ZONE);

    private final FuelSettingsRepository settings;
    private final FuelPosImportRepository imports;

    /** The rule for a report. {@code provider} true dates imported shifts by the provider's closing time; {@code zone} decides the day. */
    public record Basis(boolean provider, ZoneId zone) {
        /** The time that dates this shift in a report. */
        public Instant closedFor(FuelShift s) {
            return provider && s.getProviderClosedAt() != null ? s.getProviderClosedAt() : s.getClosedAt();
        }
        public boolean usedProviderTime(FuelShift s) { return provider && s.getProviderClosedAt() != null; }
        public String label() { return provider ? "PROVIDER" : "SYSTEM"; }
    }

    @Transactional(readOnly = true)
    public Basis basis(TenantId tenantId) {
        var s = settings.findByTenant(tenantId).orElse(null);
        if (s == null) return DEFAULT_BASIS;
        return new Basis(s.isShiftReportUseProviderTimes(), zoneOf(s.getReportTimezone()));
    }

    static ZoneId zoneOf(String id) {
        if (id == null || id.isBlank()) return DEFAULT_ZONE;
        try { return ZoneId.of(id.trim()); } catch (RuntimeException e) { return DEFAULT_ZONE; }
    }

    /**
     * What the report says about its dating: the basis, the timezone, and how many of the shifts in it came from an import with no provider closing time,
     * so their system time was used instead. Zero when the basis is the system time.
     */
    @Transactional(readOnly = true)
    public ShiftBasis describe(TenantId tenantId, Basis basis, Collection<FuelShift> inReport) {
        long fallbacks = 0;
        if (basis.provider() && !inReport.isEmpty()) {
            Set<UUID> withoutProviderTime = inReport.stream().filter(s -> s.getProviderClosedAt() == null).map(FuelShift::getId).collect(Collectors.toSet());
            if (!withoutProviderTime.isEmpty()) {
                List<UUID> imported = imports.findShiftIdsAmong(tenantId, withoutProviderTime);
                fallbacks = new HashSet<>(imported).size();
            }
        }
        return new ShiftBasis(basis.label(), basis.zone().getId(), fallbacks);
    }
}
