package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelPortalAccessGrant;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelPortalAccessGrantRepository;
import za.co.handyflow.platform.fuel.dto.PortalDtos.GrantResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.EmailTemplates;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Staff side: invite a customer contact, see who has access, take it away. */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelPortalService {

    private final FuelPortalAccessGrantRepository grants;
    private final FuelCustomerAccountRepository accounts;
    private final CrmFacade crm;
    private final EmailService emailService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public GrantResponse invite(TenantId tenantId, UUID accountId, String email, UUID invitedBy) {
        FuelCustomerAccount account = account(tenantId, accountId);
        boolean already = grants.findByTenantAndAccount(tenantId.getValue(), accountId).stream()
                .anyMatch(g -> g.getInviteEmail().equalsIgnoreCase(email.trim()) && !"REVOKED".equals(g.getStatus()));
        if (already) throw new HandyFlowException("This email already has a pending or active invite for this customer", HttpStatus.CONFLICT, "ALREADY_INVITED");

        FuelPortalAccessGrant grant = grants.save(FuelPortalAccessGrant.createInvite(tenantId.getValue(), accountId, email, invitedBy));
        String name = crm.findCustomerById(tenantId, account.getCrmCustomerId()).map(CustomerSummary::name).orElse("your fuel supplier");
        try {
            emailService.send(email, "You have been invited to the fuel customer portal",
                    EmailTemplates.portalInvite(name, "Fuel", frontendUrl + "/fuel/portal/auth/accept-invite?token=" + grant.getInviteToken()));
        } catch (Exception e) {
            log.error("[Fuel] Failed to send portal invite email to={} account={}: {}", email, accountId, e.getMessage(), e);
        }
        return toResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> list(TenantId tenantId, UUID accountId) {
        account(tenantId, accountId);
        return grants.findByTenantAndAccount(tenantId.getValue(), accountId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public GrantResponse revoke(TenantId tenantId, UUID accountId, UUID grantId, UUID by) {
        FuelPortalAccessGrant g = grants.findByTenantIdAndId(tenantId.getValue(), grantId)
                .filter(x -> x.getAccountId().equals(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Portal access", grantId.toString()));
        g.revoke(by);
        return toResponse(grants.save(g));
    }

    private FuelCustomerAccount account(TenantId tenantId, UUID accountId) {
        return accounts.findActiveById(tenantId, accountId).orElseThrow(() -> new ResourceNotFoundException("Customer account", accountId.toString()));
    }

    private GrantResponse toResponse(FuelPortalAccessGrant g) {
        return new GrantResponse(g.getId(), g.getInviteEmail(), g.getStatus(), g.getInvitedAt(), g.getAcceptedAt(), g.getRevokedAt());
    }
}
