package za.co.handyflow.platform.projects.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import za.co.handyflow.platform.projects.application.internal.DocumentService;

import java.nio.charset.StandardCharsets;

/**
 * How a stored project file leaves the system — one place, used by the staff download and the
 * client-portal download so they cannot drift apart.
 *
 * Always an attachment (never rendered in the browser), never sniffed, never cached. The file name
 * and content type were already made safe by DocumentRules before they got here.
 */
final class DocumentDownloads {

    private DocumentDownloads() {}

    static ResponseEntity<byte[]> response(DocumentService.FileDownload file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Robots-Tag", "noindex, nofollow")
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}
