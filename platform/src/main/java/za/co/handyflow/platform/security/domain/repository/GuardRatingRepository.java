package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardRating;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GuardRatingRepository extends JpaRepository<GuardRating, UUID> {

    @Query("SELECT r FROM GuardRating r WHERE r.tenantId = :tenantId AND r.guardId = :guardId AND r.ratedOn >= :since ORDER BY r.ratedOn DESC, r.createdAt DESC")
    List<GuardRating> findForGuardSince(TenantId tenantId, UUID guardId, LocalDate since);
}
