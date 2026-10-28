package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingCostRules;
import za.co.handyflow.platform.training.domain.model.TrainingEnrollment;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.math.BigDecimal;
import java.util.UUID;

/** Records a result. When the course has a pass mark the score decides pass or fail, so two people marking the same score cannot disagree. */
@Service
@RequiredArgsConstructor
public class TrainingResultService {

    private final TrainingEnrollmentService enrollments;
    private final TrainingSessionRepository sessions;
    private final TrainingCourseRepository courses;

    @Transactional
    public TrainingEnrollment complete(TenantId tenantId, UUID enrollmentId, BigDecimal score, boolean requestedPassed) {
        TrainingEnrollment enrollment = enrollments.get(tenantId, enrollmentId);
        BigDecimal mark = sessions.findByTenantAndId(tenantId, enrollment.getSessionId())
                .flatMap(s -> courses.findActiveById(tenantId, s.getCourseId())).map(c -> c.getPassMark()).orElse(null);
        TrainingCostRules.Decision d = TrainingCostRules.decide(score, mark, requestedPassed);
        if (d.problem() != null) throw new HandyFlowException(d.problem(), HttpStatus.BAD_REQUEST, "SCORE_REQUIRED");
        return enrollments.complete(tenantId, enrollmentId, score, d.passed());
    }
}
