package za.co.handyflow.platform.invoicing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {}