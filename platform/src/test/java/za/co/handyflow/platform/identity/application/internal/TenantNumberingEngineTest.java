package za.co.handyflow.platform.identity.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.identity.domain.model.TenantNumberingConfig;
import za.co.handyflow.platform.identity.domain.repository.TenantNumberingConfigRepository;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;

import java.time.Year;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for TenantNumberingEngine — no Spring context, matching
 * this codebase's own established convention (see UserManagementServiceTest).
 * <p>
 * Covers exactly the new business logic this class adds on top of the
 * already-tested TenantSequenceService: tenant-code prefixing, the built-in
 * default type codes (the actual fix for the INV- collision across
 * invoicing/bookkeeping/facilities-management), and per-tenant
 * TenantNumberingConfig overrides (type code, prefix, padding, year).
 */
@ExtendWith(MockitoExtension.class)
class TenantNumberingEngineTest {

    @Mock
    private TenantNumberingConfigRepository configRepository;
    @Mock
    private TenantSequenceService sequenceService;
    @Mock
    private TenantDocumentCodeResolver documentCodeResolver;

    @InjectMocks
    private TenantNumberingEngine engine;

    private final TenantId tenantId = TenantId.generate();

    @Nested
    @DisplayName("no config row (current default behaviour)")
    class NoConfigRow {

        @Test
        @DisplayName("uses tenant code + built-in default type code, 5-digit padding, no year")
        void usesDefaultTypeCodeAndPadding() {
            when(sequenceService.nextValue(tenantId, "INVOICE")).thenReturn(1L);
            when(documentCodeResolver.resolveDocumentCode(tenantId)).thenReturn("FPS");
            when(configRepository.findByTenantIdAndDocumentType(tenantId.getValue(), "INVOICE"))
                    .thenReturn(Optional.empty());

            String result = engine.next(tenantId, "INVOICE", "INV");

            assertThat(result).isEqualTo("FPS-INV-00001");
        }

        @Test
        @DisplayName("falls back to caller-supplied type code for an unmapped document type")
        void fallsBackToCallerSuppliedTypeCode() {
            when(sequenceService.nextValue(tenantId, "SOME_NEW_DOC_TYPE")).thenReturn(7L);
            when(documentCodeResolver.resolveDocumentCode(tenantId)).thenReturn("ABC");
            when(configRepository.findByTenantIdAndDocumentType(tenantId.getValue(), "SOME_NEW_DOC_TYPE"))
                    .thenReturn(Optional.empty());

            String result = engine.next(tenantId, "SOME_NEW_DOC_TYPE", "XYZ");

            assertThat(result).isEqualTo("ABC-XYZ-00007");
        }

        @Test
        @DisplayName("distinct default type codes prevent the INV- collision across modules")
        void invoiceAndCreditNoteAndQuoteDoNotCollide() {
            when(documentCodeResolver.resolveDocumentCode(tenantId)).thenReturn("FPS");
            when(configRepository.findByTenantIdAndDocumentType(any(), anyString()))
                    .thenReturn(Optional.empty());
            when(sequenceService.nextValue(tenantId, "INVOICE")).thenReturn(1L);
            when(sequenceService.nextValue(tenantId, "QUOTE")).thenReturn(1L);
            when(sequenceService.nextValue(tenantId, "CREDIT_NOTE")).thenReturn(1L);

            assertThat(engine.next(tenantId, "INVOICE", "INV")).isEqualTo("FPS-INV-00001");
            assertThat(engine.next(tenantId, "QUOTE", "QT")).isEqualTo("FPS-QT-00001");
            assertThat(engine.next(tenantId, "CREDIT_NOTE", "CN")).isEqualTo("FPS-CN-00001");
        }
    }

    @Nested
    @DisplayName("with a tenant_numbering_config override row")
    class WithConfigRow {

        @Test
        @DisplayName("applies configured padding and year when includeYear is true")
        void appliesPaddingAndYear() {
            TenantNumberingConfig config = configWith("INV", null, (short) 6, true);
            when(sequenceService.nextValue(tenantId, "INVOICE")).thenReturn(1L);
            when(documentCodeResolver.resolveDocumentCode(tenantId)).thenReturn("FPS");
            when(configRepository.findByTenantIdAndDocumentType(tenantId.getValue(), "INVOICE"))
                    .thenReturn(Optional.of(config));

            String result = engine.next(tenantId, "INVOICE", "INV");

            assertThat(result).isEqualTo("FPS-INV-" + Year.now().getValue() + "-000001");
        }

        @Test
        @DisplayName("a full prefix_override replaces tenant code + type code entirely")
        void prefixOverrideWins() {
            TenantNumberingConfig config = configWith(null, "CUSTOM-PREFIX", (short) 5, false);
            when(sequenceService.nextValue(tenantId, "INVOICE")).thenReturn(3L);
            when(documentCodeResolver.resolveDocumentCode(tenantId)).thenReturn("FPS");
            when(configRepository.findByTenantIdAndDocumentType(tenantId.getValue(), "INVOICE"))
                    .thenReturn(Optional.of(config));

            String result = engine.next(tenantId, "INVOICE", "INV");

            assertThat(result).isEqualTo("CUSTOM-PREFIX-00003");
        }

        private TenantNumberingConfig configWith(String typeCode, String prefixOverride,
                                                   short padding, boolean includeYear) {
            TenantNumberingConfig config = org.mockito.Mockito.mock(TenantNumberingConfig.class);
            when(config.getTypeCode()).thenReturn(typeCode);
            when(config.getPrefixOverride()).thenReturn(prefixOverride);
            when(config.getPadding()).thenReturn(padding);
            when(config.isIncludeYear()).thenReturn(includeYear);
            return config;
        }
    }
}
