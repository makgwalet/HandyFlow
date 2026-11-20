package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpAccessRules;
import za.co.handyflow.platform.legalpractice.application.internal.LpPermission;
import za.co.handyflow.platform.legalpractice.application.internal.LpRoleTemplates;
import za.co.handyflow.platform.legalpractice.dto.AccessDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.Comparator;
import java.util.List;

/** The Legal Practice permission catalogue, the starting role templates and a check for risky combinations. Read only: roles themselves are built in Roles. */
@RestController("legalPracticeAccessController")
@RequestMapping("/api/v1/legal-practice/access")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Access", description = "Permission catalogue, role templates and role checks")
public class LpAccessController {

    private final FeatureGuard featureGuard;

    static List<FindingInfo> findings(java.util.Collection<String> codes) {
        return LpAccessRules.check(codes).stream().map(f -> new FindingInfo(f.severity().name(), f.code(), f.message())).toList();
    }

    @GetMapping("/catalogue")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Every Legal Practice permission and the role templates, each template already checked")
    public ResponseEntity<ApiResponse<Catalogue>> catalogue() {
        featureGuard.requireModule("legalpractice");
        List<PermissionInfo> permissions = java.util.Arrays.stream(LpPermission.values())
                .map(p -> new PermissionInfo(p.code(), p.group(), p.kind().name(), p.level().name(), p.secondPerson(), p.description())).toList();
        List<TemplateInfo> templates = LpRoleTemplates.all().values().stream().map(t -> {
            List<String> codes = t.permissions().stream().map(LpPermission::code).sorted(Comparator.naturalOrder()).toList();
            return new TemplateInfo(t.key(), t.name(), t.description(), codes, findings(codes));
        }).toList();
        return ResponseEntity.ok(ApiResponse.success(new Catalogue(permissions, templates)));
    }

    @PostMapping("/check")
    @PreAuthorize("hasAuthority('LEGALPRACTICE_ADMIN')")
    @Operation(summary = "Check a set of permissions for combinations a law firm should avoid")
    public ResponseEntity<ApiResponse<CheckResult>> check(@RequestBody CheckRequest req) {
        featureGuard.requireModule("legalpractice");
        List<String> codes = req.permissions() == null ? List.of() : req.permissions();
        return ResponseEntity.ok(ApiResponse.success(new CheckResult(LpAccessRules.effective(codes).size(), findings(codes))));
    }
}
