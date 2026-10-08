package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientDocumentService;
import za.co.handyflow.platform.clinic.dto.DocumentDtos.RegisterItem;
import za.co.handyflow.platform.clinic.dto.DocumentDtos.RegisterResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/** The documents register of a patient. */
@RestController
@RequestMapping("/api/v1/clinic/patients/{patientId}/documents")
@RequiredArgsConstructor
@Tag(name = "Clinic patient documents", description = "Documents register and uploads")
public class ClinicPatientDocumentController {

    private final ClinicPatientDocumentService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "The documents register: uploaded and issued files, and the documents produced from the record")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.register(TenantContext.getTenantIdAsObject(), patientId)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_CREATE')")
    @Operation(summary = "Upload an outside document (PDF, JPEG or PNG, up to 10 MB) with its type and date")
    public ResponseEntity<ApiResponse<RegisterItem>> upload(
            @PathVariable UUID patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam String type,
            @RequestParam String title,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) UUID consultationId) {
        return ResponseEntity.status(201).body(ApiResponse.success("Document uploaded",
                service.upload(TenantContext.getTenantIdAsObject(), patientId, file, type, title, date, notes, consultationId)));
    }

    @GetMapping("/{docId}/file")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_DOWNLOAD')")
    @Operation(summary = "Download a stored document")
    public ResponseEntity<byte[]> download(@PathVariable UUID patientId, @PathVariable UUID docId) {
        var f = service.download(TenantContext.getTenantIdAsObject(), patientId, docId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.fileName() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(f.content());
    }

    @DeleteMapping("/{docId}")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_VOID')")
    @Operation(summary = "Take a document out of the list, with a reason (the file is kept)")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID patientId, @PathVariable UUID docId, @RequestParam String reason) {
        service.remove(TenantContext.getTenantIdAsObject(), patientId, docId, reason);
        return ResponseEntity.ok(ApiResponse.success("Document removed", null));
    }
}
