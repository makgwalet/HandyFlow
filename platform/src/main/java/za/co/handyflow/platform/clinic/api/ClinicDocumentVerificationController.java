package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService;
import za.co.handyflow.platform.clinic.dto.DocumentVerificationDtos.VerifyRequest;
import za.co.handyflow.platform.clinic.dto.DocumentVerificationDtos.VerifyResponse;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * PUBLIC (no login; see SecurityConfig and RateLimitFilter). Anyone holding a document can check that this clinic issued it
 * (CLINIC-DEC-025). It answers with the document type, number, issue date and issuer only, never a patient or clinical detail.
 * A POST body keeps the code out of URLs and logs.
 */
@RestController
@RequestMapping("/api/v1/public/clinic/document-verification")
@RequiredArgsConstructor
@Tag(name = "Clinic document verification", description = "Public check that a document was issued by the practice")
public class ClinicDocumentVerificationController {

    private final ClinicIssuedDocumentService issued;

    @PostMapping
    @Operation(summary = "Is this verification code on a document the practice issued?")
    public ResponseEntity<ApiResponse<VerifyResponse>> verify(@RequestBody VerifyRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Success", issued.verify(body == null ? null : body.code())));
    }
}
