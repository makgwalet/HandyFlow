package za.co.handyflow.platform.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SarsTaxTableRepository extends JpaRepository<SarsTaxTable, UUID> {
    @Query("SELECT t FROM SarsTaxTable t WHERE t.taxYear = :taxYear ORDER BY t.incomeFrom ASC")
    List<SarsTaxTable> findByTaxYear(@Param("taxYear") int taxYear);
}