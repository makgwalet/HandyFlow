package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.ContactService;
import za.co.handyflow.platform.crm.dto.ContactRequest;
import za.co.handyflow.platform.crm.dto.ContactResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/customers/{customerId}/contacts")
@RequiredArgsConstructor
@Tag(name = "CRM - Contacts", description = "The people at a customer")
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "List a customer's contacts, main contact first")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> list(@PathVariable UUID customerId) {
        return ResponseEntity.ok(ApiResponse.success(contactService.list(TenantContext.getTenantIdAsObject(), customerId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a contact (the first one becomes the main contact)")
    public ResponseEntity<ApiResponse<ContactResponse>> create(@PathVariable UUID customerId, @Valid @RequestBody ContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Contact added",
                contactService.create(TenantContext.getTenantIdAsObject(), customerId, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Edit a contact")
    public ResponseEntity<ApiResponse<ContactResponse>> update(@PathVariable UUID customerId, @PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contactService.update(TenantContext.getTenantIdAsObject(), customerId, id, request)));
    }

    @PostMapping("/{id}/primary")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Make this the customer's main contact")
    public ResponseEntity<ApiResponse<ContactResponse>> makePrimary(@PathVariable UUID customerId, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(contactService.makePrimary(TenantContext.getTenantIdAsObject(), customerId, id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Remove a contact (soft delete). Removing the main contact promotes the earliest-added one.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID customerId, @PathVariable UUID id) {
        contactService.delete(TenantContext.getTenantIdAsObject(), customerId, id);
        return ResponseEntity.ok(ApiResponse.success("Contact removed", null));
    }
}
