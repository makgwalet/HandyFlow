package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.security.application.internal.SecurityRoleTemplates;
import za.co.handyflow.platform.shared.ApiResponse;

@Tag(name = "Security - Role templates")
@RestController
@RequestMapping("/api/v1/security/role-templates")
public class SecurityRoleTemplateController {

    public record TemplateInfo(String key, String name, String description, List<String> permissions) {}
    public record ConflictInfo(String a, String b, String why) {}
    public record Catalogue(List<TemplateInfo> templates, List<ConflictInfo> conflicts) {}

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Starting-point role templates for Security",
            description = "Pick one when creating a role, then adjust. Also lists permission pairs one person should not hold together.")
    public ResponseEntity<ApiResponse<Catalogue>> list() {
        var templates = SecurityRoleTemplates.list().stream()
                .map(t -> new TemplateInfo(t.key(), t.name(), t.description(), t.permissions().stream().sorted().toList()))
                .toList();
        var conflicts = SecurityRoleTemplates.conflicts().stream()
                .map(c -> new ConflictInfo(c.a(), c.b(), c.why())).toList();
        return ResponseEntity.ok(ApiResponse.success(new Catalogue(templates, conflicts)));
    }
}
