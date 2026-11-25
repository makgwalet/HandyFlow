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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvAttendanceService;
import za.co.handyflow.platform.trainingprovider.dto.AttendanceDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Attendance", description = "Attendance registers per timetable slot")
public class TrainProvAttendanceController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvAttendanceService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/timetable/{slotId}/attendance")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Register>> register(@PathVariable UUID slotId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.register(TenantContext.getTenantIdAsObject(), slotId, today())));
    }

    @PostMapping("/timetable/{slotId}/attendance")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Register>> record(@PathVariable UUID slotId, @Valid @RequestBody RecordRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Attendance recorded",
                service.record(TenantContext.getTenantIdAsObject(), slotId, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/attendance/{attendanceId}/correct")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<Register>> correct(@PathVariable UUID attendanceId, @Valid @RequestBody CorrectRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Attendance corrected",
                service.correct(TenantContext.getTenantIdAsObject(), attendanceId, r, TenantContext.getCurrentUserId(), today())));
    }

    @GetMapping("/attendance/{attendanceId}/history")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<HistoryLine>>> history(@PathVariable UUID attendanceId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.historyOf(TenantContext.getTenantIdAsObject(), attendanceId)));
    }

    @GetMapping("/cohorts/{cohortId}/attendance-summary")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CohortSummary>> summary(@PathVariable UUID cohortId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.summary(TenantContext.getTenantIdAsObject(), cohortId, today())));
    }

    @GetMapping("/learners/{learnerId}/cohorts")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<LearnerCohorts>> learnerCohorts(@PathVariable UUID learnerId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.forLearner(TenantContext.getTenantIdAsObject(), learnerId, today())));
    }
}
