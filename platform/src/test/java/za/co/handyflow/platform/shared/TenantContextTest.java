package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression test for the second impersonation bug found alongside the
 * JwtService.extractPermissions NPE (see JwtServiceTest and
 * PLATFORM-ENGINES-PROGRESS.md): getCurrentUserId() used to try
 * UUID.fromString("IMPERSONATION") and throw a raw, unhelpful parse
 * exception. It now checks TenantContext.isImpersonation() first and
 * throws a clear, intentional message instead.
 */
class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getCurrentUserId works normally for a real user")
    void getCurrentUserId_realUser_returnsParsedUuid() {
        UUID userId = UUID.randomUUID();
        TenantContext.setUserId(userId.toString());
        TenantContext.setImpersonation(false);

        assertThat(TenantContext.getCurrentUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("getCurrentUserId fails with a clear message during impersonation, not a raw UUID parse error")
    void getCurrentUserId_impersonation_throwsClearMessage() {
        TenantContext.setUserId("IMPERSONATION");
        TenantContext.setImpersonation(true);

        assertThatThrownBy(TenantContext::getCurrentUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("impersonation")
                .hasMessageNotContaining("Invalid UUID"); // i.e. not the raw UUID.fromString failure
    }

    @Test
    @DisplayName("clear() resets the impersonation flag along with tenant/user")
    void clear_resetsImpersonationFlag() {
        TenantContext.setImpersonation(true);
        TenantContext.clear();

        assertThat(TenantContext.isImpersonation()).isFalse();
    }

    @Test
    @DisplayName("isImpersonation defaults to false when never set")
    void isImpersonation_defaultsFalse() {
        assertThat(TenantContext.isImpersonation()).isFalse();
    }
}
