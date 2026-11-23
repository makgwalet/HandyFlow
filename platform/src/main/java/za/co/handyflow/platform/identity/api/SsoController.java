package za.co.handyflow.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.identity.application.internal.SsoPolicyService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@Tag(name = "Single sign-on settings")
@RestController
@RequestMapping("/api/v1/identity/sso")
@RequiredArgsConstructor
public class SsoController {

    private final SsoPolicyService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "The company's single sign-on settings. No secret is held here, only the name of the server setting that holds it")
    public ResponseEntity<ApiResponse<SsoPolicyService.Settings>> get() {
        return ResponseEntity.ok(ApiResponse.success("OK", service.get(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Save the single sign-on settings (replaces them). Enforcing needs people who keep a password, and you cannot lock yourself out. Audited",
            description = "The provider hand-off is not part of this release: until it is, enforcing refuses password sign-in for the listed domains for everyone except the break-glass people.")
    public ResponseEntity<ApiResponse<SsoPolicyService.Settings>> save(@RequestBody SsoPolicyService.Request r) {
        return ResponseEntity.ok(ApiResponse.success("OK", service.save(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), r)));
    }
}
