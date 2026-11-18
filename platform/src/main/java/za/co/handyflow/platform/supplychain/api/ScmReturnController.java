package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.SupplierReturnService;
import za.co.handyflow.platform.supplychain.dto.ReturnDtos.*;

import java.util.List;
import java.util.UUID;

/** Returns to suppliers and claims against them. */
@RestController
@RequestMapping("/api/v1/supply-chain/returns")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Supplier returns and claims")
public class ScmReturnController {

    private static final String CAN_READ = "hasAnyAuthority('SCM_READ','SCM_RETURN_MANAGE','SCM_RETURN_APPROVE','SCM_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_RETURN_MANAGE','SCM_ADMIN')";
    private static final String CAN_APPROVE = "hasAnyAuthority('SCM_RETURN_APPROVE','SCM_ADMIN')";

    private final SupplierReturnService service;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T body) { return ResponseEntity.ok(ApiResponse.success(m, body)); }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Returns and claims, newest first; filter by status or supplier")
    public ResponseEntity<ApiResponse<List<Response>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) UUID supplierId) {
        return ok("Success", service.list(tenant(), status, supplierId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable UUID id) { return ok("Success", service.get(tenant(), id)); }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Raise a return (goods go back) or a claim (credit only) as a draft")
    public ResponseEntity<ApiResponse<Response>> create(@RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft raised", service.create(tenant(), uid(), uname(), req)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<Response>> submit(@PathVariable UUID id) { return ok("Submitted for approval", service.submit(tenant(), id)); }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Approve — a second person: the one who raised it cannot, unless nobody else holds SCM_RETURN_APPROVE")
    public ResponseEntity<ApiResponse<Response>> approve(@PathVariable UUID id, @RequestBody(required = false) DecisionRequest req) {
        return ok("Approved", service.approve(tenant(), id, uid(), uname(), req != null ? req.note() : null));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<Response>> reject(@PathVariable UUID id, @RequestBody DecisionRequest req) {
        return ok("Rejected", service.reject(tenant(), id, req != null ? req.note() : null));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<Response>> cancel(@PathVariable UUID id, @RequestBody DecisionRequest req) {
        return ok("Cancelled", service.cancel(tenant(), id, req != null ? req.note() : null));
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Goods leave: takes the stock out of the receiving location (RETURN only)")
    public ResponseEntity<ApiResponse<Response>> dispatch(@PathVariable UUID id) { return ok("Dispatched", service.dispatch(tenant(), id, uid(), uname())); }

    @PostMapping("/{id}/credit")
    @PreAuthorize(CAN_APPROVE)
    @Operation(summary = "Record the supplier's credit note against this return or claim")
    public ResponseEntity<ApiResponse<Response>> credit(@PathVariable UUID id, @RequestBody CreditRequest req) { return ok("Credit recorded", service.recordCredit(tenant(), id, req, uid())); }
}
