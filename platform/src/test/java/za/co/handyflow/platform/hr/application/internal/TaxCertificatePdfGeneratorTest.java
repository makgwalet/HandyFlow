package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos.Certificate;
import za.co.handyflow.platform.hr.dto.TaxCertificateDtos.Line;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCertificatePdfGeneratorTest {

    private final TaxCertificatePdfGenerator generator = new TaxCertificatePdfGenerator();

    private Certificate certificate(String status) {
        return new Certificate(UUID.randomUUID(), 2027, "ANNUAL", UUID.randomUUID(), "EMP-1", "Jane Dlamini", "8001015009087", "0123456789",
                "712345678920270200000000000001", "IRP5", 1, status, status, LocalDate.of(2026, 3, 1), LocalDate.of(2027, 2, 28), 12, Instant.now(), null, null, null,
                List.of(new Line("3601", "Income: salary or wages", new BigDecimal("360000.00")), new Line("4001", "Pension fund contributions (employee)", new BigDecimal("5000.00")),
                        new Line("4102", "PAYE", new BigDecimal("90123.45"))), List.of());
    }

    @Test
    void aCertificateBecomesAPdf() {
        byte[] pdf = generator.generate(certificate("ISSUED"), "Acme (Pty) Ltd", "7123456789");
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void aCancelledCertificateIsStillMadeButIsMarked() {
        assertThat(generator.generate(certificate("CANCELLED"), null, null).length).isGreaterThan(1000);
    }

    @Test
    void codesFallInTheirBlocks() {
        assertThat(TaxCertificatePdfGenerator.section("3601")).isEqualTo("Income");
        assertThat(TaxCertificatePdfGenerator.section("3810")).isEqualTo("Income");
        assertThat(TaxCertificatePdfGenerator.section("4001")).isEqualTo("Deductions and contributions");
        assertThat(TaxCertificatePdfGenerator.section("4474")).isEqualTo("Deductions and contributions");
        assertThat(TaxCertificatePdfGenerator.section("4102")).isEqualTo("Tax, UIF and SDL");
        assertThat(TaxCertificatePdfGenerator.section("4142")).isEqualTo("Tax, UIF and SDL");
    }
}
