package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayBureauProfile;

import java.util.Optional;
import java.util.UUID;

public interface PayBureauProfileRepository extends JpaRepository<PayBureauProfile, UUID> {

    @Query("SELECT p FROM PayBureauProfile p WHERE p.tenantId = :tenantId")
    Optional<PayBureauProfile> findByTenantId(@Param("tenantId") UUID tenantId);
}