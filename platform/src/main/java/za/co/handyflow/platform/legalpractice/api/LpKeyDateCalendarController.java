package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpMatterKeyDateService;
import za.co.handyflow.platform.legalpractice.dto.LpFirmKeyDateResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/legal-practice/key-dates")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Calendar", description = "Court dates and deadlines across every matter")
public class LpKeyDateCalendarController {

    private final LpMatterKeyDateService keyDateService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Key dates due between two dates (inclusive, at most 400 days), across all matters, with each matter's number and name")
    public ResponseEntity<ApiResponse<List<LpFirmKeyDateResponse>>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(
                keyDateService.listForFirm(TenantContext.getTenantIdAsObject(), from, to)));
    }
}
