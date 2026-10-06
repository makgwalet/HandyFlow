package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPricing;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPricingLine;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPricingLineRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderPricingRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.dto.SaveTenderPricingLineRequest;
import za.co.handyflow.platform.compliancetender.dto.SaveTenderPricingSettingsRequest;
import za.co.handyflow.platform.compliancetender.dto.TenderPricingResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pricing rules (ADR-004): the lifecycle lock, first-write creation with the VAT rate captured, tenant scoping and the snapshot hand-off. The arithmetic is in TenderPriceCalculatorTest. */
@ExtendWith(MockitoExtension.class)
class TenderPricingServiceTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderPricingRepository pricingRepository;
    @Mock private TenderPricingLineRepository lineRepository;

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private final UUID tenderId = UUID.randomUUID();

    private TenderPricingService service() {
        return new TenderPricingService(tenderRepository, pricingRepository, lineRepository, new VatRateProvider(new BigDecimal("15.00")));
    }

    /** A tender walked through the real lifecycle to the given status. */
    private static Tender tenderAt(String... path) {
        Tender t = Tender.create(TENANT, "TND-1", "T", null, null, null, null, null, new BigDecimal("500000.00"), null, null, USER);
        for (String s : path) t.transitionTo(s, USER);
        return t;
    }

    private void tenderExists(Tender t) {
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(t));
    }

    private static SaveTenderPricingLineRequest lineReq(String section, String desc, String qty, String cost) {
        return new SaveTenderPricingLineRequest(section, "1.1", desc, "m", new BigDecimal(qty), new BigDecimal(cost));
    }

    private TenderPricingLine existingLine(UUID tender) {
        return TenderPricingLine.create(TENANT, tender, "Roads", null, "Kerb", "m", new BigDecimal("2"), new BigDecimal("10"), 1, USER);
    }

    // ---- lifecycle lock -------------------------------------------------------------------------------

    @Test
    @DisplayName("pricing can be changed from DRAFT up to READY_TO_SUBMIT")
    void editableUntilReadyToSubmit() {
        String[][] paths = {{}, {"IN_PREPARATION"}, {"IN_PREPARATION", "INTERNAL_REVIEW"}, {"IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT"}};
        for (String[] path : paths) {
            Tender t = tenderAt(path);
            tenderExists(t);
            when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.of(TenderPricing.create(TENANT, tenderId, new BigDecimal("15.00"), USER)));
            when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

            TenderPricingResponse r = service().addLine(TENANT, tenderId, lineReq("A", "x", "1", "1"), USER);

            assertThat(r.editable()).isTrue();
        }
    }

    @Test
    @DisplayName("from SUBMITTED onwards, and when WITHDRAWN, every write is refused as a conflict and nothing is saved")
    void lockedAfterSubmission() {
        String[][] paths = {
                {"IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED"},
                {"IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED", "CLARIFICATION"},
                {"IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED", "SHORTLISTED"},
                {"IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED", "SHORTLISTED", "NEGOTIATION"},
                {"IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED", "SHORTLISTED", "AWARDED"},
                {"IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED", "UNSUCCESSFUL"},
                {"WITHDRAWN"}};
        UUID lineId = UUID.randomUUID();
        for (String[] path : paths) {
            Tender t = tenderAt(path);
            tenderExists(t);
            TenderPricingLine line = existingLine(tenderId);
            when(lineRepository.findByIdForTenant(TENANT, lineId)).thenReturn(Optional.of(line));

            String status = path[path.length - 1];
            assertThatThrownBy(() -> service().saveSettings(TENANT, tenderId, new SaveTenderPricingSettingsRequest(null, null, null, null, null), USER))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(status);
            assertThatThrownBy(() -> service().addLine(TENANT, tenderId, lineReq("A", "x", "1", "1"), USER))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("locked");
            assertThatThrownBy(() -> service().updateLine(TENANT, lineId, lineReq("A", "x", "1", "1"), USER))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service().deleteLine(TENANT, lineId))
                    .isInstanceOf(IllegalStateException.class);
        }
        verify(lineRepository, never()).save(any());
        verify(lineRepository, never()).delete(any());
        verify(pricingRepository, never()).save(any());
        verify(pricingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a locked tender can still be READ, and says it is not editable")
    void lockedCanStillBeRead() {
        tenderExists(tenderAt("IN_PREPARATION", "INTERNAL_REVIEW", "READY_TO_SUBMIT", "SUBMITTED"));
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

        TenderPricingResponse r = service().getPricing(TENANT, tenderId);

        assertThat(r.editable()).isFalse();
        assertThat(r.tenderStatus()).isEqualTo("SUBMITTED");
    }

    // ---- first write ----------------------------------------------------------------------------------

    @Test
    @DisplayName("an unpriced tender reads as defaults (not configured, no markups, VAT on at the current rate) and nothing is written by reading")
    void unpricedReadsAsDefaults() {
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

        TenderPricingResponse r = service().getPricing(TENANT, tenderId);

        assertThat(r.configured()).isFalse();
        assertThat(r.settings().vatApplies()).isTrue();
        assertThat(r.settings().vatRatePct()).isEqualByComparingTo("15.00");
        assertThat(r.breakdown().priceInclVat()).isEqualByComparingTo("0.00");
        assertThat(r.estimatedValue()).isEqualByComparingTo("500000.00");
        verify(pricingRepository, never()).save(any());
        verify(pricingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the first write creates the pricing row, capturing the VAT rate in force, and later writes reuse it")
    void firstWriteCapturesVatRate() {
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(pricingRepository.saveAndFlush(any(TenderPricing.class))).thenAnswer(i -> i.getArgument(0));
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(lineRepository.maxSortOrder(TENANT, tenderId)).thenReturn(0);

        service().addLine(TENANT, tenderId, lineReq("A", "x", "1", "1"), USER);

        ArgumentCaptor<TenderPricing> created = ArgumentCaptor.forClass(TenderPricing.class);
        verify(pricingRepository).saveAndFlush(created.capture());
        assertThat(created.getValue().getVatRatePct()).isEqualByComparingTo("15.00");
        assertThat(created.getValue().getTenderId()).isEqualTo(tenderId);
        assertThat(created.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("an existing pricing row keeps its own captured VAT rate even when the platform rate has changed")
    void capturedVatRateIsKept() {
        tenderExists(tenderAt());
        TenderPricing existing = TenderPricing.create(TENANT, tenderId, new BigDecimal("14.00"), USER);
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.of(existing));
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(existingLine(tenderId)));

        TenderPricingResponse r = service().getPricing(TENANT, tenderId);

        assertThat(r.settings().vatRatePct()).isEqualByComparingTo("14.00");
        assertThat(r.breakdown().vat()).isEqualByComparingTo("2.80"); // 14% of 20.00, not 15%
    }

    @Test
    @DisplayName("two people creating the pricing at once: the loser gets a clear conflict, not a raw database error")
    void creationRaceIsAConflict() {
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(pricingRepository.saveAndFlush(any(TenderPricing.class))).thenThrow(new DataIntegrityViolationException("uq_tender_pricing_tender"));

        assertThatThrownBy(() -> service().addLine(TENANT, tenderId, lineReq("A", "x", "1", "1"), USER))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("just created");
        verify(lineRepository, never()).save(any());
    }

    // ---- settings -------------------------------------------------------------------------------------

    @Test
    @DisplayName("settings are saved and the breakdown reflects them straight away")
    void settingsSaved() {
        tenderExists(tenderAt("IN_PREPARATION"));
        TenderPricing pricing = TenderPricing.create(TENANT, tenderId, new BigDecimal("15.00"), USER);
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.of(pricing));
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(
                TenderPricingLine.create(TENANT, tenderId, "A", null, "x", null, new BigDecimal("1"), new BigDecimal("100"), 1, USER)));

        TenderPricingResponse r = service().saveSettings(TENANT, tenderId,
                new SaveTenderPricingSettingsRequest(new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("8"), false, "  note  "), USER);

        assertThat(r.configured()).isTrue();
        assertThat(r.settings().vatApplies()).isFalse();
        assertThat(r.settings().notes()).isEqualTo("note");
        assertThat(r.breakdown().priceExVat()).isEqualByComparingTo("124.20");
        assertThat(r.breakdown().vat()).isEqualByComparingTo("0.00");
        verify(pricingRepository).save(pricing);
    }

    @Test
    @DisplayName("a missing vatApplies means VAT applies; missing percentages mean zero")
    void settingsDefaults() {
        tenderExists(tenderAt());
        TenderPricing pricing = TenderPricing.create(TENANT, tenderId, new BigDecimal("15.00"), USER);
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.of(pricing));
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

        TenderPricingResponse r = service().saveSettings(TENANT, tenderId, new SaveTenderPricingSettingsRequest(null, null, null, null, null), USER);

        assertThat(r.settings().vatApplies()).isTrue();
        assertThat(r.settings().overheadPct()).isEqualByComparingTo("0");
        assertThat(r.settings().notes()).isNull();
    }

    @Test
    @DisplayName("percentages outside 0 to 100 are refused by the entity itself, not only by request validation")
    void percentagesBounded() {
        TenderPricing p = TenderPricing.create(TENANT, tenderId, new BigDecimal("15.00"), USER);
        assertThatThrownBy(() -> p.update(new BigDecimal("101"), null, null, true, null, USER)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("overheadPct");
        assertThatThrownBy(() -> p.update(null, new BigDecimal("-1"), null, true, null, USER)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("contingencyPct");
        assertThatThrownBy(() -> p.update(null, null, new BigDecimal("100.01"), true, null, USER)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("profitPct");
        p.update(new BigDecimal("100"), new BigDecimal("0"), new BigDecimal("100"), true, null, USER);
        assertThat(p.getOverheadPct()).isEqualByComparingTo("100");
    }

    // ---- lines ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("a new line goes to the end of the schedule, with a blank section becoming General")
    void lineAppended() {
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.of(TenderPricing.create(TENANT, tenderId, new BigDecimal("15.00"), USER)));
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        when(lineRepository.maxSortOrder(TENANT, tenderId)).thenReturn(7);

        service().addLine(TENANT, tenderId, lineReq("   ", "Kerb", "2", "10"), USER);

        ArgumentCaptor<TenderPricingLine> saved = ArgumentCaptor.forClass(TenderPricingLine.class);
        verify(lineRepository).save(saved.capture());
        assertThat(saved.getValue().getSortOrder()).isEqualTo(8);
        assertThat(saved.getValue().getSection()).isEqualTo("General");
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getValue().getTenderId()).isEqualTo(tenderId);
    }

    @Test
    @DisplayName("a line cannot have a negative quantity or cost, or a blank description")
    void lineValidation() {
        assertThatThrownBy(() -> TenderPricingLine.create(TENANT, tenderId, "A", null, "x", null, new BigDecimal("-1"), BigDecimal.ONE, 1, USER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenderPricingLine.create(TENANT, tenderId, "A", null, "x", null, BigDecimal.ONE, new BigDecimal("-0.01"), 1, USER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenderPricingLine.create(TENANT, tenderId, "A", null, "  ", null, BigDecimal.ONE, BigDecimal.ONE, 1, USER)).isInstanceOf(IllegalArgumentException.class);
        // more decimals than the database keeps are refused, never silently rounded (trailing zeros do not count)
        assertThatThrownBy(() -> TenderPricingLine.create(TENANT, tenderId, "A", null, "x", null, BigDecimal.ONE, new BigDecimal("0.125"), 1, USER)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unitCost");
        assertThatThrownBy(() -> TenderPricingLine.create(TENANT, tenderId, "A", null, "x", null, new BigDecimal("1.2345"), BigDecimal.ONE, 1, USER)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
        TenderPricingLine ok = TenderPricingLine.create(TENANT, tenderId, "A", null, "x", null, new BigDecimal("1.2300"), new BigDecimal("0.500"), 1, USER);
        assertThat(ok.getUnitCost()).isEqualByComparingTo("0.5");
        TenderPricingLine zero = TenderPricingLine.create(TENANT, tenderId, "A", null, "x", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, USER);
        assertThat(zero.getQuantity()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("updating a line changes it in place and prices the tender it belongs to")
    void lineUpdated() {
        TenderPricingLine line = existingLine(tenderId);
        UUID lineId = line.getId();
        when(lineRepository.findByIdForTenant(TENANT, lineId)).thenReturn(Optional.of(line));
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.of(TenderPricing.create(TENANT, tenderId, new BigDecimal("15.00"), USER)));
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(line));

        TenderPricingResponse r = service().updateLine(TENANT, lineId, lineReq("Drainage", "Pipe", "3", "100"), USER);

        assertThat(line.getSection()).isEqualTo("Drainage");
        assertThat(line.getQuantity()).isEqualByComparingTo("3");
        assertThat(r.breakdown().directCost()).isEqualByComparingTo("300.00");
        verify(lineRepository).save(line);
    }

    @Test
    @DisplayName("a line from another tenant, or one that does not exist, is a 404 and changes nothing")
    void lineNotFound() {
        UUID lineId = UUID.randomUUID();
        when(lineRepository.findByIdForTenant(TENANT, lineId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateLine(TENANT, lineId, lineReq("A", "x", "1", "1"), USER)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().deleteLine(TENANT, lineId)).isInstanceOf(ResourceNotFoundException.class);
        verify(lineRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a tender from another tenant, or one that does not exist, is a 404")
    void tenderNotFound() {
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getPricing(TENANT, tenderId)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().addLine(TENANT, tenderId, lineReq("A", "x", "1", "1"), USER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleting a line removes it and returns the re-priced schedule")
    void lineDeleted() {
        TenderPricingLine line = existingLine(tenderId);
        when(lineRepository.findByIdForTenant(TENANT, line.getId())).thenReturn(Optional.of(line));
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());

        TenderPricingResponse r = service().deleteLine(TENANT, line.getId());

        verify(lineRepository).delete(line);
        assertThat(r.lines()).isEmpty();
    }

    @Test
    @DisplayName("each line carries its own total, and the response lists section subtotals")
    void responseShape() {
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(existingLine(tenderId)));

        TenderPricingResponse r = service().getPricing(TENANT, tenderId);

        assertThat(r.lines().get(0).lineTotal()).isEqualByComparingTo("20.00");
        assertThat(r.breakdown().sections()).hasSize(1);
        assertThat(r.breakdown().sections().get(0).subtotal()).isEqualByComparingTo("20.00");
    }

    // ---- snapshot -------------------------------------------------------------------------------------

    @Test
    @DisplayName("snapshotOf is null for a tender never priced, and present once settings were saved or a line exists")
    void snapshotOf() {
        tenderExists(tenderAt());
        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of());
        assertThat(service().snapshotOf(TENANT, tenderId)).isNull();

        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.of(TenderPricing.create(TENANT, tenderId, new BigDecimal("15.00"), USER)));
        assertThat(service().snapshotOf(TENANT, tenderId)).isNotNull();

        when(pricingRepository.findByTender(TENANT, tenderId)).thenReturn(Optional.empty());
        when(lineRepository.findByTender(TENANT, tenderId)).thenReturn(List.of(existingLine(tenderId)));
        assertThat(service().snapshotOf(TENANT, tenderId)).isNotNull();
    }
}
