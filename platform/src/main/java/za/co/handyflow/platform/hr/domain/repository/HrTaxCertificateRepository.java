package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrTaxCertificate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrTaxCertificateRepository extends JpaRepository<HrTaxCertificate, UUID> {

    @Query("SELECT c FROM HrTaxCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.taxYear = :taxYear AND c.period = :period ORDER BY c.surname, c.firstNames, c.version")
    List<HrTaxCertificate> forReconciliation(TenantId tenantId, int taxYear, String period);

    @Query("SELECT c FROM HrTaxCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<HrTaxCertificate> findByTenantAndId(TenantId tenantId, UUID id);

    /** What an employee may be given: annual certificates that were released, live ones only. */
    @Query("SELECT c FROM HrTaxCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId = :employeeId AND c.period = 'ANNUAL' AND c.status = 'ISSUED' AND c.releasedAt IS NOT NULL ORDER BY c.taxYear DESC")
    List<HrTaxCertificate> releasedFor(TenantId tenantId, UUID employeeId);

    @Query(value = "SELECT nextval('hr_tax_certificate_seq')", nativeQuery = true)
    long nextSequence();
}
