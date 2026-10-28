package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.*;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicantRepository;
import za.co.handyflow.platform.recruiter.domain.repository.RecApplicationRepository;
import za.co.handyflow.platform.recruiter.dto.RecruiterApprovalDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unsuccessful applicants' personal details are not kept for ever (POPIA). This lists applicants whose retention period has ended and, when
 * someone confirms, removes what identifies them and deletes their CV. Nothing is removed on a timer: a person looks at the list first.
 * Applications, stages and scores stay, without the person, so recruiting figures still add up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterRetentionService {

    private final RecApplicantRepository applicantRepo;
    private final RecApplicationRepository applicationRepo;
    private final RecruiterApprovalService approvalService;
    private final CvStorage cvStorage;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<RemovalCandidate> due(TenantId tenantId) { return due(tenantId, Instant.now()); }

    List<RemovalCandidate> due(TenantId tenantId, Instant now) {
        int months = approvalService.settings(tenantId).getRetentionMonths();
        Instant cutoff = now.atZone(ZoneId.of("Africa/Johannesburg")).minusMonths(months).toInstant();
        List<RecApplicant> older = applicantRepo.notChangedSince(tenantId, cutoff);
        if (older.isEmpty()) return List.of();
        Map<UUID, List<RecApplication>> byApplicant = applicationRepo.findByApplicantIdIn(older.stream().map(RecApplicant::getId).toList())
                .stream().collect(Collectors.groupingBy(RecApplication::getApplicantId));
        List<RemovalCandidate> out = new ArrayList<>();
        for (RecApplicant a : older) {
            List<RecApplication> apps = byApplicant.getOrDefault(a.getId(), List.of());
            if (problem(a, apps, months, now) != null) continue;
            Instant last = lastActivity(a, apps);
            out.add(new RemovalCandidate(a.getId(), a.getFullName(), a.getEmail(), last, RecruiterRetentionRules.dueOn(last, months), apps.size(), a.getCvUrl() != null));
        }
        return out;
    }

    /** Removes the details of each applicant who is still past the period when this runs. The others are listed with the reason. */
    @Transactional
    public RemovalResult remove(TenantId tenantId, List<UUID> applicantIds, UUID by) { return remove(tenantId, applicantIds, by, Instant.now()); }

    RemovalResult remove(TenantId tenantId, List<UUID> applicantIds, UUID by, Instant now) {
        if (applicantIds == null || applicantIds.isEmpty()) throw new IllegalArgumentException("Choose at least one applicant.");
        int months = approvalService.settings(tenantId).getRetentionMonths();
        int removed = 0;
        List<Refused> refused = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(applicantIds)) {
            Optional<RecApplicant> found = applicantRepo.findById(id).filter(a -> tenantId.getValue().equals(a.getTenantId().getValue()));
            if (found.isEmpty()) { refused.add(new Refused(id, "No such applicant.")); continue; }
            RecApplicant a = found.get();
            List<RecApplication> apps = applicationRepo.findByApplicantId(id);
            String problem = problem(a, apps, months, now);
            if (problem != null) { refused.add(new Refused(id, problem)); continue; }
            boolean hadCv = a.getCvUrl() != null;
            try {
                cvStorage.delete(a.getCvUrl());
            } catch (RuntimeException e) {
                refused.add(new Refused(id, "The CV file could not be deleted, so nothing was removed. Try again."));
                continue;
            }
            a.anonymise();
            applicantRepo.save(a);
            // Free text that can name or describe the person. Stage, score and job stay.
            jdbc.update("UPDATE rec_applications SET notes = NULL, rejection_reason = NULL, referrer_name = NULL WHERE applicant_id = ?", id);
            jdbc.update("UPDATE rec_interviews SET notes = NULL WHERE application_id IN (SELECT id FROM rec_applications WHERE applicant_id = ?)", id);
            jdbc.update("UPDATE rec_stage_history SET notes = NULL WHERE application_id IN (SELECT id FROM rec_applications WHERE applicant_id = ?)", id);
            jdbc.update("DELETE FROM rec_outbox WHERE application_id IN (SELECT id FROM rec_applications WHERE applicant_id = ?)", id);
            jdbc.update("INSERT INTO rec_retention_log (id, tenant_id, applicant_id, removed_by, removed_at, applications, had_cv) VALUES (?, ?, ?, ?, NOW(), ?, ?)",
                    UUID.randomUUID(), tenantId.getValue(), id, by, apps.size(), hadCv);
            removed++;
        }
        log.info("Removed the details of {} applicant(s) for tenant={} by user={}", removed, tenantId.getValue(), by);
        return new RemovalResult(removed, refused);
    }

    private static String problem(RecApplicant a, List<RecApplication> apps, int months, Instant now) {
        List<RecruiterRetentionRules.App> view = apps.stream()
                .map(x -> new RecruiterRetentionRules.App(x.getStage(), x.getHrEmployeeId() != null, latest(x))).toList();
        return RecruiterRetentionRules.removalProblem(a.isAnonymised(), a.getUpdatedAt(), view, months, now);
    }

    private static Instant lastActivity(RecApplicant a, List<RecApplication> apps) {
        Instant last = a.getUpdatedAt() == null ? a.getCreatedAt() : a.getUpdatedAt();
        for (RecApplication x : apps) { Instant t = latest(x); if (t != null && (last == null || t.isAfter(last))) last = t; }
        return last == null ? Instant.EPOCH : last;
    }

    private static Instant latest(RecApplication x) {
        Instant t = x.getUpdatedAt();
        if (x.getStageChangedAt() != null && (t == null || x.getStageChangedAt().isAfter(t))) t = x.getStageChangedAt();
        return t;
    }
}
