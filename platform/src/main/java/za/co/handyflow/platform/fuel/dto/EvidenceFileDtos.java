package za.co.handyflow.platform.fuel.dto;

import java.util.UUID;

/** Photo and signature capture: what arrives from the upload endpoints and what goes back on download. */
public final class EvidenceFileDtos {
    private EvidenceFileDtos() {}

    public record Upload(String kind, String filename, byte[] bytes, String note, UUID clientRequestId) {}

    public record StoredFile(byte[] bytes, String contentType, String filename) {}
}
