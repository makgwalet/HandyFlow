package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrTaxCertificateLine;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HrTaxCertificateLineRepository extends JpaRepository<HrTaxCertificateLine, UUID> {

    @Query("SELECT l FROM HrTaxCertificateLine l WHERE l.certificateId IN :ids ORDER BY l.code")
    List<HrTaxCertificateLine> forCertificates(Collection<UUID> ids);
}
