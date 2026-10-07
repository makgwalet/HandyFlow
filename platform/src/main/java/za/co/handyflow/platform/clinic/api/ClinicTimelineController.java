package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicTimelineService;
import za.co.handyflow.platform.clinic.dto.TimelineEvent;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}")
@RequiredArgsConstructor
@Tag(name = "Clinic timeline", description = "A patient's visits, prescriptions, results, claims and payments in one list")
public class ClinicTimelineController {

    private final ClinicTimelineService timeline;

    @GetMapping("/timeline")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Newest-first timeline. kinds is a comma list (APPOINTMENT, CONSULTATION, PRESCRIPTION, LAB, CLAIM, PAYMENT); "
            + "claims and payments are only included for users with CLINIC_BILLING_READ.")
    public ResponseEntity<ApiResponse<List<TimelineEvent>>> get(
            @PathVariable UUID patientId,
            @RequestParam(required = false) String kinds,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer limit,
            Authentication auth) {
        boolean billing = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "CLINIC_BILLING_READ".equals(a.getAuthority()));
        Set<String> kindSet = kinds == null || kinds.isBlank() ? null
                : Arrays.stream(kinds.split(",")).map(String::trim).map(String::toUpperCase)
                        .filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        return ResponseEntity.ok(ApiResponse.success("Success",
                timeline.timeline(TenantContext.getTenantIdAsObject(), patientId, kindSet, from, to, billing, limit)));
    }
}
