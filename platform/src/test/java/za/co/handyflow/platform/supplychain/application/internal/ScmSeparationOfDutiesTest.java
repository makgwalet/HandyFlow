package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.SeparationOfDutiesChecker;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** SoD is on by default; the rule itself (other holders / sole approver) is covered by the shared checker's tests. */
class ScmSeparationOfDutiesTest {

    private final SeparationOfDutiesChecker checker = mock(SeparationOfDutiesChecker.class);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID maker = UUID.randomUUID();

    private ScmSeparationOfDuties sod(boolean enforce) {
        ScmSeparationOfDuties s = new ScmSeparationOfDuties(checker);
        ReflectionTestUtils.setField(s, "enforce", enforce);
        return s;
    }

    @Test
    void defaultIsOn() throws Exception {
        var f = ScmSeparationOfDuties.class.getDeclaredField("enforce");
        assertThat(f.getAnnotation(org.springframework.beans.factory.annotation.Value.class).value()).isEqualTo("${app.scm.sod.enforce:true}");
    }

    @Test
    void whenOn_itAsksTheSharedCheckerAboutTheCheckingPermission() {
        UUID item = UUID.randomUUID();
        sod(true).require(tenant, "SCM_INVOICE", maker, maker, "msg", "ScSupplierInvoice", item, "approval");
        verify(checker).require(tenant, "SCM_INVOICE", maker, maker, "msg", "ScSupplierInvoice", item, "approval");
    }

    @Test
    void whenOn_aRefusalFromTheCheckerReachesTheCaller() {
        doThrow(new HandyFlowException("no", org.springframework.http.HttpStatus.CONFLICT, "SAME_PERSON"))
                .when(checker).require(any(), any(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> sod(true).require(tenant, "SCM_INVOICE", maker, maker, "m", "t", UUID.randomUUID(), "w"))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    void emergencySwitchOff_skipsTheCheck() {
        sod(false).require(tenant, "SCM_INVOICE", maker, maker, "m", "t", UUID.randomUUID(), "w");
        verifyNoInteractions(checker);
    }
}
