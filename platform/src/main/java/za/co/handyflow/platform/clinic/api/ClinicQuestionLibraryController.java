package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicContentGovernanceService;
import za.co.handyflow.platform.clinic.application.internal.ClinicQuestionAuthoringService;
import za.co.handyflow.platform.clinic.application.internal.ClinicQuestionLibraryService;
import za.co.handyflow.platform.clinic.dto.QuestionLibraryDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Clinical question library. Module entitlement is enforced by ClinicModuleGuardConfig. */
@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic question library", description = "Reusable question groups, rule evaluation and answers")
public class ClinicQuestionLibraryController {

    private final ClinicQuestionLibraryService library;
    private final ClinicContentGovernanceService governance;
    private final ClinicQuestionAuthoringService authoring;

    // ── Clinicians ───────────────────────────────────────────────────────────

    @GetMapping("/question-groups")
    @PreAuthorize("hasAuthority('CLINIC_QUESTIONNAIRE_READ')")
    @Operation(summary = "Question groups for a visit type (ACTIVE content only), filtered for the patient")
    public ResponseEntity<ApiResponse<List<GroupView>>> forVisit(
            @RequestParam String visitType, @RequestParam(required = false) UUID patientId,
            @RequestParam(defaultValue = "QUESTIONNAIRE") String kind) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                library.groupsForVisit(TenantContext.getTenantIdAsObject(), visitType, patientId, "EXAMINATION".equalsIgnoreCase(kind))));
    }

    @GetMapping("/question-groups/{code}")
    @PreAuthorize("hasAuthority('CLINIC_QUESTIONNAIRE_READ')")
    @Operation(summary = "One served question group by code (for groups opened by a TRIGGER_GROUP rule)")
    public ResponseEntity<ApiResponse<GroupView>> one(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                library.servedGroup(TenantContext.getTenantIdAsObject(), code)));
    }

    @PostMapping("/question-groups/{code}/evaluate")
    @PreAuthorize("hasAuthority('CLINIC_QUESTIONNAIRE_READ')")
    @Operation(summary = "Evaluate answers: what is visible, required, warned, triggered and flagged. Never a diagnosis.")
    public ResponseEntity<ApiResponse<EvaluationView>> evaluate(@PathVariable String code, @RequestBody EvaluateRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Success", library.evaluate(
                TenantContext.getTenantIdAsObject(), code, body.patientId(), body.visitType(), body.answers())));
    }

    @GetMapping("/consultations/{id}/form-data")
    @PreAuthorize("hasAuthority('CLINIC_QUESTIONNAIRE_READ')")
    @Operation(summary = "Answers stored on a consultation, by group")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFormData(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                library.getFormData(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/consultations/{id}/form-data/{groupCode}")
    @PreAuthorize("hasAuthority('CLINIC_QUESTIONNAIRE_ANSWER')")
    @Operation(summary = "Save one group's answers on a consultation (validated; hidden answers are dropped)")
    public ResponseEntity<ApiResponse<EvaluationView>> saveAnswers(
            @PathVariable UUID id, @PathVariable String groupCode, @RequestBody SaveAnswersRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Saved", library.saveAnswers(
                TenantContext.getTenantIdAsObject(), id, groupCode, body.answers())));
    }

    // ── Governance ───────────────────────────────────────────────────────────

    @GetMapping("/question-groups/admin")
    @PreAuthorize("hasAnyAuthority('CLINIC_CONTENT_ADMIN','CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "All groups with review status (including DRAFT and demo)")
    public ResponseEntity<ApiResponse<List<GroupSummary>>> adminList() {
        return ResponseEntity.ok(ApiResponse.success("Success", governance.list(TenantContext.getTenantIdAsObject())));
    }

    // ── Authoring ────────────────────────────────────────────────────────────

    @GetMapping("/question-groups/{groupId}/definition")
    @PreAuthorize("hasAnyAuthority('CLINIC_CONTENT_ADMIN','CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "Full definition of a group in any status, for editing or review (platform groups are read-only)")
    public ResponseEntity<ApiResponse<GroupDefinitionView>> definition(@PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                authoring.definition(TenantContext.getTenantIdAsObject(), groupId)));
    }

    @PostMapping("/question-groups")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Create a new question group as a DRAFT (version 1)")
    public ResponseEntity<ApiResponse<UUID>> createGroup(@RequestBody CreateGroupRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Group created", authoring.create(
                TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), body.code(), body.definition())));
    }

    @PutMapping("/question-groups/{groupId}/definition")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Replace a group's definition. Only while DRAFT or CHANGES_REQUESTED; fully validated.")
    public ResponseEntity<ApiResponse<Void>> replaceDefinition(@PathVariable UUID groupId, @RequestBody GroupDefinition body) {
        authoring.replace(TenantContext.getTenantIdAsObject(), groupId, UserContext.getCurrentUserId(), body);
        return ResponseEntity.ok(ApiResponse.success("Saved", null));
    }

    @PostMapping("/question-groups/{groupId}/new-version")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "Copy a group into a new DRAFT version (refused while another version is in progress)")
    public ResponseEntity<ApiResponse<UUID>> newVersion(@PathVariable UUID groupId) {
        return ResponseEntity.status(201).body(ApiResponse.success("New version created", authoring.newVersion(
                TenantContext.getTenantIdAsObject(), groupId, UserContext.getCurrentUserId())));
    }

    @PostMapping("/question-groups/{groupId}/submit-for-review")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_ADMIN')")
    @Operation(summary = "DRAFT or CHANGES_REQUESTED to CLINICAL_REVIEW; needs a clinical source and valid rules")
    public ResponseEntity<ApiResponse<Void>> submit(@PathVariable UUID groupId, @RequestBody(required = false) StatusChangeRequest body) {
        governance.changeStatus(TenantContext.getTenantIdAsObject(), groupId, "CLINICAL_REVIEW",
                UserContext.getCurrentUserId(), body == null ? null : body.note());
        return ResponseEntity.ok(ApiResponse.success("Sent for review", null));
    }

    @PostMapping("/question-groups/{groupId}/status")
    @PreAuthorize("hasAuthority('CLINIC_CONTENT_APPROVE')")
    @Operation(summary = "Review outcome and lifecycle: APPROVED, CHANGES_REQUESTED, ACTIVE (a different person from the reviewer), DEPRECATED, RETIRED")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable UUID groupId, @RequestBody StatusChangeRequest body) {
        governance.changeStatus(TenantContext.getTenantIdAsObject(), groupId, body.status(),
                UserContext.getCurrentUserId(), body.note());
        return ResponseEntity.ok(ApiResponse.success("Status updated", null));
    }
}
