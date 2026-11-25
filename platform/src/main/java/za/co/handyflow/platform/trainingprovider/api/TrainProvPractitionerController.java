package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPractitionerService;
import za.co.handyflow.platform.trainingprovider.dto.PractitionerDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/practitioners")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Practitioners", description = "Facilitators, assessors and moderators and their appointments")
public class TrainProvPractitionerController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String ADMIN = "hasAuthority('TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvPractitionerService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<PractitionerView>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), status, q, today())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<PractitionerDetail>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.detail(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @GetMapping("/eligible")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<EligibleView>>> eligible(@RequestParam UUID programmeId, @RequestParam String role,
                                                                    @RequestParam(required = false) LocalDate on) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.eligible(TenantContext.getTenantIdAsObject(), programmeId, role, on == null ? today() : on)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PractitionerView>> create(@Valid @RequestBody PractitionerRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Person added",
                service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PractitionerView>> update(@PathVariable UUID id, @Valid @RequestBody PractitionerRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Person updated", service.update(TenantContext.getTenantIdAsObject(), id, r, today())));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<PractitionerView>> deactivate(@PathVariable UUID id, @Valid @RequestBody PractitionerReason r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Person deactivated",
                service.deactivate(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(ADMIN)
    public ResponseEntity<ApiResponse<PractitionerView>> reactivate(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Person reactivated", service.reactivate(TenantContext.getTenantIdAsObject(), id, today())));
    }

    @PostMapping("/{id}/appointments")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AppointmentView>> appoint(@PathVariable UUID id, @Valid @RequestBody AppointmentRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Appointment recorded",
                service.appoint(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PutMapping("/appointments/{appointmentId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AppointmentView>> change(@PathVariable UUID appointmentId, @Valid @RequestBody AppointmentChange r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Appointment updated",
                service.change(TenantContext.getTenantIdAsObject(), appointmentId, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/appointments/{appointmentId}/verify")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AppointmentView>> verify(@PathVariable UUID appointmentId, @Valid @RequestBody VerifyRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Verification recorded",
                service.verify(TenantContext.getTenantIdAsObject(), appointmentId, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/appointments/{appointmentId}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AppointmentView>> withdraw(@PathVariable UUID appointmentId, @Valid @RequestBody PractitionerReason r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Appointment withdrawn",
                service.withdraw(TenantContext.getTenantIdAsObject(), appointmentId, r.reason(), TenantContext.getCurrentUserId(), today())));
    }
}
