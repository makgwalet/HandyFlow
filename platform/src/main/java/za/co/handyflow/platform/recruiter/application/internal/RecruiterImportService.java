package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.*;
import za.co.handyflow.platform.recruiter.domain.repository.*;
import za.co.handyflow.platform.recruiter.dto.RecruiterImportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adds candidates from a CSV to a job as new applications at the first stage. Nobody is emailed and the hiring team is not notified,
 * so a file of hundreds does not flood anyone. The same email already on the job is left alone.
 */
@Service
@RequiredArgsConstructor
public class RecruiterImportService {

    private final RecJobRepository jobRepo;
    private final RecApplicantRepository applicantRepo;
    private final RecApplicationRepository applicationRepo;
    private final RecStageHistoryRepository historyRepo;

    @Transactional
    public ImportResult importCandidates(TenantId tenantId, UUID jobId, ImportRequest req, boolean dryRun, String byName) {
        String basis = RecruiterImportRules.basisProblem(req.lawfulBasis());
        if (basis != null) throw new HandyFlowException(basis, HttpStatus.BAD_REQUEST, "BASIS_REQUIRED");
        RecJob job = jobRepo.findByIdAndTenantId(jobId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Job", jobId.toString()));
        RecruiterImportRules.Parsed parsed = RecruiterImportRules.parse(req.csv());
        if (parsed.fileProblem() != null) throw new HandyFlowException(parsed.fileProblem(), HttpStatus.BAD_REQUEST, "INVALID_FILE");

        int added = 0, already = 0;
        List<LineProblem> problems = new ArrayList<>();
        for (Map.Entry<Integer, String> e : parsed.badLines().entrySet()) problems.add(new LineProblem(e.getKey(), e.getValue()));

        for (RecruiterImportRules.Row r : parsed.rows()) {
            RecApplicant existing = applicantRepo.findByTenantIdAndEmail(tenantId, r.email()).orElse(null);
            if (existing != null && applicationRepo.findByJobIdAndApplicantId(jobId, existing.getId()).isPresent()) { already++; continue; }
            added++;
            if (dryRun) continue;
            RecApplicant applicant = existing != null ? existing : applicantRepo.save(RecApplicant.create(tenantId, r.firstName(), r.lastName(), r.email(),
                    blankToNull(r.phone()), blankToNull(r.location()), blankToNull(r.linkedinUrl()), blankToNull(r.portfolioUrl()), null, null));
            RecApplication application = applicationRepo.save(RecApplication.create(tenantId, jobId, applicant.getId(), RecruiterImportRules.SOURCE, null));
            job.incrementApplicationCount();
            historyRepo.save(RecStageHistory.create(application.getId(), null, "APPLIED", byName == null ? "Recruiter" : byName, "Added from a CSV import"));
        }
        if (!dryRun && added > 0) jobRepo.save(job);

        problems.sort(java.util.Comparator.comparingInt(LineProblem::line));
        int shown = Math.min(problems.size(), RecruiterImportRules.MAX_PROBLEMS_SHOWN);
        return new ImportResult(dryRun, parsed.rows().size() + parsed.badLines().size(), added, already, parsed.badLines().size(),
                problems.subList(0, shown), problems.size() - shown);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
