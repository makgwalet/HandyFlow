package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.clinic.application.internal.ClinicLetterheadService;
import za.co.handyflow.platform.clinic.dto.LetterheadDtos.LetterheadRequest;
import za.co.handyflow.platform.clinic.dto.LetterheadDtos.LetterheadResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/letterheads")
@RequiredArgsConstructor
@Tag(name = "Clinic letterheads", description = "Practice and branch letterheads for issued documents")
public class ClinicLetterheadController {

    private final ClinicLetterheadService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "The practice's letterheads (the practice-wide default first)")
    public ResponseEntity<ApiResponse<List<LetterheadResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.list(tenant())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_LETTERHEAD_MANAGE')")
    @Operation(summary = "Add a letterhead. With no branch name it is practice-wide; the first practice-wide one becomes the default")
    public ResponseEntity<ApiResponse<LetterheadResponse>> create(@RequestBody LetterheadRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Letterhead saved", service.create(tenant(), UserContext.getCurrentUserId(), body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLINIC_LETTERHEAD_MANAGE')")
    @Operation(summary = "Change a letterhead (documents already issued keep what was printed on them)")
    public ResponseEntity<ApiResponse<LetterheadResponse>> update(@PathVariable UUID id, @RequestBody LetterheadRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Letterhead saved", service.update(tenant(), id, UserContext.getCurrentUserId(), body)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CLINIC_LETTERHEAD_MANAGE')")
    @Operation(summary = "Take a letterhead out of use")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        service.deactivate(tenant(), id, UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Letterhead removed", null));
    }

    @PutMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CLINIC_LETTERHEAD_MANAGE')")
    @Operation(summary = "Set the logo (PNG or JPEG, up to 300 KB)")
    public ResponseEntity<ApiResponse<Void>> setLogo(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        service.setLogo(tenant(), id, UserContext.getCurrentUserId(), file.getBytes());
        return ResponseEntity.ok(ApiResponse.success("Logo saved", null));
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("hasAuthority('CLINIC_LETTERHEAD_MANAGE')")
    @Operation(summary = "Remove the logo")
    public ResponseEntity<ApiResponse<Void>> removeLogo(@PathVariable UUID id) {
        service.removeLogo(tenant(), id, UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Logo removed", null));
    }

    @GetMapping("/{id}/logo")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "The logo picture")
    public ResponseEntity<byte[]> logo(@PathVariable UUID id) {
        var logo = service.logo(tenant(), id).orElseThrow(() -> new ResourceNotFoundException("Logo", id.toString()));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .contentType(MediaType.parseMediaType(logo.type())).contentLength(logo.bytes().length).body(logo.bytes());
    }

    private static UUID tenant() {
        return TenantContext.getTenantIdAsObject().getValue();
    }
}
