// security/application/internal/ReadinessSettingsService.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.GuardDocument;
import za.co.handyflow.platform.security.domain.model.GuardReadinessSettings;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord;
import za.co.handyflow.platform.security.domain.repository.GuardReadinessSettingsRepository;
import za.co.handyflow.platform.security.dto.ReadinessSettingsDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads and saves which screenings and guard-file documents a tenant requires for deployment readiness.
 * Until something is saved the defaults apply. The PSiRA registration is not a choice: it is always required.
 */
@Service
@RequiredArgsConstructor
public class ReadinessSettingsService {

    private final GuardReadinessSettingsRepository repository;

    @Transactional(readOnly = true)
    public GuardReadinessCalculator.Requirements effective(TenantId tenantId) {
        return repository.findForTenant(tenantId).map(GuardReadinessSettings::toRequirements)
                .orElseGet(GuardReadinessCalculator.Requirements::defaults);
    }

    @Transactional(readOnly = true)
    public ReadinessSettingsDto get(TenantId tenantId) {
        return repository.findForTenant(tenantId)
                .map(s -> dto(s.toRequirements(), true, s.getUpdatedByName(), s.getUpdatedAt()))
                .orElseGet(() -> dto(GuardReadinessCalculator.Requirements.defaults(), false, null, null));
    }

    @Transactional
    public ReadinessSettingsDto save(TenantId tenantId, SaveReadinessSettingsRequest req, String byName) {
        Set<String> screening = clean(req.requiredScreening(), screeningValues(), "screening type");
        Set<String> documents = clean(req.requiredDocuments(), documentValues(), "document");
        var requirements = new GuardReadinessCalculator.Requirements(screening, documents);
        GuardReadinessSettings row = repository.findForTenant(tenantId).orElseGet(() -> GuardReadinessSettings.forTenant(tenantId));
        row.apply(requirements, byName);
        repository.save(row);
        return dto(requirements, true, row.getUpdatedByName(), row.getUpdatedAt());
    }

    private static Set<String> clean(List<String> values, Set<String> allowed, String what) {
        Set<String> out = new LinkedHashSet<>();
        for (String v : values) {
            String name = v == null ? "" : v.trim().toUpperCase();
            if (name.isEmpty()) continue;
            if (!allowed.contains(name))
                throw new HandyFlowException("Unknown " + what + ": " + v, HttpStatus.BAD_REQUEST, "INVALID_READINESS_SETTINGS");
            out.add(name);
        }
        return out;
    }

    /** OTHER is a catch-all and cannot be a requirement. */
    static Set<String> screeningValues() {
        return Arrays.stream(GuardScreeningRecord.ScreeningType.values()).map(Enum::name).filter(n -> !n.equals("OTHER"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    static Set<String> documentValues() {
        return Arrays.stream(GuardDocument.Category.values()).map(Enum::name)
                .filter(n -> !n.equals("OTHER"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    static ReadinessSettingsDto dto(GuardReadinessCalculator.Requirements r, boolean customised, String by, java.time.Instant at) {
        return new ReadinessSettingsDto(List.copyOf(r.screening()), List.copyOf(r.documents()),
                screeningValues().stream().map(v -> new Option(v, GuardReadinessCalculator.labelOf(v))).toList(),
                documentValues().stream().map(v -> new Option(v, GuardReadinessCalculator.labelOfDocument(v))).toList(),
                customised, by, at);
    }
}
