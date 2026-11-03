package za.co.handyflow.platform.documents.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.documents.application.DocumentStore;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.UUID;

/**
 * Platform administrators only (the admin portal sign-in): the state of the document store across all tenants, and a check
 * started by hand. Tenant staff never see this; it names storage keys and tenants.
 */
@RestController
@RequestMapping("/api/v1/admin/document-store")
@PreAuthorize("hasRole('SUPERADMIN')")
public class DocumentStoreAdminController {

    private final DocumentStore store;

    public DocumentStoreAdminController(DocumentStore store) { this.store = store; }

    /** Counts by check status, the last run, and up to 50 failing files (most recently checked first). */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<DocumentStore.Health>> health() {
        return ResponseEntity.ok(ApiResponse.success(store.health()));
    }

    /** Checks up to {@code limit} files now (1 to 5,000, default 200), those checked longest ago first. 409 while another check runs. */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<DocumentStore.RunSummary>> verify(@RequestParam(defaultValue = "200") int limit) {
        try {
            DocumentStore.RunSummary r = store.verifyNow(limit, adminId());
            return ResponseEntity.ok(ApiResponse.success(r.failed() > 0 || Boolean.FALSE.equals(r.canaryOk())
                    ? "Check finished: problems found" : "Check finished: all checked files are intact", r));
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "CHECK_RUNNING");
        }
    }

    private static UUID adminId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        try { return UUID.fromString(auth.getPrincipal().toString()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
