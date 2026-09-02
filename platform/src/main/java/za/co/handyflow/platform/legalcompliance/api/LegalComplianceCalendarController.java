package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.LegalComplianceCalendarService;
import za.co.handyflow.platform.legalcompliance.dto.CalendarEntryResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;

/** Aggregated calendar — obligation review dates + litigation key dates + contract renewals (via ContractingFacade). */
@RestController
@RequestMapping("/api/v1/legalcompliance/calendar")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Calendar", description = "Aggregated obligation/litigation/contract-renewal calendar")
public class LegalComplianceCalendarController {

    private final LegalComplianceCalendarService calendarService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')")
    @Operation(summary = "Upcoming obligation reviews, litigation key dates, and contract renewals within N days")
    public ResponseEntity<ApiResponse<List<CalendarEntryResponse>>> upcoming(
            @RequestParam(defaultValue = "30") int days) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(
                calendarService.upcoming(TenantContext.getTenantIdAsObject(), days)));
    }
}
