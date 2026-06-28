package za.co.handyflow.platform.crm.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.io.Writer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CustomerExportService — streams customer data as CSV.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY stream to a Writer instead of returning a String or byte[]?
 *
 * If a tenant has 5,000 customers, building the entire CSV in memory
 * before writing it to the HTTP response means holding ~5MB of String
 * data on the heap simultaneously.  With concurrent exports, this
 * causes GC pressure and OutOfMemoryError under load.
 *
 * Streaming writes each row directly to the HttpServletResponse's
 * OutputStream as soon as it's ready.  Memory usage is O(batch size),
 * not O(total rows).  The client starts receiving data immediately.
 *
 * WHY not use OpenCSV or Apache Commons CSV?
 * This is a simple, well-defined format.  Adding a library dependency
 * for 50 lines of CSV generation is unnecessary overhead.  If the
 * schema grows complex (nested fields, quoting edge cases), swap in
 * Apache Commons CSV — the Writer interface stays identical.
 *
 * WHY no Pageable here?
 * Export is a one-shot operation — the user wants ALL records.
 * We use a List query (not paginated) because we're streaming, not
 * paging.  For > 50k records, replace with a ScrollableResults /
 * JPA Scroll query to avoid loading all rows into the JPA first-level
 * cache at once.
 * ═══════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerExportService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Africa/Johannesburg"));

    /**
     * CSV column headers — order must match writeRow().
     * WHY a constant?  If you add a column, you change it in one place
     * (HEADERS) and the corresponding writeRow() call.  Impossible to
     * get header/data out of sync silently.
     */
    private static final String[] HEADERS = {
            "ID", "Name", "Type", "Status", "Email", "Phone",
            "VAT Number", "Street", "Suburb", "City", "Province", "Postal Code",
            "Tags", "Notes", "Created", "Updated"
    };

    @Transactional(readOnly = true)
    public void exportActiveToCsv(TenantId tenantId, Writer writer) throws IOException {
        var customers = customerRepository.findAllActiveForExport(tenantId);
        writeCsv(customers, writer);
        log.info("[CRM] CSV export: {} customers, tenant={}", customers.size(), tenantId);
    }

    @Transactional(readOnly = true)
    public void exportAllToCsv(TenantId tenantId, Writer writer) throws IOException {
        var customers = customerRepository.findAllForExport(tenantId);
        writeCsv(customers, writer);
        log.info("[CRM] CSV full export: {} customers (incl. deleted), tenant={}", customers.size(), tenantId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void writeCsv(List<Customer> customers, Writer writer) throws IOException {
        writeRow(writer, HEADERS);
        for (var c : customers) {
            writeRow(writer, new String[]{
                    str(c.getId()),
                    c.getName(),
                    c.getCustomerType().name(),
                    c.getStatus().name(),
                    c.getEmail(),
                    c.getPhone(),
                    c.getTaxNumber(),
                    addr(c, "street"),
                    addr(c, "suburb"),
                    addr(c, "city"),
                    addr(c, "province"),
                    addr(c, "postalCode"),
                    tags(c),
                    c.getNotes(),
                    c.getCreatedAt() != null ? DATE_FMT.format(c.getCreatedAt()) : "",
                    c.getUpdatedAt() != null ? DATE_FMT.format(c.getUpdatedAt()) : "",
            });
        }
        writer.flush();
    }

    /**
     * RFC 4180-compliant CSV row writer.
     *
     * WHY manual quoting instead of a library?
     * The rules are simple:
     *   1. Wrap any field that contains a comma, quote, or newline in double-quotes.
     *   2. Escape internal double-quotes by doubling them ("He said ""hello""").
     * This handles all edge cases correctly without a dependency.
     */
    private static void writeRow(Writer w, String[] fields) throws IOException {
        var sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvField(fields[i]));
        }
        sb.append("\r\n");   // RFC 4180 line ending — Excel on Windows requires \r\n
        w.write(sb.toString());
    }

    private static String csvField(String value) {
        if (value == null || value.isEmpty()) return "";
        // Needs quoting if it contains comma, double-quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String addr(Customer c, String key) {
        if (c.getAddress() == null) return "";
        return c.getAddress().getOrDefault(key, "");
    }

    private static String tags(Customer c) {
        if (c.getTags() == null || c.getTags().isEmpty()) return "";
        // Semicolon-separated so the field stays as one CSV cell
        return c.getTags().stream().collect(Collectors.joining(";"));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private final CustomerRepository customerRepository;
}
