package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.application.internal.CustomReportRules.Def;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.Audience;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.ExportFile;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportService.Format;
import za.co.handyflow.platform.legalcompliance.domain.model.CustomReport;
import za.co.handyflow.platform.legalcompliance.domain.repository.CustomReportRepository;
import za.co.handyflow.platform.legalcompliance.dto.CustomReportDtos.SaveRequest;
import za.co.handyflow.platform.legalcompliance.dto.CustomReportDtos.View;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportInfo;
import za.co.handyflow.platform.legalcompliance.dto.ReportDtos.ReportResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;

/**
 * Saved views over the standard reports. A view is built from the standard report's own output, so the standard report's audience rule, hidden personal data and row cap
 * all still apply: someone who may not run the standard report gets "not found" for a view built on it. Whoever runs a shared view sees it through their own audience, never
 * the owner's. Each download is audited like a standard report's. Creating, changing, sharing and deleting a view are audited too (names only).
 */
@Service
@RequiredArgsConstructor
public class CustomReportService {

    private static final String ENTITY = "REPORT";

    private final CustomReportRepository repo;
    private final ReportService reports;
    private final RegulatoryAuditService trail;

    // ── Reading ──

    @Transactional(readOnly = true)
    public List<View> list(TenantId t, UUID uid, Audience who, boolean admin) {
        Map<String, String> titles = titles(who);
        return repo.visibleTo(t, uid).stream().map(c -> view(c, uid, admin, titles)).toList();
    }

    @Transactional(readOnly = true)
    public View get(TenantId t, UUID id, UUID uid, Audience who, boolean admin) { return view(visible(t, id, uid), uid, admin, titles(who)); }

    @Transactional(readOnly = true)
    public ReportResult run(TenantId t, UUID id, String asOf, UUID uid, String uname, Audience who) {
        CustomReport c = visible(t, id, uid);
        ReportResult base = reports.run(t, c.getBaseKey(), asOf, uname, who);
        Def def = def(c);
        String problem = CustomReportRules.problem(def, base.columns());
        if (problem != null) throw new HandyFlowException("The standard report this is built on has changed, so this report needs editing. " + problem, HttpStatus.CONFLICT, "INVALID_STATE");
        CustomReportRules.Result r = CustomReportRules.apply(def, base.columns(), base.rows());
        return new ReportResult("CUSTOM", c.getName(), "Custom", Instant.now(), uname, base.asOf(), r.columns(), r.rows(), r.rows().size(), base.truncated());
    }

    /** Runs the view and records the download in the same transaction, so there is no export without a record. */
    @Transactional
    public ExportFile export(TenantId t, UUID id, String asOf, UUID uid, String uname, Audience who, Format format) {
        CustomReport c = visible(t, id, uid);
        ReportResult r = run(t, id, asOf, uid, uname, who);
        trail.record(t.getValue(), ENTITY, UUID.nameUUIDFromBytes(("custom:" + id).getBytes(StandardCharsets.UTF_8)), "EXPORTED",
                "Custom report \"" + c.getName() + "\" (based on " + c.getBaseKey() + "): " + r.rowCount() + " row(s)" + (r.truncated() ? " (the standard report was cut at " + ReportRules.MAX_ROWS + ")" : "")
                        + (r.asOf() == null ? "" : ", as of " + r.asOf()) + ", " + format, uid, uname);
        String subtitle = ReportRules.subtitle(r.generatedAt(), r.generatedBy(), r.asOf(), r.rowCount(), r.truncated());
        byte[] body = switch (format) {
            case CSV -> ("﻿" + ReportRules.csv(r.columns(), r.rows())).getBytes(StandardCharsets.UTF_8);
            case XLSX -> ReportFiles.xlsx(r.title(), subtitle, r.columns(), r.rows());
            case PDF -> ReportFiles.pdf(r.title(), subtitle, r.columns(), r.rows());
        };
        return new ExportFile(ReportRules.fileName("custom-" + slug(c.getName()), today(), format.ext), body, format.contentType);
    }

    // ── Changing ──

    @Transactional
    public View create(TenantId t, SaveRequest r, UUID uid, String uname, Audience who, boolean canShare) {
        if (r.shared() && !canShare) throw new HandyFlowException("Only a manager can share a report with the rest of the company", HttpStatus.FORBIDDEN, "FORBIDDEN");
        Def def = checked(t, r, uname, who);
        if (repo.countOwned(t, uid) >= CustomReportRules.MAX_PER_USER) throw new HandyFlowException("You already have " + CustomReportRules.MAX_PER_USER + " custom reports: delete one first", HttpStatus.CONFLICT, "INVALID_STATE");
        if (repo.nameTaken(t, uid, r.name().trim(), null)) throw new HandyFlowException("You already have a custom report called \"" + r.name().trim() + "\"", HttpStatus.CONFLICT, "INVALID_STATE");
        CustomReport c = guarded(() -> CustomReport.create(t, uid, uname, r.name(), r.description(), def.baseKey(), def.columns(), def.filters(), def.sortColumn(), def.sortDesc(), r.shared()));
        repo.save(c);
        trail.record(t.getValue(), ENTITY, c.getId(), "CUSTOM_CREATED", "Custom report \"" + c.getName() + "\" created on " + c.getBaseKey() + (c.isShared() ? ", shared" : ""), uid, uname);
        return view(c, uid, false, titles(who));
    }

