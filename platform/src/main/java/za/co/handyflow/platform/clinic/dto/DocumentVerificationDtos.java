package za.co.handyflow.platform.clinic.dto;

/** The public check that a document is genuine (patch 0193, CLINIC-DEC-025). Authenticity only: never a patient name or any clinical detail. */
public final class DocumentVerificationDtos {
    private DocumentVerificationDtos() {}

    public record VerifyRequest(String code) {}

    /** {@code valid} is false for an unknown code. {@code voided} is true when the practice has withdrawn the document. */
    public record VerifyResponse(boolean valid, boolean voided, String documentType, String documentNumber, String issuedOn, String issuedBy) {
        public static VerifyResponse notFound() { return new VerifyResponse(false, false, null, null, null, null); }
    }
}
