package za.co.handyflow.platform.debtcollection.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.debtcollection.application.internal.WebhookPayloads.PaymentKind;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationStatus;
import za.co.handyflow.platform.debtcollection.domain.model.PaymentSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookPayloadsTest {

    private final ObjectMapper m = new ObjectMapper();

    @Test
    void classifiesPaymentStatuses() {
        assertEquals(PaymentKind.PAYMENT, WebhookPayloads.classify("complete"));
        assertEquals(PaymentKind.PAYMENT, WebhookPayloads.classify("PAID"));
        assertEquals(PaymentKind.REVERSAL, WebhookPayloads.classify("chargeback"));
        assertEquals(PaymentKind.REVERSAL, WebhookPayloads.classify("BOUNCED"));
        assertEquals(PaymentKind.IGNORE, WebhookPayloads.classify("PENDING"));
        assertEquals(PaymentKind.IGNORE, WebhookPayloads.classify(null));
    }

    @Test
    void extractsCaseNumberFromFreeText() {
        assertEquals("DC-00042", WebhookPayloads.extractCaseNumber("EFT ref dc-00042 thanks"));
        assertEquals("DC-123456", WebhookPayloads.extractCaseNumber("DC-123456"));
        assertNull(WebhookPayloads.extractCaseNumber("INV-0042"));
        assertNull(WebhookPayloads.extractCaseNumber(null));
    }

    @Test
    void parsesAFullPaymentEvent() throws Exception {
        var e = WebhookPayloads.payment(m.readTree("{\"reference\":\"BANK1\",\"amount\":\"1500.5\",\"currency\":\"zar\","
                + "\"date\":\"2026-10-10\",\"payerReference\":\"pay DC-00042 now\",\"source\":\"eft\"}"));
        assertEquals("BANK1", e.reference());
        assertEquals(new BigDecimal("1500.50"), e.amount());
        assertEquals("ZAR", e.currency());
        assertEquals(LocalDate.of(2026, 10, 10), e.date());
        assertEquals("DC-00042", e.caseNumber());
        assertEquals(PaymentSource.EFT, e.source());
        assertEquals(PaymentKind.PAYMENT, e.kind()); // no status => treated as COMPLETE
    }

    @Test
    void explicitCaseNumberWinsAndUnknownSourceFallsBackToGateway() throws Exception {
        var e = WebhookPayloads.payment(m.readTree("{\"reference\":\"R\",\"amount\":10,\"caseNumber\":\"dc-00007\","
                + "\"payerReference\":\"DC-00009\",\"source\":\"carrier pigeon\"}"));
        assertEquals("DC-00007", e.caseNumber());
        assertEquals(PaymentSource.GATEWAY, e.source());
    }

    @Test
    void rejectsMalformedPayments() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> WebhookPayloads.payment(m.readTree("{\"amount\":\"5\"}")));
        assertThrows(IllegalArgumentException.class, () -> WebhookPayloads.payment(m.readTree("{\"reference\":\"R\",\"amount\":\"abc\"}")));
        assertThrows(IllegalArgumentException.class, () -> WebhookPayloads.payment(m.readTree("{\"reference\":\"R\",\"amount\":\"-5\"}")));
        assertThrows(IllegalArgumentException.class, () -> WebhookPayloads.payment(m.readTree("{\"reference\":\"R\",\"amount\":\"5\",\"date\":\"10/10/2026\"}")));
    }

    @Test
    void deliveryStatusMapping() throws Exception {
        var ok = WebhookPayloads.delivery(m.readTree("{\"messageId\":\"abc\",\"status\":\"delivered\",\"timestamp\":\"2026-10-10T08:31:00Z\"}"));
        assertEquals(CommunicationStatus.DELIVERED, ok.status());
        assertEquals(java.time.Instant.parse("2026-10-10T08:31:00Z"), ok.at());
        assertNull(WebhookPayloads.delivery(m.readTree("{\"messageId\":\"abc\",\"status\":\"BLOCKED\"}")).status()); // not a provider status
        assertNull(WebhookPayloads.delivery(m.readTree("{\"messageId\":\"abc\",\"status\":\"whatever\"}")).status());
        assertThrows(IllegalArgumentException.class, () -> WebhookPayloads.delivery(m.readTree("{\"status\":\"SENT\"}")));
    }

    @Test
    void inboundRequiresChannelFromAndBody() throws Exception {
        var e = WebhookPayloads.inbound(m.readTree("{\"channel\":\"sms\",\"from\":\"+27821234567\",\"body\":\"STOP\"}"));
        assertEquals(CommunicationChannel.SMS, e.channel());
        assertThrows(IllegalArgumentException.class, () -> WebhookPayloads.inbound(m.readTree("{\"channel\":\"fax\",\"from\":\"x\",\"body\":\"y\"}")));
        assertThrows(IllegalArgumentException.class, () -> WebhookPayloads.inbound(m.readTree("{\"channel\":\"SMS\",\"body\":\"y\"}")));
    }
}