    @Transactional
    public View update(TenantId t, UUID id, SaveRequest r, UUID uid, String uname, Audience who, boolean canShare, boolean admin) {
        CustomReport c = editable(t, id, uid, admin);
        if (r.shared() && !c.isShared() && !canShare) throw new HandyFlowException("Only a manager can share a report with the rest of the company", HttpStatus.FORBIDDEN, "FORBIDDEN");
        Def def = checked(t, r, uname, who);
        if (repo.nameTaken(t, c.getOwnerUserId(), r.name().trim(), c.getId())) throw new HandyFlowException("There is already a custom report called \"" + r.name().trim() + "\"", HttpStatus.CONFLICT, "INVALID_STATE");
        boolean wasShared = c.isShared();
        guarded(() -> { c.change(r.name(), r.description(), def.baseKey(), def.columns(), def.filters(), def.sortColumn(), def.sortDesc(), r.shared()); return null; });
        repo.save(c);
        trail.record(t.getValue(), ENTITY, c.getId(), "CUSTOM_CHANGED", "Custom report \"" + c.getName() + "\" changed" + (wasShared != c.isShared() ? (c.isShared() ? ", now shared" : ", no longer shared") : ""), uid, uname);
        return view(c, uid, admin, titles(who));
    }

    @Transactional
    public void delete(TenantId t, UUID id, UUID uid, String uname, boolean admin) {
        CustomReport c = editable(t, id, uid, admin);
        repo.delete(c);
        trail.record(t.getValue(), ENTITY, c.getId(), "CUSTOM_DELETED", "Custom report \"" + c.getName() + "\" deleted", uid, uname);
    }

    // ── Helpers ──

    /** Validates the request against the standard report's real columns (the caller must be allowed to run that report). */
    private Def checked(TenantId t, SaveRequest r, String uname, Audience who) {
        String np = CustomReportRules.nameProblem(r.name(), r.description());
        if (np != null) throw new HandyFlowException(np, HttpStatus.BAD_REQUEST, "VALIDATION");
        String key = r.baseKey().trim().toUpperCase(Locale.ROOT);
        ReportResult base = reports.run(t, key, null, uname, who);
        Def def = new Def(key, r.columns(), r.filters() == null ? List.of() : r.filters(), r.sortColumn() == null || r.sortColumn().isBlank() ? null : r.sortColumn(), r.sortDesc());
        String p = CustomReportRules.problem(def, base.columns());
        if (p != null) throw new HandyFlowException(p, HttpStatus.BAD_REQUEST, "VALIDATION");
        return def;
    }

    private CustomReport visible(TenantId t, UUID id, UUID uid) {
        CustomReport c = repo.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("Custom report", id.toString()));
        if (!c.isOwnedBy(uid) && !c.isShared()) throw new ResourceNotFoundException("Custom report", id.toString());
        return c;
    }

    private CustomReport editable(TenantId t, UUID id, UUID uid, boolean admin) {
        CustomReport c = visible(t, id, uid);
        if (!c.isOwnedBy(uid) && !admin) throw new HandyFlowException("Only the person who made this report, or an administrator, can change it", HttpStatus.FORBIDDEN, "FORBIDDEN");
        return c;
    }

    private Map<String, String> titles(Audience who) {
        Map<String, String> m = new HashMap<>();
        for (ReportInfo i : reports.catalogue(who)) m.put(i.key(), i.title());
        return m;
    }

    private static View view(CustomReport c, UUID uid, boolean admin, Map<String, String> titles) {
        boolean mine = c.isOwnedBy(uid);
        return new View(c.getId(), c.getName(), c.getDescription(), c.getBaseKey(), titles.get(c.getBaseKey()), c.getColumns(), c.getFilters(), c.getSortColumn(), c.isSortDesc(), c.isShared(),
                c.getOwnerName(), mine, mine || admin, c.getUpdatedAt());
    }

    private static Def def(CustomReport c) { return new Def(c.getBaseKey(), c.getColumns(), c.getFilters(), c.getSortColumn(), c.isSortDesc()); }

    static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("-+$", "");
        return s.isEmpty() ? "report" : s;
    }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Johannesburg")); }

    private static <T> T guarded(Supplier<T> action) {
        try { return action.get(); }
        catch (IllegalArgumentException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "VALIDATION"); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "INVALID_STATE"); }
    }
}
