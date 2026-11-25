package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ImportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/**
 * Bringing in clients and registrations from a CSV file. A preview checks every row and changes nothing. A commit, by an administrator, creates only the rows
 * that still pass. Nothing existing is overwritten: a match is set aside as a duplicate. Registrations that are already past their expiry date come in as expired.
 */
@Service
@RequiredArgsConstructor
public class ComplianceImportService {

    static final int VIEW_ROWS = 500;

    private final ComplianceImportBatchRepository batches;
    private final ComplianceImportRowRepository rows;
    private final ComplianceClientRepository clients;
    private final ClientComplianceRegistrationRepository registrations;

    @Transactional
    public BatchDetail preview(TenantId tenantId, String kind, String fileName, byte[] bytes, UUID by) {
        if (!ImportRules.CLIENTS.equals(kind) && !ImportRules.REGISTRATIONS.equals(kind)) throw bad("Choose clients or registrations");
        if (bytes == null || bytes.length == 0) throw bad("The file is empty");
        if (bytes.length > ImportRules.MAX_BYTES) throw bad("The file is larger than " + ImportRules.MAX_BYTES / 1_000_000 + " MB");
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) throw bad("Save the spreadsheet as a CSV file and upload that");
        List<List<String>> parsed = guard(() -> ImportRules.parseCsv(new String(bytes, StandardCharsets.UTF_8)));
        if (parsed.size() < 2) throw bad("The file has a header row but no data rows");
        if (parsed.size() - 1 > ImportRules.MAX_ROWS) throw bad("The file has more than " + ImportRules.MAX_ROWS + " rows; split it into smaller files");
        int[] pos = guard(() -> ImportRules.mapHeader(kind, parsed.get(0)));

