package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.model.ImportJob;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.domain.repository.ImportJobRepository;
import za.co.handyflow.platform.crm.dto.ImportJobResult;
import za.co.handyflow.platform.crm.dto.ImportJobResult.RowError;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * CustomerImportService — parses CSV files and imports customers in bulk.
 *
 * DUPLICATE DETECTION — three levels applied in order:
 *   Level 1: exact email match          O(1) HashSet lookup
 *   Level 2: normalised E.164 phone     O(1) HashSet lookup
 *   Level 3: Jaro-Winkler fuzzy name    O(N) scan, threshold 0.92
 *
 * WHY inline Jaro-Winkler and not commons-text?
 * Adding commons-text for a single algorithm brings transitive dependencies
 * and additional CVE surface.  The 40-line inline implementation is
 * identical in correctness and has zero extra attack surface.
 *
 * WHY @Async?
 * 500-row imports with dedup checks take seconds.  Return 202 Accepted
 * immediately with a jobId.  Client polls GET /import/{jobId} for results.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerImportService {

    private static final double NAME_SIMILARITY_THRESHOLD = 0.92;
    private static final int    MAX_FILE_SIZE_BYTES       = 5 * 1024 * 1024;
    private static final int    MAX_ROWS                  = 2_000;

    private final CustomerRepository  customerRepository;
    private final ImportJobRepository importJobRepository;
    private final EmailService        emailService;
    private final JdbcTemplate        jdbc;

    // ── Public API ────────────────────────────────────────────────────────────

    @Transactional
    public UUID startImport(TenantId tenantId, MultipartFile file, UUID initiatedBy) {
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum size of 5 MB");
        }
        var job = ImportJob.create(tenantId, file.getOriginalFilename(), initiatedBy);
        importJobRepository.save(job);
        processAsync(tenantId, file, job.getId(), initiatedBy);
        return job.getId();
    }

    @Transactional(readOnly = true)
    public ImportJobResult getJobStatus(TenantId tenantId, UUID jobId) {
        return importJobRepository.findByTenantAndId(tenantId, jobId)
                .map(this::toResult)
                .orElseThrow(() -> new ResourceNotFoundException("ImportJob", jobId.toString()));
    }

    // ── Async processing ──────────────────────────────────────────────────────

    @Async("crmTaskExecutor")
    public void processAsync(TenantId tenantId, MultipartFile file, UUID jobId, UUID initiatedBy) {
        var job = importJobRepository.findById(jobId).orElseThrow();
        job.markProcessing();
        importJobRepository.save(job);

        var errors  = new ArrayList<RowError>();
        int created = 0;
        int skipped = 0;
        int rowNum  = 0;

        try (var reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            var existing = customerRepository.findAllActiveForExport(tenantId);
            Set<String> emailSet = buildEmailSet(existing);
            Set<String> phoneSet = buildPhoneSet(existing);
            var nameList         = buildNameList(existing);

            String   line;
            boolean  firstLine = true;
            String[] headers   = null;

            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (rowNum > MAX_ROWS + 1) {
                    errors.add(new RowError(rowNum, "", "File exceeds " + MAX_ROWS + " row limit"));
                    break;
                }

                if (firstLine) {
                    line      = stripBom(line);
                    headers   = parseCsvRow(line);
                    firstLine = false;
                    continue;
                }

                var fields  = parseCsvRow(line);
                var rowData = mapRow(headers, fields);

                var name = rowData.getOrDefault("name", "").strip();
                if (name.isBlank()) {
                    errors.add(new RowError(rowNum, name, "Name is required"));
                    skipped++;
                    continue;
                }

                var email = normaliseEmail(rowData.get("email"));
                var phone = normalisePhone(rowData.get("phone"));

                // Level 1: exact email
                if (email != null && emailSet.contains(email)) {
                    errors.add(new RowError(rowNum, name, "Duplicate email: " + email));
                    skipped++;
                    continue;
                }

                // Level 2: normalised phone
                if (phone != null && phoneSet.contains(phone)) {
                    errors.add(new RowError(rowNum, name, "Duplicate phone: " + phone));
                    skipped++;
                    continue;
                }

                // Level 3: fuzzy name
                var similar = findSimilarName(name, nameList);
                if (similar != null) {
                    errors.add(new RowError(rowNum, name,
                            "Potential duplicate of '" + similar + "' (name similarity > 92%)"));
                    skipped++;
                    continue;
                }

                try {
                    var customer = Customer.create(
                            tenantId, name, email, phone,
                            buildAddress(rowData),
                            rowData.get("taxnumber"),
                            rowData.get("notes"),
                            parseType(rowData.get("customertype")),
                            initiatedBy
                    );
                    customerRepository.save(customer);

                    // Update in-memory sets so within-file duplicates are caught
                    if (email != null) emailSet.add(email);
                    if (phone != null) phoneSet.add(phone);
                    nameList.add(name.toLowerCase());
                    created++;

                } catch (Exception e) {
                    log.warn("[CRM] Import row {} failed: {}", rowNum, e.getMessage());
                    errors.add(new RowError(rowNum, name, e.getMessage()));
                    skipped++;
                }
            }

            job.markDone(rowNum - 1, created, skipped, errors);

        } catch (Exception e) {
            log.error("[CRM] Import job {} failed: {}", jobId, e.getMessage(), e);
            job.markFailed(e.getMessage());
        }

        importJobRepository.save(job);
        log.info("[CRM] Import job {} complete: created={} skipped={} errors={}",
                jobId, created, skipped, errors.size());

        // FIX: "no import-completion notification" gap — ImportModal.tsx's
        // synchronous-poll workaround (its own comment documents this
        // explicitly) only works for small files; there was no fallback
        // for someone who kicked off a larger import and closed the tab
        // before it finished. initiatedBy is always a platform user's UUID
        // here (startImport requires it), so this always has someone to
        // notify — never a system/import-triggered job with no owner.
        sendImportCompletionEmail(job);
    }

    private void sendImportCompletionEmail(ImportJob job) {
        try {
            if (job.getCreatedBy() == null) return;

            // Same jdbc.queryForObject pattern already confirmed working
            // elsewhere in this codebase (ClinicAppointmentReminderService)
            // for resolving a user's contact details directly, rather than
            // guessing at an unseen UserRepository/IdentityFacade.
            String email;
            String firstName;
            try {
                email = jdbc.queryForObject(
                        "SELECT email FROM users WHERE id = ?", String.class, job.getCreatedBy());
                firstName = jdbc.queryForObject(
                        "SELECT first_name FROM users WHERE id = ?", String.class, job.getCreatedBy());
            } catch (Exception e) {
                log.info("[CRM] Could not resolve email for import job={} initiatedBy={} — not notified",
                        job.getId(), job.getCreatedBy());
                return;
            }
            if (email == null || email.isBlank()) return;

            boolean failed = job.getStatus() == ImportJob.Status.FAILED;
            String subject = failed
                    ? "Customer import failed" + (job.getFilename() != null ? ": " + job.getFilename() : "")
                    : "Customer import complete" + (job.getFilename() != null ? ": " + job.getFilename() : "");

            String greetingName = firstName != null ? firstName : "there";
            String html;
            if (failed) {
                String reason = (job.getRowErrors() != null && !job.getRowErrors().isEmpty())
                        ? job.getRowErrors().get(0).reason() : "Unknown error";
                html = "<p>Dear " + greetingName + ",</p>"
                        + "<p>Your customer import" + (job.getFilename() != null ? " of <b>" + job.getFilename() + "</b>" : "")
                        + " failed to complete.</p>"
                        + "<p><b>Reason:</b> " + escapeHtml(reason) + "</p>"
                        + "<p>Please check the file and try again.</p>";
            } else {
                html = "<p>Dear " + greetingName + ",</p>"
                        + "<p>Your customer import" + (job.getFilename() != null ? " of <b>" + job.getFilename() + "</b>" : "")
                        + " has finished processing.</p>"
                        + "<p><b>" + job.getCreatedCount() + "</b> customer" + (job.getCreatedCount() == 1 ? "" : "s") + " created<br/>"
                        + "<b>" + job.getSkippedCount() + "</b> row" + (job.getSkippedCount() == 1 ? "" : "s") + " skipped<br/>"
                        + "<b>" + job.getTotalRows() + "</b> total rows processed</p>"
                        + (job.getSkippedCount() > 0
                        ? "<p>Some rows were skipped — usually duplicates or missing required fields. "
                        + "Open the import history in the CRM to see the full per-row breakdown.</p>"
                        : "");
            }

            emailService.send(email, subject, html);
            log.info("[CRM] Sent import completion email job={} to={}", job.getId(), email);
        } catch (Exception e) {
            // Same principle as every other notification hookup in this
            // codebase: the import itself already completed and saved
            // successfully above — an email failure must never affect that.
            log.warn("[CRM] Import completion email not sent for job={}: {}", job.getId(), e.getMessage());
        }
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Dedup helpers ─────────────────────────────────────────────────────────

    /**
     * WHY HashSet<String> and not ConcurrentHashMap.newKeySet()?
     *
     * ConcurrentHashMap.newKeySet() returns KeySetView<Object, Boolean>.
     * The Java compiler infers var as that raw type, which is then
     * incompatible with the Set<String> return type — causing the two
     * "incompatible types" errors seen in the build.
     *
     * HashSet<String> is explicitly typed, thread-safe enough for our
     * use (single async thread writes, no concurrent readers during write),
     * and unambiguously assignable to Set<String>.
     */
    private Set<String> buildEmailSet(List<Customer> existing) {
        Set<String> set = new HashSet<>();
        existing.stream()
                .filter(c -> c.getEmail() != null)
                .forEach(c -> set.add(c.getEmail()));
        return set;
    }

    private Set<String> buildPhoneSet(List<Customer> existing) {
        Set<String> set = new HashSet<>();
        existing.stream()
                .filter(c -> c.getPhone() != null)
                .map(c -> normalisePhone(c.getPhone()))
                .filter(Objects::nonNull)
                .forEach(set::add);
        return set;
    }

    private List<String> buildNameList(List<Customer> existing) {
        var list = new ArrayList<String>();
        existing.forEach(c -> list.add(c.getName().toLowerCase()));
        return list;
    }

    private String findSimilarName(String name, List<String> existingNames) {
        var lower = name.toLowerCase();
        for (var existing : existingNames) {
            if (jaroWinkler(lower, existing) >= NAME_SIMILARITY_THRESHOLD) {
                return existing;
            }
        }
        return null;
    }

    // ── Jaro-Winkler similarity (self-contained, no external dependency) ──────

    /**
     * Returns a score between 0.0 (no match) and 1.0 (identical).
     * Gives extra weight to matching prefix characters, making it well-suited
     * to company names that share a trading name but differ in legal suffix
     * ("Tau Mining Ltd" vs "Tau Mining (Pty) Ltd").
     *
     * Reference: Winkler (1990), "String Comparator Metrics and Enhanced
     * Decision Rules in the Fellegi-Sunter Model of Record Linkage."
     */
    static double jaroWinkler(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length(), len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0.0;

        int matchDist = Math.max(len1, len2) / 2 - 1;
        boolean[] s1m = new boolean[len1], s2m = new boolean[len2];
        int matches = 0, transpositions = 0;

        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDist);
            int end   = Math.min(i + matchDist + 1, len2);
            for (int j = start; j < end; j++) {
                if (!s2m[j] && s1.charAt(i) == s2.charAt(j)) {
                    s1m[i] = true; s2m[j] = true; matches++; break;
                }
            }
        }
        if (matches == 0) return 0.0;

        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (s1m[i]) {
                while (!s2m[k]) k++;
                if (s1.charAt(i) != s2.charAt(k)) transpositions++;
                k++;
            }
        }

        double jaro = (matches / (double) len1
                + matches / (double) len2
                + (matches - transpositions / 2.0) / matches) / 3.0;

        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(len1, len2)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    // ── Phone / email normalisation ───────────────────────────────────────────

    static String normalisePhone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var digits = raw.replaceAll("[^\\d+]", "");
        if (digits.startsWith("+27")) return digits;
        if (digits.startsWith("0") && digits.length() >= 10)
            return "+27" + digits.substring(1);
        return null;
    }

    static String normaliseEmail(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.strip().toLowerCase();
    }

    // ── CSV parsing (RFC 4180) ────────────────────────────────────────────────

    static String[] parseCsvRow(String line) {
        var fields  = new ArrayList<String>();
        var sb      = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQ) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else inQ = false;
                } else sb.append(c);
            } else {
                if      (c == '"') inQ = true;
                else if (c == ',') { fields.add(sb.toString().strip()); sb.setLength(0); }
                else               sb.append(c);
            }
        }
        fields.add(sb.toString().strip());
        return fields.toArray(String[]::new);
    }

    private static Map<String, String> mapRow(String[] headers, String[] fields) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < headers.length && i < fields.length; i++) {
            map.put(headers[i].strip().toLowerCase(), fields[i]);
        }
        return map;
    }

    private static Map<String, String> buildAddress(Map<String, String> row) {
        var addr = new LinkedHashMap<String, String>();
        addIfPresent(addr, "street",     row.get("street"));
        addIfPresent(addr, "suburb",     row.get("suburb"));
        addIfPresent(addr, "city",       row.get("city"));
        addIfPresent(addr, "province",   row.get("province"));
        addIfPresent(addr, "postalCode", row.get("postalcode"));
        return addr.isEmpty() ? null : addr;
    }

    private static void addIfPresent(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v.strip());
    }

    private static CustomerType parseType(String raw) {
        return "LEAD".equalsIgnoreCase(raw) ? CustomerType.LEAD : CustomerType.CUSTOMER;
    }

    private static String stripBom(String line) {
        return line.startsWith("\uFEFF") ? line.substring(1) : line;
    }

    private ImportJobResult toResult(ImportJob job) {
        return new ImportJobResult(
                job.getId(), job.getStatus().name(), job.getFilename(),
                job.getTotalRows(), job.getCreatedCount(),
                job.getSkippedCount(), job.getErrorCount(),
                job.getRowErrors(), job.getStartedAt(), job.getCompletedAt()
        );
    }
}