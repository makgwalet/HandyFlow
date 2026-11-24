package za.co.handyflow.platform.complianceservices.dto;

import jakarta.validation.constraints.Size;

/** {@code exceptionReason} is needed only when the person verifying uploaded the document themselves. */
public record VerifyClientDocumentRequest(@Size(max = 1000) String exceptionReason) {}