        Lookup lookup = new Lookup(tenantId, kind);
        List<Staged> staged = new ArrayList<>();
        int ok = 0, dup = 0, rej = 0;
        for (int i = 1; i < parsed.size(); i++) {
            List<String> f = ImportRules.arrange(pos, parsed.get(i));
            Verdict v = lookup.judge(kind, f, true);
            if (ImportRules.OK.equals(v.status)) ok++; else if (ImportRules.DUPLICATE.equals(v.status)) dup++; else rej++;
            staged.add(new Staged(i + 1, v.fields, v.status, v.message));
        }
        ComplianceImportBatch b = batches.save(ComplianceImportBatch.preview(tenantId, kind, fileName, ok, dup, rej, by));
        for (Staged s : staged) rows.save(ComplianceImportRow.of(tenantId, b.getId(), s.rowNumber, s.fields, s.status, s.message));
        return detail(tenantId, b);
    }

    @Transactional
    public BatchDetail commit(TenantId tenantId, UUID id, UUID by, LocalDate today) {
        ComplianceImportBatch b = find(tenantId, id);
        if (!"PREVIEWED".equals(b.getStatus())) throw new HandyFlowException("This import has already been " + b.getStatus().toLowerCase(), HttpStatus.CONFLICT, "IMPORT_STATE");
        Lookup lookup = new Lookup(tenantId, b.getKind());
        int imported = 0, dup = 0, rej = 0;
        for (ComplianceImportRow r : rows.findForBatch(tenantId, id)) {
            if (!ImportRules.OK.equals(r.getStatus())) continue;
            Verdict v = lookup.judge(b.getKind(), r.fieldList(), false);       // the world may have changed since the preview
            if (!ImportRules.OK.equals(v.status)) {
                r.setAside(v.status, v.message);
                if (ImportRules.DUPLICATE.equals(v.status)) dup++; else rej++;
                continue;
            }
            UUID target = ImportRules.CLIENTS.equals(b.getKind()) ? createClient(tenantId, v.fields, by) : createRegistration(tenantId, v, by, today);
            r.imported(target);
            imported++;
        }
        int fi = imported, fd = dup, fr = rej;
        guard(() -> { b.commit(by, fi, fd, fr); return null; });
        return detail(tenantId, batches.save(b));
    }

    @Transactional
    public BatchDetail discard(TenantId tenantId, UUID id, String note, UUID by) {
        ComplianceImportBatch b = find(tenantId, id);
        guard(() -> { b.discard(by, note); return null; });
        return detail(tenantId, batches.save(b));
    }

    @Transactional(readOnly = true)
    public List<BatchView> list(TenantId tenantId) {
        List<BatchView> out = new ArrayList<>();
        for (ComplianceImportBatch b : batches.findAllForTenant(tenantId)) out.add(view(b));
        return out;
    }

    @Transactional(readOnly = true)
    public BatchDetail get(TenantId tenantId, UUID id) { return detail(tenantId, find(tenantId, id)); }

    // ---- internals ----

    private UUID createClient(TenantId tenantId, List<String> f, UUID by) {
        ComplianceClient c = ComplianceClient.create(tenantId, f.get(0), null, blankNull(f.get(1)), blankNull(f.get(2)), blankNull(f.get(3)), by);
        return clients.save(c).getId();
    }

    private UUID createRegistration(TenantId tenantId, Verdict v, UUID by, LocalDate today) {
        List<String> f = v.fields;
        LocalDate issued = ImportRules.date(f.get(4)), expiry = ImportRules.date(f.get(5));
        ClientComplianceRegistration r = ClientComplianceRegistration.create(tenantId, v.clientId, f.get(1).toUpperCase(), f.get(2), blankNull(f.get(3)), issued, expiry, blankNull(f.get(6)), by);
        if (expiry != null && expiry.isBefore(today)) r.markExpired();
        return registrations.save(r).getId();
    }

    private static String blankNull(String s) { return s == null || s.isBlank() ? null : s; }

    private BatchDetail detail(TenantId tenantId, ComplianceImportBatch b) {
        List<ComplianceImportRow> all = rows.findForBatch(tenantId, b.getId());
        List<RowView> shown = new ArrayList<>();
        // problems first so they are not lost behind a long list of rows that are fine
        all.stream().filter(r -> !ImportRules.OK.equals(r.getStatus()) && !ImportRules.IMPORTED.equals(r.getStatus())).limit(VIEW_ROWS).forEach(r -> shown.add(rowView(r)));
        all.stream().filter(r -> ImportRules.OK.equals(r.getStatus()) || ImportRules.IMPORTED.equals(r.getStatus())).limit(Math.max(0, VIEW_ROWS - shown.size())).forEach(r -> shown.add(rowView(r)));
        return new BatchDetail(view(b), ImportRules.columns(b.getKind()), shown, all.size() > shown.size());
    }

    private static RowView rowView(ComplianceImportRow r) { return new RowView(r.getRowNumber(), r.fieldList(), r.getStatus(), r.getMessage(), r.getTargetId()); }

    private static BatchView view(ComplianceImportBatch b) {
        return new BatchView(b.getId(), b.getKind(), b.getFileName(), b.getStatus(), b.getTotalRows(), b.getOkRows(), b.getDuplicateRows(), b.getRejectedRows(), b.getImportedRows(),
                b.getCreatedBy(), b.getCreatedAt(), b.getCommittedBy(), b.getCommittedAt(), b.getDiscardNote());
    }

    private ComplianceImportBatch find(TenantId tenantId, UUID id) {
        return batches.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("ComplianceImportBatch", id.toString()));
    }

    private static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "IMPORT_INVALID"); }

    private static <T> T guard(Supplier<T> work) {
        try { return work.get(); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "IMPORT_STATE"); }
        catch (IllegalArgumentException e) { throw bad(e.getMessage()); }
    }

    private record Staged(int rowNumber, List<String> fields, String status, String message) {}
    private record Verdict(String status, String message, List<String> fields, UUID clientId) {}

    /** What already exists, plus what earlier rows of the same file will create, so repeats inside one file are caught too. */
    private final class Lookup {
        private final Map<String, List<ComplianceClient>> byName = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private final TenantId tenantId;
        private final Map<UUID, Set<String>> existingRegs = new HashMap<>();

        Lookup(TenantId tenantId, String kind) {
            this.tenantId = tenantId;
            for (ComplianceClient c : clients.findAll(tenantId, null, Pageable.unpaged()).getContent()) byName.computeIfAbsent(ImportRules.nameKey(c.getName()), k -> new ArrayList<>()).add(c);
        }

        Verdict judge(String kind, List<String> f, boolean preview) {
            if (ImportRules.CLIENTS.equals(kind)) {
                String p = ImportRules.clientProblem(f);
                if (p != null) return new Verdict(ImportRules.REJECTED, p, f, null);
                String key = ImportRules.nameKey(f.get(0));
                if (byName.containsKey(key)) return new Verdict(ImportRules.DUPLICATE, "A client with this name already exists", f, null);
                if (!seen.add(key)) return new Verdict(ImportRules.DUPLICATE, "This name appears earlier in the file", f, null);
                return new Verdict(ImportRules.OK, null, f, null);
            }
            String p = ImportRules.registrationProblem(f);
            if (p != null) return new Verdict(ImportRules.REJECTED, p, f, null);
            List<ComplianceClient> found = byName.getOrDefault(ImportRules.nameKey(f.get(0)), List.of());
            if (found.isEmpty()) return new Verdict(ImportRules.REJECTED, "No client has this name. Import the client first, or correct the name", f, null);
            if (found.size() > 1) return new Verdict(ImportRules.REJECTED, "More than one client has this name", f, null);
            ComplianceClient c = found.get(0);
            if (!"ACTIVE".equals(c.getStatus())) return new Verdict(ImportRules.REJECTED, "This client is inactive", f, null);
            List<String> iso = ImportRules.isoDates(f);
            String key = ImportRules.registrationKey(ImportRules.nameKey(f.get(0)), iso);
            Set<String> have = existingRegs.computeIfAbsent(c.getId(), id -> {
                Set<String> s = new HashSet<>();
                for (ClientComplianceRegistration r : registrations.findByClient(tenantId, id))
                    s.add(ImportRules.registrationKey(ImportRules.nameKey(f.get(0)), java.util.Arrays.asList("", r.getAuthority(), r.getRegistrationType(), r.getRegistrationNumber() == null ? "" : r.getRegistrationNumber())));
                return s;
            });
            if (have.contains(key)) return new Verdict(ImportRules.DUPLICATE, "This registration already exists for the client", iso, c.getId());
            if (!seen.add(key)) return new Verdict(ImportRules.DUPLICATE, "This registration appears earlier in the file", iso, c.getId());
            return new Verdict(ImportRules.OK, null, iso, c.getId());
        }
    }
}
