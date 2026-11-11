package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;
import java.util.stream.Collectors;

/** Stage 1B: fuel customer accounts (classification, credit terms, credit hold) and delivery sites. */
@Service
@RequiredArgsConstructor
public class FuelCustomerService {

    private final FuelCustomerAccountRepository accounts;
    private final FuelCustomerSiteRepository sites;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public List<CustomerAccountResponse> list(TenantId tenantId) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : sites.countByAccount(tenantId)) counts.put((UUID) row[0], (Long) row[1]);
        return accounts.findAllActive(tenantId).stream()
                .map(a -> toResponse(tenantId, a, counts.getOrDefault(a.getId(), 0L).intValue(), null)).toList();
    }

    @Transactional(readOnly = true)
    public CustomerAccountResponse get(TenantId tenantId, UUID id) {
        FuelCustomerAccount a = find(tenantId, id);
        List<CustomerSiteResponse> list = sites.findByAccount(tenantId, id).stream().map(this::toResponse).toList();
        return toResponse(tenantId, a, list.size(), list);
    }

    @Transactional
    public CustomerAccountResponse create(TenantId tenantId, CustomerAccountRequest req) {
        if (req.crmCustomerId() == null) throw new IllegalArgumentException("Choose the customer this account is for.");
        if (!crm.customerExists(tenantId, req.crmCustomerId())) {
            throw new ResourceNotFoundException("Customer", req.crmCustomerId().toString());
        }
        if (accounts.findByCrmCustomerId(tenantId, req.crmCustomerId()).isPresent()) {
            throw new ConflictException("This customer already has a fuel account.");
        }
        FuelCustomerAccount a = accounts.save(FuelCustomerAccount.create(tenantId, req.crmCustomerId(), req.customerType(),
                req.creditLimit(), req.paymentTermsDays(), req.licenceNumber(), req.notes()));
        return toResponse(tenantId, a, 0, List.of());
    }

    @Transactional
    public CustomerAccountResponse update(TenantId tenantId, UUID id, CustomerAccountRequest req) {
        FuelCustomerAccount a = find(tenantId, id);
        a.apply(req.customerType(), req.creditLimit(), req.paymentTermsDays(), req.licenceNumber(), req.notes());
        accounts.save(a);
        return get(tenantId, id);
    }

    @Transactional
    public CustomerAccountResponse setCreditHold(TenantId tenantId, UUID id, CreditHoldRequest req) {
        FuelCustomerAccount a = find(tenantId, id);
        if (req.hold()) a.placeOnHold(req.reason()); else a.releaseHold();
        accounts.save(a);
        return get(tenantId, id);
    }

    @Transactional
    public CustomerSiteResponse addSite(TenantId tenantId, UUID accountId, CustomerSiteRequest req) {
        find(tenantId, accountId);
        FuelCustomerSite s = sites.save(FuelCustomerSite.create(tenantId, accountId, req.name(), req.address(),
                req.contactName(), req.contactPhone(), req.deliveryInstructions(), req.operatingHours()));
        return toResponse(s);
    }

    @Transactional
    public CustomerSiteResponse updateSite(TenantId tenantId, UUID siteId, CustomerSiteRequest req) {
        FuelCustomerSite s = findSite(tenantId, siteId);
        s.update(req.name(), req.address(), req.contactName(), req.contactPhone(), req.deliveryInstructions(), req.operatingHours());
        return toResponse(sites.save(s));
    }

    @Transactional
    public void removeSite(TenantId tenantId, UUID siteId) {
        FuelCustomerSite s = findSite(tenantId, siteId);
        s.softDelete();
        sites.save(s);
    }

    private FuelCustomerAccount find(TenantId tenantId, UUID id) {
        return accounts.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Customer account", id.toString()));
    }

    private FuelCustomerSite findSite(TenantId tenantId, UUID id) {
        return sites.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Delivery site", id.toString()));
    }

    private CustomerAccountResponse toResponse(TenantId tenantId, FuelCustomerAccount a, int siteCount, List<CustomerSiteResponse> siteList) {
        String name = crm.findCustomerById(tenantId, a.getCrmCustomerId()).map(CustomerSummary::name).orElse("(customer removed)");
        return new CustomerAccountResponse(a.getId(), a.getCrmCustomerId(), name, a.getCustomerType(), a.getCreditLimit(),
                a.getPaymentTermsDays(), a.isCreditHold(), a.getCreditHoldReason(), a.getLicenceNumber(), a.getNotes(),
                siteCount, siteList, a.getCreatedAt());
    }

    private CustomerSiteResponse toResponse(FuelCustomerSite s) {
        return new CustomerSiteResponse(s.getId(), s.getAccountId(), s.getName(), s.getAddress(), s.getContactName(),
                s.getContactPhone(), s.getDeliveryInstructions(), s.getOperatingHours());
    }
}
