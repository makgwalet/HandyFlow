package za.co.handyflow.platform.admin.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.admin.domain.repository.AdminAuditLogRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for AdminDiscountService — no Spring context, all
 * dependencies mocked with Mockito, matching this codebase's own
 * established convention (see ClinicServiceTest, PayrollEngineParityTest).
 * <p>
 * Written as part of the VAT-consolidation + FIXED-discount pass: the
 * {@code admin} module previously had zero backend unit tests despite
 * {@link AdminDiscountService#resolveDiscount} being the exact method
 * this pass changed to close a real, confirmed bug (every FIXED-type
 * discount code silently resolved to 0% — see resolveCodePercent()'s
 * own Javadoc for the full finding). These tests cover both the
 * pre-existing resolution behaviour (regression coverage it never had)
 * and the new FIXED-code conversion specifically.
 * <p>
 * resolveDiscount() and resolveCodePercent() are both
 * package-private-callable from this same package
 * (za.co.handyflow.platform.admin.application.internal), so no
 * reflection or facade indirection is needed to exercise
 * resolveCodePercent()'s edge cases directly through the public
 * resolveDiscount() entry point — every test here goes through the real
 * public method, not the private helper directly, to keep this a true
 * behavioural test rather than an implementation-detail test.
 */
@ExtendWith(MockitoExtension.class)
class AdminDiscountServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock AdminAuditLogRepository auditRepo;

    private AdminDiscountService service;

    private AdminDiscountService newService() {
        return new AdminDiscountService(jdbc, auditRepo);
    }

    private static final UUID TENANT_ID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    private static final String MODULE_KEY = "fleet";

    private static Map<String, Object> discountCodeRow(String discountType, BigDecimal value, String appliesTo) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("discount_type", discountType);
        row.put("value", value);
        row.put("applies_to", appliesTo);
        row.put("module_key", "ALL".equals(appliesTo) ? null : MODULE_KEY);
        return row;
    }

    @Nested
    @DisplayName("resolveDiscount — baseline")
    class Baseline {

        @Test
        @DisplayName("returns NONE / zero when nothing applies and no code is given")
        void returnsNoneWhenNothingApplies() {
            service = newService();

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, null);

            assertThat(result.pct()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.source()).isEqualTo("NONE");
            assertThat(result.hasDiscount()).isFalse();
        }

        @Test
        @DisplayName("a blank discount code is treated the same as no code at all")
        void blankCodeIsIgnored() {
            service = newService();

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "   ");

            assertThat(result.hasDiscount()).isFalse();
            // FIX: a real build (mvn test) caught this — verifyNoInteractions(jdbc)
            // was wrong. Partnership and Volume checks run unconditionally in
            // resolveDiscount() regardless of whether a discount code was
            // supplied at all (confirmed directly against the method: only the
            // block guarded by `if (discountCode != null && !discountCode.isBlank())`
            // is skipped) — only the discount-CODE-specific lookup
            // (queryForMap against admin_discounts) is what a blank code
            // actually suppresses. Narrowed the verification to that one call.
            org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
                    .queryForMap(anyString(), any());
        }
    }

    @Nested
    @DisplayName("resolveDiscount — PERCENT codes (pre-existing behaviour)")
    class PercentCodes {

        @Test
        @DisplayName("resolves a PERCENT code applying to ALL modules")
        void resolvesPercentCode() {
            service = newService();
            when(jdbc.queryForMap(anyString(), any()))
                    .thenReturn(discountCodeRow("PERCENT", new BigDecimal("20"), "ALL"));

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "SAVE20");

            assertThat(result.pct()).isEqualByComparingTo(new BigDecimal("20"));
            assertThat(result.source()).isEqualTo("CODE:SAVE20");
        }

        @Test
        @DisplayName("a PERCENT code scoped to a different module is ignored")
        void ignoresCodeScopedToDifferentModule() {
            service = newService();
            Map<String, Object> row = discountCodeRow("PERCENT", new BigDecimal("20"), "MODULE");
            row.put("module_key", "security"); // not MODULE_KEY ("fleet")
            when(jdbc.queryForMap(anyString(), any())).thenReturn(row);

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "SECURITY20");

            assertThat(result.hasDiscount()).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveDiscount — FIXED codes (the fix)")
    class FixedCodes {

        @Test
        @DisplayName("converts a FIXED amount to the equivalent percentage of the module's catalogue price")
        void convertsFixedToEquivalentPercent() {
            service = newService();
            // R50 off a R200/month module = 25%.
            when(jdbc.queryForMap(anyString(), any()))
                    .thenReturn(discountCodeRow("FIXED", new BigDecimal("50"), "ALL"));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any()))
                    .thenReturn(new BigDecimal("200"));

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "FIXED50");

            assertThat(result.pct()).isEqualByComparingTo(new BigDecimal("25.0000"));
            assertThat(result.source()).isEqualTo("CODE:FIXED50");
            assertThat(result.hasDiscount()).isTrue();
        }

        @Test
        @DisplayName("caps a FIXED amount larger than the module's price at 100%, never over")
        void capsAt100PercentWhenFixedExceedsPrice() {
            service = newService();
            // R500 off a R200/month module must never imply more than 100% off.
            when(jdbc.queryForMap(anyString(), any()))
                    .thenReturn(discountCodeRow("FIXED", new BigDecimal("500"), "ALL"));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any()))
                    .thenReturn(new BigDecimal("200"));

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "HUGEFIXED");

            assertThat(result.pct()).isEqualByComparingTo(new BigDecimal("100"));
        }

        @Test
        @DisplayName("falls back to zero — not an exception — when the module's catalogue price can't be resolved")
        void fallsBackToZeroWhenPriceUnresolvable() {
            service = newService();
            when(jdbc.queryForMap(anyString(), any()))
                    .thenReturn(discountCodeRow("FIXED", new BigDecimal("50"), "ALL"));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any()))
                    .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, "unknown-module", "FIXED50");

            assertThat(result.hasDiscount()).isFalse();
            assertThat(result.pct()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("falls back to zero when the module's catalogue price is zero or null")
        void fallsBackToZeroWhenPriceIsZero() {
            service = newService();
            when(jdbc.queryForMap(anyString(), any()))
                    .thenReturn(discountCodeRow("FIXED", new BigDecimal("50"), "ALL"));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any()))
                    .thenReturn(BigDecimal.ZERO);

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "FIXED50");

            assertThat(result.hasDiscount()).isFalse();
        }
    }

    @Nested
    @DisplayName("resolveDiscount — resolution order is unchanged by the FIXED fix")
    class ResolutionOrderUnchanged {

        @Test
        @DisplayName("a Partnership discount still beats a lower-value FIXED code, compared like-for-like as a percentage")
        void partnershipBeatsLowerFixedCode() {
            service = newService();
            // Partnership: 30% off. FIXED code: R50 off a R200 module = 25% off.
            // Partnership must still win — this is the exact "best wins,
            // never stacks" rule the FIXED fix was required NOT to change.
            // NOTE: any(Object.class), not bare any() — JdbcTemplate overloads
            // queryForList() several ways (String,Object...) vs
            // (String,Class<T>,Object...) vs (String,Object[],int[]); two loose
            // any() matchers left the call genuinely ambiguous to javac
            // (confirmed via a real build: "reference to queryForList is
            // ambiguous"). Typing the matcher to Object pins it to the plain
            // varargs overload the production code actually calls.
            when(jdbc.queryForList(anyString(), any(Object.class), any(Object.class)))
                    .thenReturn(List.of(Map.of("discount_pct", new BigDecimal("30"), "partner_name", "AcmeCo")));
            when(jdbc.queryForMap(anyString(), any()))
                    .thenReturn(discountCodeRow("FIXED", new BigDecimal("50"), "ALL"));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any()))
                    .thenReturn(new BigDecimal("200"));

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "FIXED50");

            assertThat(result.pct()).isEqualByComparingTo(new BigDecimal("30"));
            assertThat(result.source()).startsWith("PARTNERSHIP:");
        }

        @Test
        @DisplayName("a FIXED code beats a lower Partnership discount once converted to a percentage")
        void fixedCodeBeatsLowerPartnership() {
            service = newService();
            // Partnership: 10% off. FIXED code: R50 off a R200 module = 25% off — code wins.
            when(jdbc.queryForList(anyString(), any(Object.class), any(Object.class)))
                    .thenReturn(List.of(Map.of("discount_pct", new BigDecimal("10"), "partner_name", "AcmeCo")));
            when(jdbc.queryForMap(anyString(), any()))
                    .thenReturn(discountCodeRow("FIXED", new BigDecimal("50"), "ALL"));
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any()))
                    .thenReturn(new BigDecimal("200"));

            AdminDiscountService.DiscountResult result =
                    service.resolveDiscount(TENANT_ID, MODULE_KEY, "FIXED50");

            assertThat(result.pct()).isEqualByComparingTo(new BigDecimal("25.0000"));
            assertThat(result.source()).isEqualTo("CODE:FIXED50");
        }
    }
}
