package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.QuotationService;
import za.co.handyflow.platform.supplychain.application.internal.RfqComparisonService;
import za.co.handyflow.platform.supplychain.application.internal.RfqService;
import za.co.handyflow.platform.supplychain.dto.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * RFQs, the quotations captured against them, and the side-by-side comparison. The services existed without an API; this is the API.
 * Segregation of duties on award is applied in {@link RfqService#award} (the person who created the RFQ does not award it).
 */
@RestController
@RequestMapping("/api/v1/supply-chain/rfqs")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "RFQs and quotations")
public class ScmRfqController {

    private static final String CAN_READ = "hasAnyAuthority('SCM_READ','SCM_RFQ_MANAGE','SCM_ORDER','SCM_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_RFQ_MANAGE','SCM_ORDER','SCM_ADMIN')";
    private static final String CAN_AWARD = "hasAnyAuthority('SCM_RFQ_MANAGE','SCM_ADMIN')";
    private static final String CAN_PO = "hasAnyAuthority('SCM_PO_CREATE','SCM_ORDER','SCM_ADMIN')";

    private final FeatureGuard featureGuard;
    private final RfqService rfqs;
    private final QuotationService quotations;
    private final RfqComparisonService comparison;

    public record ReasonBody(String reason) {}
    public record PublishBody(String soleSourceJustification) {}
    public record ExtendBody(Instant closesAt, String reason) {}
    public record AwardBody(UUID quotationId, String reason) {}
    public record EvaluateBody(Integer technicalScore, String notes) {}

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T body) { return ResponseEntity.ok(ApiResponse.success(m, body)); }


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "RFQs, newest first (lines and invitations are left out of the list)")
    public ResponseEntity<ApiResponse<Page<RfqResponse>>> list(@RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ok("Success", rfqs.list(tenant(), status, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100))));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<RfqResponse>> get(@PathVariable UUID id) { return ok("Success", rfqs.get(tenant(), id)); }

    @GetMapping("/{id}/events")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<RfqEventResponse>>> events(@PathVariable UUID id) { return ok("Success", rfqs.events(tenant(), id)); }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RfqResponse>> create(@RequestBody CreateRfqRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft saved", rfqs.create(tenant(), uid(), uname(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RfqResponse>> update(@PathVariable UUID id, @RequestBody CreateRfqRequest req) { return ok("Draft saved", rfqs.update(tenant(), id, uid(), uname(), req)); }

    @PostMapping("/{id}/publish")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RfqResponse>> publish(@PathVariable UUID id, @RequestBody(required = false) PublishBody body) {
        return ok("RFQ published", rfqs.publish(tenant(), id, uid(), uname(), body != null ? body.soleSourceJustification() : null));
    }

    @PostMapping("/{id}/extend")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RfqResponse>> extend(@PathVariable UUID id, @RequestBody ExtendBody body) { return ok("Closing time changed", rfqs.extend(tenant(), id, body.closesAt(), body.reason(), uid(), uname())); }

    @PostMapping("/{id}/close")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RfqResponse>> close(@PathVariable UUID id) { return ok("RFQ closed", rfqs.close(tenant(), id, uid(), uname())); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<RfqResponse>> cancel(@PathVariable UUID id, @RequestBody ReasonBody body) { return ok("RFQ cancelled", rfqs.cancel(tenant(), id, body != null ? body.reason() : null, uid(), uname())); }

    @PostMapping("/{id}/award")
    @PreAuthorize(CAN_AWARD)
    @Operation(summary = "Award to a quotation. A reason is recorded, and a second person is needed: the RFQ's creator cannot award it unless nobody else can")
    public ResponseEntity<ApiResponse<RfqResponse>> award(@PathVariable UUID id, @RequestBody AwardBody body) {
        return ok("RFQ awarded", rfqs.award(tenant(), id, body.quotationId(), body.reason(), uid(), uname()));
    }

    @PostMapping("/{id}/create-po")
    @PreAuthorize(CAN_PO)
    public ResponseEntity<ApiResponse<RfqResponse>> createPo(@PathVariable UUID id) { return ok("Purchase order created", rfqs.createPo(tenant(), id, uid(), uname())); }

    // ── Quotations ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/comparison")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Side-by-side comparison with weighted scores and the recommendation (refused while quotations are sealed)")
    public ResponseEntity<ApiResponse<RfqComparisonResponse>> comparison(@PathVariable UUID id) { return ok("Success", comparison.compare(tenant(), id, true)); }

    @GetMapping("/{id}/quotations")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<QuotationResponse>>> quotations(@PathVariable UUID id) { return ok("Success", quotations.list(tenant(), id)); }

    @PostMapping("/{id}/quotations")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Capture a supplier's quotation")
    public ResponseEntity<ApiResponse<QuotationResponse>> submit(@PathVariable UUID id, @RequestBody QuotationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Quotation captured", quotations.submit(tenant(), id, uid(), uname(), req)));
    }

    @PostMapping("/{id}/quotations/{quotationId}/evaluate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<QuotationResponse>> evaluate(@PathVariable UUID id, @PathVariable UUID quotationId, @RequestBody EvaluateBody body) {
        return ok("Evaluation saved", quotations.evaluate(tenant(), id, quotationId, body.technicalScore(), body.notes(), uid(), uname()));
    }

    @PostMapping("/{id}/quotations/{quotationId}/disqualify")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<QuotationResponse>> disqualify(@PathVariable UUID id, @PathVariable UUID quotationId, @RequestBody ReasonBody body) {
        return ok("Quotation disqualified", quotations.disqualify(tenant(), id, quotationId, body != null ? body.reason() : null, uid(), uname()));
    }

    @PostMapping("/{id}/quotations/{quotationId}/withdraw")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<QuotationResponse>> withdraw(@PathVariable UUID id, @PathVariable UUID quotationId, @RequestBody ReasonBody body) {
        return ok("Quotation withdrawn", quotations.withdraw(tenant(), id, quotationId, body != null ? body.reason() : null, uid(), uname()));
    }
}
