package za.co.handyflow.platform.capture.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads text out of photos and scanned PDFs with Tesseract, run on this server so the document
 * never leaves it. Off unless {@code capture.ocr.enabled=true} and Tesseract (and pdftoppm for
 * scanned PDFs) are installed. Arguments are passed as a list, never through a shell.
 */
@Component
public class OcrEngine {
    private static final Logger log = LoggerFactory.getLogger(OcrEngine.class);
    private static final Pattern LANGS = Pattern.compile("^[a-z]{3}(_[a-z]+)?(\\+[a-z]{3}(_[a-z]+)?)*$");
    private static final int PAGES = 2;

    private final boolean enabled;
    private final String tesseract;
    private final String pdftoppm;
    private final String languages;
    private final long timeoutSeconds;

    public OcrEngine(@Value("${capture.ocr.enabled:false}") boolean enabled,
                     @Value("${capture.ocr.tesseract-command:tesseract}") String tesseract,
                     @Value("${capture.ocr.pdftoppm-command:pdftoppm}") String pdftoppm,
                     @Value("${capture.ocr.languages:eng}") String languages,
                     @Value("${capture.ocr.timeout-seconds:40}") long timeoutSeconds) {
        this.enabled = enabled;
        this.tesseract = tesseract;
        this.pdftoppm = pdftoppm;
        this.languages = validLanguages(languages);
        this.timeoutSeconds = Math.max(5, Math.min(timeoutSeconds, 120));
    }

    public boolean enabled() {
        return enabled;
    }

    static String validLanguages(String s) {
        return s != null && LANGS.matcher(s.trim()).matches() ? s.trim() : "eng";
    }

    /** Text from an image, or from the first pages of a scanned PDF. Empty on any failure. */
    public String read(byte[] file, boolean pdf) {
        if (!enabled) return "";
        Path dir = null;
        try {
            dir = Files.createTempDirectory("hf-ocr-");
            List<Path> images = new ArrayList<>();
            if (pdf) {
                Path in = dir.resolve("in.pdf");
                Files.write(in, file);
                run(List.of(pdftoppm, "-r", "200", "-png", "-f", "1", "-l", String.valueOf(PAGES), in.toString(), dir.resolve("page").toString()), null);
                try (Stream<Path> s = Files.list(dir)) {
                    s.filter(p -> p.getFileName().toString().startsWith("page") && p.toString().endsWith(".png")).sorted().forEach(images::add);
                }
            } else {
                Path in = dir.resolve("in.img");
                Files.write(in, file);
                images.add(in);
            }
            StringBuilder out = new StringBuilder();
            for (Path img : images) {
                run(List.of(tesseract, img.toString(), "stdout", "-l", languages, "--psm", "6"), out);
                out.append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            log.warn("OCR failed: {}", e.toString());
            return "";
        } finally {
            if (dir != null) {
                try (Stream<Path> s = Files.walk(dir)) {
                    s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                } catch (IOException ignored) { }
            }
        }
    }

    private void run(List<String> cmd, StringBuilder into) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        p.getOutputStream().close();
        Thread drain = new Thread(() -> { try { p.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream()); } catch (IOException ignored) { } });
        drain.setDaemon(true);
        drain.start();
        byte[] bytes = p.getInputStream().readAllBytes();
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("OCR timed out");
        }
        if (p.exitValue() != 0) throw new IOException("OCR command exited with " + p.exitValue());
        if (into != null) into.append(new String(bytes, StandardCharsets.UTF_8));
    }
}
