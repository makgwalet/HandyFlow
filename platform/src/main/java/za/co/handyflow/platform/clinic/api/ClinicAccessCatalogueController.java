package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.clinic.application.internal.ClinicPermission;
import za.co.handyflow.platform.clinic.application.internal.ClinicRoleTemplates;
import za.co.handyflow.platform.clinic.dto.AccessCatalogueDtos.Catalogue;
import za.co.handyflow.platform.clinic.dto.AccessCatalogueDtos.PermissionInfo;
import za.co.handyflow.platform.clinic.dto.AccessCatalogueDtos.RoleTemplateInfo;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/clinic/access")
@Tag(name = "Clinic access catalogue", description = "Every Clinic permission and the starting role templates")
public class ClinicAccessCatalogueController {

    @GetMapping("/catalogue")
    @PreAuthorize("hasAuthority('CLINIC_ADMIN')")
    @Operation(summary = "The permission catalogue, the coarse legacy permissions and the role templates")
    public ResponseEntity<ApiResponse<Catalogue>> catalogue() {
        List<PermissionInfo> permissions = Arrays.stream(ClinicPermission.values())
                .map(p -> new PermissionInfo(p.code(), p.group().name(), p.kind().name(), p.legacy().orElse(null), p.enforced(), p.description()))
                .toList();
        List<RoleTemplateInfo> roles = ClinicRoleTemplates.all().values().stream()
                .map(t -> new RoleTemplateInfo(t.key(), t.name(), t.description(),
                        t.permissions().stream().map(ClinicPermission::code).sorted(Comparator.naturalOrder()).toList()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Success",
                new Catalogue(permissions, ClinicPermission.LEGACY.stream().sorted().toList(), roles)));
    }
}
