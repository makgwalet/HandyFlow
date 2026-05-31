package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrTaxTable;

import java.util.List;
import java.util.UUID;

public interface HrTaxTableRepository extends JpaRepository<HrTaxTable, UUID> {

    @Query("SELECT t FROM HrTaxTable t WHERE t.taxYear = :taxYear ORDER BY t.incomeFrom")
    List<HrTaxTable> findByTaxYear(int taxYear);
}