package za.co.handyflow.platform.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.billing.application.BillingFacade;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.billing.domain.model.SubscriptionStatus;
import za.co.handyflow.platform.billing.domain.repository.SubscriptionRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureGuardTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());

    @Mock BillingFacade billing;
    @Mock SubscriptionRepository subscriptions;
    @InjectMocks FeatureGuard guard;

    private void status(SubscriptionStatus s) {
        Subscription sub = mock(Subscription.class);
        when(sub.getStatus()).thenReturn(s);
        when(subscriptions.findByTenantId(T)).thenReturn(Optional.of(sub));
    }

    @Test @DisplayName("hasModuleFor is true for an active subscription that includes the module")
    void entitled() {
        status(SubscriptionStatus.ACTIVE);
        when(billing.hasActiveSubscription(T)).thenReturn(true);
        when(billing.isModuleActive(T, "fuel")).thenReturn(true);
        assertTrue(guard.hasModuleFor(T, "fuel"));
    }

    @Test @DisplayName("hasModuleFor is false when the module is not in the plan")
    void notInPlan() {
        status(SubscriptionStatus.ACTIVE);
        when(billing.hasActiveSubscription(T)).thenReturn(true);
        when(billing.isModuleActive(T, "fuel")).thenReturn(false);
        assertFalse(guard.hasModuleFor(T, "fuel"));
    }

    @Test @DisplayName("hasModuleFor is false for a suspended or cancelled tenant even if the module is listed")
    void suspendedOrCancelled() {
        status(SubscriptionStatus.SUSPENDED);
        assertFalse(guard.hasModuleFor(T, "fuel"));
        status(SubscriptionStatus.CANCELLED);
        assertFalse(guard.hasModuleFor(T, "fuel"));
        verify(billing, never()).isModuleActive(any(), any());
    }

    @Test @DisplayName("hasModuleFor is false with no tenant")
    void noTenant() { assertFalse(guard.hasModuleFor(null, "fuel")); }
}
