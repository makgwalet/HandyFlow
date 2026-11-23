package za.co.handyflow.platform.agriculture.api;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;

import java.nio.charset.StandardCharsets;

/**
 * Builds the download response for Agriculture evidence. The stored file name was copied into the
 * header as it came, so a name containing a quote or a line break could break the header. Spring's
 * ContentDisposition quotes and encodes it, and the type falls back to a plain download if the
 * stored one is not valid.
 */
final class AgEvidenceResponses {

    private AgEvidenceResponses() {}

    static ResponseEntity<byte[]> download(EvidenceFacade.DownloadedEvidence file) {
        MediaType type;
        try {
            type = MediaType.parseMediaType(file.contentType());
        } catch (RuntimeException e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        String name = file.fileName() == null ? "file" : file.fileName().replaceAll("[\\r\\n\\u0000]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(type)
                .body(file.content());
    }
}
