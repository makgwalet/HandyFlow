package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayBankFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayBankFileRepository extends JpaRepository<PayBankFile, UUID> {

    @Query("SELECT f FROM PayBankFile f WHERE f.tenantId = :tenantId AND f.payRunId = :payRunId ORDER BY f.fileNumber DESC")
    List<PayBankFile> findByRun(@Param("tenantId") UUID tenantId, @Param("payRunId") UUID payRunId);

    @Query("SELECT f FROM PayBankFile f WHERE f.tenantId = :tenantId AND f.payRunId = :payRunId AND f.id = :id")
    Optional<PayBankFile> findByRunAndId(@Param("tenantId") UUID tenantId, @Param("payRunId") UUID payRunId, @Param("id") UUID id);
}
