package za.co.handyflow.platform.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SarsTaxRebateRepository extends JpaRepository<SarsTaxRebate, UUID> {
    @Query("SELECT r FROM SarsTaxRebate r WHERE r.taxYear = :taxYear")
    List<SarsTaxRebate> findByTaxYear(@Param("taxYear") int taxYear);
}