package za.co.handyflow.platform.accountant.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record UploadFicaDocumentRequest(
        @NotBlank String docType,   // ID_COPY, PROOF_OF_ADDRESS, BENEFICIAL_OWNERSHIP, COMPANY_DOCUMENTS, TRUST_DEED, OTHER
        @NotBlank String fileName,
        String contentType,
        long fileSizeBytes,
        @NotBlank String fileContentBase64,
        LocalDate expiryDate
) {
}