package za.co.handyflow.platform.compliancetender.dto;

import java.util.UUID;

/** One requirement that needs a document, and the document chosen for it (or why none could be). outcome: CHOSEN, NOT_VERIFIED, EXPIRED or MISSING. */
public record SuggestedDocumentResponse(String requirement, String documentType, String outcome, UUID documentId, String message) {}
