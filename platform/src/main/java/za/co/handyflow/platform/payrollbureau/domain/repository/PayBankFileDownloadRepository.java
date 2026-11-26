package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayBankFileDownload;

import java.util.UUID;

public interface PayBankFileDownloadRepository extends JpaRepository<PayBankFileDownload, UUID> {

    @Query("SELECT COUNT(d) FROM PayBankFileDownload d WHERE d.tenantId = :tenantId AND d.bankFileId = :bankFileId")
    long countForFile(@Param("tenantId") UUID tenantId, @Param("bankFileId") UUID bankFileId);
}
