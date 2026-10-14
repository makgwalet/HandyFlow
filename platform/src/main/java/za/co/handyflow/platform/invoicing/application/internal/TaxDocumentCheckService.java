package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.invoicing.application.internal.TaxDocumentRules.Issue;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Finance patch F14: what is missing from the business's or a customer's details for a compliant tax document. */
@Service
@RequiredArgsConstructor
public class TaxDocumentCheckService {

    private final TenantFacade tenantFacade;
    private final InvoiceRepository invoiceRepository;
    private final CrmFacade crmFacade;

    @Transactional(readOnly = true)
    public List<Issue> forBusiness(TenantId tenantId) {
        TenantDetails t = tenantFacade.findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));
        return TaxDocumentRules.supplierIssues(t.vatNumber(), t.address(), t.companyName());
    }

    /** The business's own issues plus anything missing about this invoice's customer. */
    @Transactional(readOnly = true)
    public List<Issue> forInvoice(TenantId tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));
        List<Issue> out = new ArrayList<>(forBusiness(tenantId));
        String name = invoice.getWalkinClientName();
        java.util.Map<String, String> address = null;
        String vat = null;
        if (invoice.getCustomerId() != null) {
            var c = crmFacade.findCustomerById(tenantId, invoice.getCustomerId()).orElse(null);
            if (c != null) { name = c.name(); address = c.address(); vat = c.taxNumber(); }
        }
        out.addAll(TaxDocumentRules.recipientIssues(invoice.getTotal(), name, address, vat));
        return out;
    }
}
