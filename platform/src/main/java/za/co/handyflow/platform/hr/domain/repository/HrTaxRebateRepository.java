package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrTaxRebate;

import java.util.List;
import java.util.UUID;

public interface HrTaxRebateRepository extends JpaRepository<HrTaxRebate, UUID> {

    @Query("SELECT r FROM HrTaxRebate r WHERE r.taxYear = :taxYear")
    List<HrTaxRebate> findByTaxYear(int taxYear);
}