package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.*;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.legalcompliance.dto.AssuranceDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Control testing. Evidence files are uploaded through the shared evidence endpoint (POST /api/v1/evidence) with
 * sourceModule=legalcompliance, relatedEntityType=CONTROL_TEST and relatedEntityId=the test id; submission counts what is attached.
 */
@RestController
@RequestMapping("/api/v1/legalcompliance/control-tests")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Control Testing", description = "Test controls, review the tests, and set operating effectiveness")
public class ControlTestController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_CONTROL_TEST','LEGALCOMPLIANCE_CONTROL_TEST_REVIEW','LEGALCOMPLIANCE_CONTROL_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_TEST = "hasAnyAuthority('LEGALCOMPLIANCE_CONTROL_TEST','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_REVIEW = "hasAnyAuthority('LEGALCOMPLIANCE_CONTROL_TEST_REVIEW','LEGALCOMPLIANCE_ADMIN')";

    private final ControlTestService tests;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<TestResponse>>> list(@RequestParam(required = false) ControlTestStatus status, @RequestParam(required = false) UUID controlId) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(tests.list(tenant(), status, controlId)));
    }

    @GetMapping("/summary")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<TestSummary>> summary() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(tests.summary(tenant())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<TestDetailResponse>> detail(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(tests.detail(tenant(), id)));
    }

    @PostMapping
    @PreAuthorize(CAN_TEST)
    @Operation(summary = "Schedule a test of an active control (one open test per control)")
    public ResponseEntity<ApiResponse<TestResponse>> schedule(@Valid @RequestBody ScheduleTestRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Test scheduled", tests.schedule(tenant(), req, uid(), uname())));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(CAN_TEST)
    public ResponseEntity<ApiResponse<TestResponse>> start(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Test started", tests.start(tenant(), id, uid(), uname())));
    }

    @PutMapping("/{id}/progress")
    @PreAuthorize(CAN_TEST)
    public ResponseEntity<ApiResponse<TestResponse>> progress(@PathVariable UUID id, @RequestBody ProgressRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Saved", tests.saveProgress(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(CAN_TEST)
    @Operation(summary = "Submit the result for review. Needs a sample size, notes and at least one attached evidence file.")
    public ResponseEntity<ApiResponse<TestResponse>> submit(@PathVariable UUID id, @Valid @RequestBody SubmitTestRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Submitted for review", tests.submit(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_REVIEW)
    @Operation(summary = "Approve the submitted test. Sets the control's operating effectiveness and raises a finding for a partial or failed result. The tester cannot approve their own test.")
    public ResponseEntity<ApiResponse<TestDetailResponse>> approve(@PathVariable UUID id, @RequestBody(required = false) NoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Test approved", tests.approve(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(CAN_REVIEW)
    public ResponseEntity<ApiResponse<TestResponse>> returnTest(@PathVariable UUID id, @RequestBody NoteRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Returned to the tester", tests.returnForRework(tenant(), id, req, uid(), uname())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_TEST)
    public ResponseEntity<ApiResponse<TestResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Test cancelled", tests.cancel(tenant(), id, req, uid(), uname())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
