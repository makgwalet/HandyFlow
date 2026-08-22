package za.co.handyflow.platform.approvals.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.approvals.domain.model.ApprovalStep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {

    @Query("SELECT s FROM ApprovalStep s WHERE s.approvalRequestId = :requestId ORDER BY s.stepOrder ASC")
    List<ApprovalStep> findByApprovalRequest(@Param("requestId") UUID requestId);

    Optional<ApprovalStep> findByPublicToken(String publicToken);

    /**
     * Every PENDING step where approverType=USER and approverValue matches
     * this user, OR approverType=ROLE and approverValue is one of their
     * held authorities — the "my pending approvals" list. authorities is
     * passed in by the controller from the acting user's own JWT, per
     * this module's own no-identity-dependency design (see package-info).
     * <p>
     * Status/ApproverType compared as plain string literals ('PENDING',
     * 'USER', 'ROLE') rather than a fully-qualified nested-enum JPQL path
     * — both are @Enumerated(STRING) columns, so Hibernate resolves a
     * matching string literal directly; safer than the fully-qualified
     * nested-enum-class JPQL syntax for an enum declared INSIDE this
     * entity (as opposed to CustomerActivityRepository's reference to the
     * top-level ActivityType, a genuinely different, already-confirmed case).
     */
    @Query("""
            SELECT s FROM ApprovalStep s
            WHERE s.status = 'PENDING'
              AND (
                (s.approverType = 'USER' AND s.approverValue = :userId)
                OR (s.approverType = 'ROLE' AND s.approverValue IN :authorities)
              )
            ORDER BY s.createdAt ASC
            """)
    List<ApprovalStep> findPendingForApprover(@Param("userId") String userId,
                                              @Param("authorities") List<String> authorities);
}