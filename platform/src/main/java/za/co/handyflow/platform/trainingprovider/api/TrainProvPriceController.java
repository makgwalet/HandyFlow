package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPriceService;
import za.co.handyflow.platform.trainingprovider.dto.PriceDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/price-lines")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Price books", description = "Fees the provider records for a programme version, shown to invoicing as suggestions only")
public class TrainProvPriceController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvPriceService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<PriceView>>> list(@RequestParam UUID programmeVersionId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), programmeVersionId, status, today())));
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<PriceView>> create(@Valid @RequestBody PriceRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Fee recorded", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<PriceView>> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Fee withdrawn", service.withdraw(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId(), today())));
    }

    @GetMapping("/suggest")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Suggestion>> suggest(@RequestParam UUID enrolmentId, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate on) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.suggest(TenantContext.getTenantIdAsObject(), enrolmentId, on == null ? today() : on)));
    }
}
