package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PricingCalculatorServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID PERSON = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final LocalDate ON = LocalDate.of(2027, 3, 1);

    @Mock CompliancePriceBookVersionRepository versions;
    @Mock CompliancePriceBookLineRepository lines;
    @Mock CompliancePriceBookRateRepository rates;

    private PricingCalculatorService calculator;
    private CompliancePriceBookVersion book;

    @BeforeEach
    void setUp() {
        calculator = new PricingCalculatorService(versions, lines, rates, new VatRateProvider(new BigDecimal("15.00")));
        book = CompliancePriceBookVersion.draft(TENANT, 1, PERSON);
        book.edit("2027", null);
        book.submit(PERSON, 1, 0);
        book.approve(OTHER, LocalDate.of(2027, 1, 1), null, null);
        lenient().when(versions.findAllForTenant(TENANT)).thenReturn(List.of(book));
        lenient().when(lines.findForVersions(TENANT, List.of(book.getId()))).thenReturn(List.of(
                line("CS-CIPC-AR", "fixed", "2000", null, null),
                line("CS-OHS", "per_site", "3000", "800", null),
                line("CS-ADVISORY", "hourly", null, null, "senior")));
        lenient().when(rates.findForVersions(TENANT, List.of(book.getId()))).thenReturn(List.of(
                CompliancePriceBookRate.of(TENANT, book.getId(), "SENIOR", new BigDecimal("900"), new BigDecimal("400"), 0)));
    }

    private CompliancePriceBookLine line(String code, String model, String base, String unit, String grade) {
        return CompliancePriceBookLine.of(TENANT, book.getId(), new CompliancePriceBookLine.Input(code, model, base == null ? null : new BigDecimal(base),
                unit == null ? null : new BigDecimal(unit), grade, null, null, null, null), 0);
    }

    private static CalculateRequest req(String code, String units, String hours, String complexity, String discount) {
        return new CalculateRequest(code, ON, units == null ? null : new BigDecimal(units), hours == null ? null : new BigDecimal(hours),
                complexity == null ? null : new BigDecimal(complexity), false, null, null, discount == null ? null : new BigDecimal(discount));
    }

    @Test @DisplayName("a fixed price with complexity and VAT, from the book in force")
    void fixed() {
        CalculateResponse r = calculator.calculate(TENANT, req("cs-cipc-ar", null, null, "20", null));
        assertThat(r.priceBookVersion()).isEqualTo(1);
        assertThat(r.professional()).isEqualByComparingTo("2400.00");
        assertThat(r.vat()).isEqualByComparingTo("360.00");
        assertThat(r.total()).isEqualByComparingTo("2760.00");
    }

    @Test @DisplayName("a per-site price uses the unit rate")
    void perSite() {
        assertThat(calculator.calculate(TENANT, req("CS-OHS", "3", null, null, null)).professional()).isEqualByComparingTo("5400.00");
    }

    @Test @DisplayName("an hourly price uses the grade's rate from the same book")
    void hourly() {
        assertThat(calculator.calculate(TENANT, req("CS-ADVISORY", null, "2", null, null)).professional()).isEqualByComparingTo("1800.00");
    }

    @Test @DisplayName("a discount returns who may agree it")
    void discount() {
        assertThat(calculator.calculate(TENANT, req("CS-CIPC-AR", null, null, null, "12")).discountApprover()).isEqualTo("PRACTICE_OWNER");
    }

    @Test @DisplayName("a service with no price, or a date with no approved book, is refused")
    void refused() {
        assertThatThrownBy(() -> calculator.calculate(TENANT, req("CS-OTHER", null, null, null, null))).isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> calculator.calculate(TENANT, new CalculateRequest("CS-CIPC-AR", LocalDate.of(2026, 6, 1), null, null, null, false, null, null, null)))
                .hasMessageContaining("No approved price book");
        assertThatThrownBy(() -> calculator.calculate(TENANT, req("CS-ADVISORY", null, null, null, null))).isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("margin gives cost, the price for a target margin, and the margin of a quote")
    void margin() {
        MarginResponse m = calculator.margin(TENANT, new MarginRequest("senior", new BigDecimal("4"), null, null, new BigDecimal("40"), new BigDecimal("50"), new BigDecimal("2700"), ON));
        assertThat(m.cost()).isEqualByComparingTo("1600.00");
        assertThat(m.recommendedPrice()).isEqualByComparingTo("2666.67");
        assertThat(m.roundedPrice()).isEqualByComparingTo("2700.00");
        assertThat(m.marginAtProposed()).isEqualByComparingTo("40.74");
        assertThatThrownBy(() -> calculator.margin(TENANT, new MarginRequest("junior", BigDecimal.ONE, null, null, BigDecimal.TEN, null, null, ON)))
                .hasMessageContaining("no cost rate");
    }
}
