package za.co.handyflow.platform.secureaccess.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider.ReleasedFile;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * How a released file leaves the system. The engine does not trust the owning module to have made the name
 * and type safe: it always sends an attachment, never lets the browser sniff or render it, never caches it,
 * and turns anything a browser could run (HTML, SVG, XML, JavaScript) into an opaque download.
 */
final class SecureDownloads {

    private SecureDownloads() {}

    static ResponseEntity<byte[]> response(ReleasedFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType(file.contentType())))
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeName(file.fileName()), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }

    static String contentType(String stored) {
        final String opaque = "application/octet-stream";
        if (stored == null || stored.isBlank()) return opaque;
        String t = stored.trim().toLowerCase(Locale.ROOT);
        int semi = t.indexOf(';');
        if (semi >= 0) t = t.substring(0, semi).trim();
        if (!t.matches("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")) return opaque;
        if (t.contains("html") || t.contains("svg") || t.contains("xml") || t.contains("javascript")
                || t.contains("ecmascript") || t.equals("text/css")) return opaque;
        return t;
    }

    static String safeName(String name) {
        if (name == null) return "document";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        StringBuilder sb = new StringBuilder();
        for (char c : name.substring(slash + 1).toCharArray()) {
            sb.append((c < 0x20 || c == 0x7f || "\"\\/:*?<>|;".indexOf(c) >= 0) ? '_' : c);
        }
        String r = sb.toString().trim();
        while (r.startsWith(".")) r = r.substring(1);
        if (r.length() > 150) r = r.substring(0, 150);
        return r.isEmpty() ? "document" : r;
    }
}
