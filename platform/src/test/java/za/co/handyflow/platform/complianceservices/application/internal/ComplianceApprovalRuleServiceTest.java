package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceApprovalRule;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceApprovalRuleRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ApprovalRuleDtos.RuleRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceApprovalRuleServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock ComplianceApprovalRuleRepository rules;
    @Mock ComplianceClientRepository clients;
    @InjectMocks ComplianceApprovalRuleService service;

    @Test
    void ruleForAnUnknownClientIsRefused() {
        UUID client = UUID.randomUUID();
        when(clients.findByIdForTenant(TENANT, client)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.propose(TENANT, new RuleRequest("Acme", null, null, null, client, "PRACTICE_OWNER", "Always check this client"), ME)).isInstanceOf(ResourceNotFoundException.class);
        verify(rules, never()).save(any());
    }

    @Test
    void proposerCannotActivateTheirOwnRule() {
        ComplianceApprovalRule r = ComplianceApprovalRule.propose(TENANT, "Big", BigDecimal.TEN, null, null, null, "PRACTICE_OWNER", "Large quotes need a second look", ME);
        when(rules.findOne(TENANT, r.getId())).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.activate(TENANT, r.getId(), ME)).isInstanceOf(HandyFlowException.class);
        verify(rules, never()).save(any());
    }

    @Test
    void invalidRuleIsRefusedAsBadRequest() {
        assertThatThrownBy(() -> service.propose(TENANT, new RuleRequest("Big", null, null, null, null, "PRACTICE_OWNER", "Large quotes need a second look"), ME)).isInstanceOf(HandyFlowException.class);
    }
}
