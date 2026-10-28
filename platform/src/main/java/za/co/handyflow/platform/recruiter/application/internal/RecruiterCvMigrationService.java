package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterCvMigrationRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterCvMigrationDtos.Result;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Moves CVs saved the old way (the PDF as base64 text inside the applicant's record) into file storage, a few at a time. A CV that cannot be
 * read as a PDF is left exactly as it was, so nothing is lost, and the caller moves on past it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterCvMigrationService {

    private final JdbcTemplate jdbc;
    private final CvStorage cvStorage;

    private record Row(UUID id, String cv, String name) {}

    @Transactional
    public Result migrate(TenantId tenantId, UUID after, Integer limit) {
        int batch = RecruiterCvMigrationRules.batchOrDefault(limit);
        List<Row> rows = jdbc.query("""
                SELECT id, cv_url, cv_name FROM rec_applicants
                WHERE tenant_id = ? AND cv_url IS NOT NULL AND cv_url <> '' AND cv_url NOT LIKE '%://%' AND (?::uuid IS NULL OR id > ?::uuid)
                ORDER BY id LIMIT ?
                """, (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("cv_url"), rs.getString("cv_name")), tenantId.getValue(), after, after, batch);
        int moved = 0, unreadable = 0;
        for (Row r : rows) {
            byte[] bytes = RecruiterCvMigrationRules.legacyBytes(r.cv());
            if (bytes == null) { unreadable++; continue; }
            String reference = cvStorage.store(bytes, RecruiterCvMigrationRules.fileName(r.name()));
            int n = jdbc.update("UPDATE rec_applicants SET cv_url = ? WHERE id = ? AND tenant_id = ? AND cv_url = ?", reference, r.id(), tenantId.getValue(), r.cv());
            if (n == 1) moved++;
            else cvStorage.delete(reference); // changed meanwhile: keep what is there, drop the copy
        }
        log.info("Moved {} old CV(s) into storage for tenant={}, {} unreadable", moved, tenantId.getValue(), unreadable);
        return new Result(moved, unreadable, rows.size() < batch, rows.isEmpty() ? after : rows.get(rows.size() - 1).id());
    }
}
