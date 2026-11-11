package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.events.domain.model.EventTicketTier;
import za.co.handyflow.platform.events.domain.repository.EventRepository;
import za.co.handyflow.platform.events.domain.repository.EventTicketTierRepository;
import za.co.handyflow.platform.events.dto.ImportReport;
import za.co.handyflow.platform.events.dto.ImportReport.ImportRowResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bulk guest import from CSV.
 * <p>
 * DRY RUN = THE REAL THING, ROLLED BACK. A dry run executes the complete
 * import — duplicate detection (including duplicates inside the file itself),
 * capacity, tier inventory, deadlines — and then marks the transaction
 * rollback-only. So the preview can never disagree with what a real run would
 * do, which a separately-written "validate only" path eventually would. Ticket
 * emails are registered after-commit, so a rolled-back dry run sends nothing.
 * <p>
 * ROW OUTCOMES: IMPORTED, DUPLICATE (already registered / repeated in the
 * file), INVALID (bad data in the row), REJECTED (a business rule refused it —
 * e.g. the event is full). One bad row never blocks the good ones.
 * <p>
 * LIMITS OF DUPLICATE DETECTION: duplicates are matched on email + name, so a
 * row WITHOUT an email can't be matched and will be imported even if the same
 * name already exists.
 * <p>
 * POPIA: a real (non-dry-run) import requires the caller to confirm they have
 * a lawful basis to register these people, and that confirmation is recorded
 * in the audit trail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestImportService {

    static final int MAX_ROWS = 500;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final EventRepository           eventRepo;
    private final EventTicketTierRepository tierRepo;
    private final GuestRegistrationEngine   engine;
    private final EventAuditService         audit;

    @Transactional
    public ImportReport importGuests(TenantId tenantId, UUID eventId, byte[] content,
                                     UUID defaultTierId, boolean sendTickets,
                                     boolean dryRun, boolean consentConfirmed) {
        if (!dryRun && !consentConfirmed) {
            throw new HandyFlowException(
                    "Confirm that you have a lawful basis (POPIA) to register these people before importing",
                    HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");
        }

        // ── Parse & map columns (cheap; done before taking the event lock) ──
        List<List<String>> rows = parseCsv(new String(content, StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new HandyFlowException("The file is empty", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
        }
        Map<String, Integer> col = mapHeader(rows.get(0));
        Integer nameIdx = find(col, "full_name", "name", "fullname", "guest_name");
        if (nameIdx == null) {
            throw new HandyFlowException(
                    "Missing a name column — the header row needs 'full_name' (or 'name')",
                    HttpStatus.BAD_REQUEST, "MISSING_COLUMN");
        }
        Integer emailIdx = find(col, "email", "email_address");
        Integer phoneIdx = find(col, "phone", "mobile", "cell", "phone_number");
        Integer companyIdx = find(col, "company", "organisation", "organization");
        Integer dietIdx = find(col, "dietary_requirements", "dietary", "diet");
        Integer tierIdx = find(col, "tier", "ticket_type", "tier_name");

        int dataRows = 0;
        for (int i = 1; i < rows.size(); i++) if (!isBlankRow(rows.get(i))) dataRows++;
        if (dataRows == 0) {
            throw new HandyFlowException("The file has a header but no guests", HttpStatus.BAD_REQUEST, "NO_DATA_ROWS");
        }
        if (dataRows > MAX_ROWS) {
            throw new HandyFlowException("Too many rows (" + dataRows + ") — the maximum is " + MAX_ROWS
                    + " per import. Split the file and import in batches.",
                    HttpStatus.BAD_REQUEST, "TOO_MANY_ROWS");
        }

        // ── From here on we hold the event lock ──
        Event event = eventRepo.findActiveByIdForUpdate(tenantId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId.toString()));

        Map<String, EventTicketTier> tiersByName = new HashMap<>();
        for (EventTicketTier t : tierRepo.findByEvent(eventId)) tiersByName.put(t.getName().trim().toLowerCase(), t);
        if (defaultTierId != null) {
            tierRepo.findByIdAndEvent(defaultTierId, eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tier", defaultTierId.toString()));
        }

        // ── Process ──
        List<ImportRowResult> results = new ArrayList<>();
        int imported = 0, duplicates = 0, invalid = 0, rejected = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (isBlankRow(r)) continue;
            int rowNum = i + 1;

            String name    = cell(r, nameIdx);
            String email   = cell(r, emailIdx);
            String phone   = cell(r, phoneIdx);
            String company = cell(r, companyIdx);
            String diet    = cell(r, dietIdx);
            String tierCol = cell(r, tierIdx);

            String problem = validate(name, email, phone, company, diet);
            UUID tierId = defaultTierId;
            if (problem == null && tierCol != null) {
                EventTicketTier t = tiersByName.get(tierCol.toLowerCase());
                if (t == null) problem = "Unknown ticket tier '" + tierCol + "'";
                else tierId = t.getId();
            }
            if (problem != null) {
                results.add(new ImportRowResult(rowNum, name, email, "INVALID", problem, null));
                invalid++;
                continue;
            }

            try {
                GuestRegistrationEngine.Placed placed = engine.register(tenantId, event,
                        GuestRegistrationEngine.Command.builder()
                                .fullName(name)
                                .email(email)
                                .phone(phone)
                                .company(company)
                                .dietaryRequirements(diet)
                                .tierId(tierId)
                                .source("IMPORT")
                                .sendTicketEmail(sendTickets)
                                .audit(false)           // one summary line at the end instead of N
                                .build());
                results.add(new ImportRowResult(rowNum, name, email, "IMPORTED", "Registered",
                        dryRun ? null : placed.guest().getTicketNumber()));
                imported++;
            } catch (HandyFlowException e) {
                if ("DUPLICATE_REGISTRATION".equals(e.getErrorCode())) {
                    results.add(new ImportRowResult(rowNum, name, email, "DUPLICATE", e.getMessage(), null));
                    duplicates++;
                } else {
                    results.add(new ImportRowResult(rowNum, name, email, "REJECTED", e.getMessage(), null));
                    rejected++;
                }
            }
        }

        if (dryRun) {
            // Everything above really ran — now discard it. Nothing is saved and,
            // because emails are queued after-commit, nothing is sent.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } else if (imported > 0 || duplicates > 0 || invalid > 0 || rejected > 0) {
            audit.record(tenantId, eventId, "GUESTS_IMPORTED", "GUEST", null,
                    "CSV import: " + imported + " imported, " + duplicates + " duplicate, "
                            + invalid + " invalid, " + rejected + " rejected",
                    List.of("lawful basis confirmed by importer",
                            "ticket emails " + (sendTickets ? "sent" : "not sent")));
        }

        log.info("[Events] Guest import event={} dryRun={} imported={} duplicates={} invalid={} rejected={}",
                event.getEventNumber(), dryRun, imported, duplicates, invalid, rejected);
        return new ImportReport(dryRun, results.size(), imported, duplicates, invalid, rejected, results);
    }

    // ── Row validation ────────────────────────────────────────────────────────

    private String validate(String name, String email, String phone, String company, String diet) {
        if (name == null) return "Name is required";
        if (name.length() > 200) return "Name is longer than 200 characters";
        if (email != null) {
            if (email.length() > 254) return "Email is longer than 254 characters";
            if (!EMAIL.matcher(email).matches()) return "'" + email + "' is not a valid email address";
        }
        if (phone != null && phone.length() > 50) return "Phone is longer than 50 characters";
        if (company != null && company.length() > 200) return "Company is longer than 200 characters";
        if (diet != null && diet.length() > 500) return "Dietary requirements are longer than 500 characters";
        return null;
    }

    // ── CSV handling ──────────────────────────────────────────────────────────

    private static Map<String, Integer> mapHeader(List<String> header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim().toLowerCase().replace(' ', '_').replace('-', '_');
            map.putIfAbsent(key, i);
        }
        return map;
    }

    private static Integer find(Map<String, Integer> col, String... aliases) {
        for (String a : aliases) if (col.containsKey(a)) return col.get(a);
        return null;
    }

    /** Trimmed cell value, or null if the column is absent or the cell is blank. */
    private static String cell(List<String> row, Integer idx) {
        if (idx == null || idx >= row.size()) return null;
        String v = row.get(idx).trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean isBlankRow(List<String> row) {
        for (String c : row) if (!c.trim().isEmpty()) return false;
        return true;
    }

    /**
     * Small RFC 4180 parser: quoted fields, "" escapes, commas/newlines inside
     * quotes, CRLF or LF line ends, optional UTF-8 BOM. The delimiter is
     * auto-detected from the header line — South African Excel locales commonly
     * export semicolon-separated "CSV".
     */
    static List<List<String>> parseCsv(String text) {
        if (text.startsWith("\uFEFF")) text = text.substring(1);

        int firstNewline = text.indexOf('\n');
        String headerLine = firstNewline < 0 ? text : text.substring(0, firstNewline);
        char delimiter = (headerLine.indexOf(';') >= 0 && headerLine.indexOf(',') < 0) ? ';' : ',';

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        if (inQuotes) {
            throw new HandyFlowException("The CSV is malformed: a quoted field is never closed",
                    HttpStatus.BAD_REQUEST, "MALFORMED_CSV");
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }
}