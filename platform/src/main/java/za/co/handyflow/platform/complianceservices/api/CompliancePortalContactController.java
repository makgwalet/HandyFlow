package za.co.handyflow.platform.complianceservices.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.complianceservices.application.internal.CompliancePortalContactService;
import za.co.handyflow.platform.complianceservices.dto.ContactDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** A client contact writing to the practice and seeing what the practice has asked for. Gated by the portal token; the service works out tenant and client. */
@RestController
@RequestMapping("/api/v1/compliance-services/portal/me")
@RequiredArgsConstructor
@Tag(name = "Compliance Services Client Portal - Contact", description = "Queries and document requests")
public class CompliancePortalContactController {

    private static final String PORTAL = "hasAuthority('PORTAL_USER')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final CompliancePortalContactService contact;

    @GetMapping("/queries")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<List<PortalQuery>>> list(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(contact.list(userId(), clientId)));
    }

    @GetMapping("/queries/{id}")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<PortalQueryDetail>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(contact.get(userId(), id)));
    }

    @PostMapping("/queries")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<PortalQueryDetail>> raise(@Valid @RequestBody RaiseRequest body) {
        return ResponseEntity.ok(ApiResponse.success(contact.raise(userId(), body.clientId(), body.subject(), body.body())));
    }

    @PostMapping("/queries/{id}/messages")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<PortalQueryDetail>> reply(@PathVariable UUID id, @Valid @RequestBody BodyRequest body) {
        return ResponseEntity.ok(ApiResponse.success(contact.reply(userId(), id, body.body())));
    }

    @GetMapping("/document-requests")
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<List<PortalRequest>>> requests(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(contact.requests(userId(), clientId, LocalDate.now(SAST))));
    }

    @PostMapping(value = "/document-requests/{id}/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(PORTAL)
    public ResponseEntity<ApiResponse<PortalRequest>> upload(@PathVariable UUID id, @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(contact.upload(userId(), id, file, LocalDate.now(SAST))));
    }

    private UUID userId() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }
}
