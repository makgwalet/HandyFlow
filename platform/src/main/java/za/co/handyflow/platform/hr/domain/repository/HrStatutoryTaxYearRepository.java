package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrStatutoryTaxYear;

import java.util.List;

public interface HrStatutoryTaxYearRepository extends JpaRepository<HrStatutoryTaxYear, Integer> {

    @Query("SELECT y FROM HrStatutoryTaxYear y ORDER BY y.taxYear DESC")
    List<HrStatutoryTaxYear> allNewestFirst();
}
