package za.co.handyflow.platform.payrollbureau.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployeeChangeRequest;
import za.co.handyflow.platform.payrollbureau.domain.repository.PayClientRepository;
import za.co.handyflow.platform.payrollbureau.domain.repository.PayEmployeeChangeRequestRepository;
import za.co.handyflow.platform.payrollbureau.domain.repository.PayEmployeeRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayEmployeeChangeServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final TenantId TENANT = TenantId.of(TENANT_ID);

    @Mock private PayEmployeeChangeRequestRepository changeRepo;
    @Mock private PayEmployeeRepository employeeRepo;
    @Mock private PayClientRepository clientRepo;
    @InjectMocks private PayEmployeeChangeService service;

    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();

    private PayEmployee employee(BigDecimal gross, String account) {
        PayEmployee e = mock(PayEmployee.class);
        lenient().when(e.getId()).thenReturn(UUID.randomUUID());
        lenient().when(e.getTenantId()).thenReturn(TENANT_ID);
        lenient().when(e.getPayClientId()).thenReturn(UUID.randomUUID());
        lenient().when(e.getGrossSalary()).thenReturn(gross);
        lenient().when(e.getTravelAllowance()).thenReturn(BigDecimal.ZERO);
        lenient().when(e.getPensionContribution()).thenReturn(BigDecimal.ZERO);
        lenient().when(e.getMedicalAidContribution()).thenReturn(BigDecimal.ZERO);
        lenient().when(e.getBankName()).thenReturn("FNB");
        lenient().when(e.getBankAccountNumber()).thenReturn(account);
        lenient().when(e.getBankBranchCode()).thenReturn("250655");
        return e;
    }

    @Test
    void unchangedPayAndBankDetailsAreNotHeld() {
        PayEmployee e = employee(new BigDecimal("20000.00"), "123");
        assertThat(service.touchesControlledFields(e, new BigDecimal("20000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "FNB", "123", "250655")).isFalse();
    }

    @Test
    void aNewAccountNumberIsHeld() {
        PayEmployee e = employee(new BigDecimal("20000.00"), "123");
        assertThat(service.touchesControlledFields(e, new BigDecimal("20000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "FNB", "999", "250655")).isTrue();
    }

    @Test
    void asecondOpenChangeForTheSameEmployeeIsRefused() {
        PayEmployee e = employee(new BigDecimal("20000.00"), "123");
        when(changeRepo.findPendingForEmployee(TENANT_ID, e.getId())).thenReturn(Optional.of(mock(PayEmployeeChangeRequest.class)));

        assertThatThrownBy(() -> service.submit(e, maker, new BigDecimal("25000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "FNB", "123", "250655"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already has a change");
        verify(changeRepo, never()).saveAndFlush(any());
    }

    @Test
    void approvalByTheMakerIsRefusedAndNothingIsApplied() {
        PayEmployee e = employee(new BigDecimal("20000.00"), "123");
        PayEmployeeChangeRequest r = PayEmployeeChangeRequest.create(e, maker, new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "FNB", "123", "250655");
        when(changeRepo.findByTenantAndId(TENANT_ID, r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(TENANT, r.getId(), maker, null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("different user");
        verify(e, never()).setGrossSalary(any());
        assertThat(r.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void approvalByAnotherUserAppliesTheChange() {
        PayEmployee e = employee(new BigDecimal("20000.00"), "123");
        PayEmployeeChangeRequest r = PayEmployeeChangeRequest.create(e, maker, new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "FNB", "999", "250655");
        when(changeRepo.findByTenantAndId(TENANT_ID, r.getId())).thenReturn(Optional.of(r));
        when(employeeRepo.findActiveById(TENANT_ID, r.getPayEmployeeId())).thenReturn(Optional.of(e));
        when(employeeRepo.findById(r.getPayEmployeeId())).thenReturn(Optional.of(e));

        service.approve(TENANT, r.getId(), checker, "ok");

        verify(e).setGrossSalary(new BigDecimal("25000"));
        verify(e).setBankDetails("FNB", "999", "250655");
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getDecidedBy()).isEqualTo(checker);
    }

    @Test
    void approvalIsRefusedWhenTheEmployeeChangedSinceTheRequest() {
        PayEmployee e = employee(new BigDecimal("20000.00"), "123");
        PayEmployeeChangeRequest r = PayEmployeeChangeRequest.create(e, maker, new BigDecimal("25000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "FNB", "123", "250655");
        PayEmployee changed = employee(new BigDecimal("22000.00"), "123");
        when(changeRepo.findByTenantAndId(TENANT_ID, r.getId())).thenReturn(Optional.of(r));
        when(employeeRepo.findActiveById(TENANT_ID, r.getPayEmployeeId())).thenReturn(Optional.of(changed));

        assertThatThrownBy(() -> service.approve(TENANT, r.getId(), checker, null))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("changed after");
        verify(changed, never()).setGrossSalary(any());
    }
}
