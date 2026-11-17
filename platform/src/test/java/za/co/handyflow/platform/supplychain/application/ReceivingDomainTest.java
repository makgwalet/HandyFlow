package za.co.handyflow.platform.supplychain.application;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.GoodsReceivingService;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceivingDomainTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    @Test
    void maxReceivable_isStrictByDefault_andHonoursTolerance() {
        assertThat(GoodsReceivingService.maxReceivable(bd("10"), bd("0")).compareTo(bd("10"))).isZero();
        assertThat(GoodsReceivingService.maxReceivable(bd("100"), bd("5")).compareTo(bd("105"))).isZero();
    }

    @Test
    void grLine_acceptedIsReceivedMinusRejected() {
        ScGrLine l = ScGrLine.createInspected(TENANT, UUID.randomUUID(), UUID.randomUUID(), null, "Widget",
                bd("10"), bd("10"), bd("3"), "Damaged", bd("100"), "DAMAGED", null, null);
        assertThat(l.getQtyAccepted()).isEqualByComparingTo("7");
    }

    @Test
    void poLine_reverseReceived_clearsFullyReceivedAndNeverGoesNegative() {
        ScPoLine l = ScPoLine.create(TENANT, UUID.randomUUID(), null, "Widget", null, bd("10"), bd("100"), bd("15"));
        l.recordReceived(bd("10"));
        assertThat(l.isFullyReceived()).isTrue();
        l.reverseReceived(bd("4"));
        assertThat(l.getQtyReceived()).isEqualByComparingTo("6");
        assertThat(l.isFullyReceived()).isFalse();
        l.reverseReceived(bd("50"));
        assertThat(l.getQtyReceived()).isEqualByComparingTo("0");
    }

    @Test
    void goodsReceipt_canOnlyBeReversedOnce_andNeedsReason() {
        ScGoodsReceipt gr = receipt();
        assertThatThrownBy(() -> gr.reverse(USER, "x")).isInstanceOf(IllegalStateException.class); // still DRAFT
        gr.post();
        assertThatThrownBy(() -> gr.reverse(USER, " ")).isInstanceOf(IllegalArgumentException.class);
        gr.reverse(USER, "Delivered to wrong site");
        assertThat(gr.getStatus()).isEqualTo(ScGoodsReceipt.REVERSED);
        assertThat(gr.getReversalReason()).isEqualTo("Delivered to wrong site");
        assertThatThrownBy(() -> gr.reverse(USER, "again")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void goodsReceipt_cannotBePostedTwice() {
        ScGoodsReceipt gr = receipt();
        gr.post();
        assertThatThrownBy(gr::post).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void po_revertReceiptStatus_goesBackToSentOrApproved() {
        ScPurchaseOrder sent = approvedPo();
        sent.send();
        sent.partiallyReceive();
        sent.revertReceiptStatus(false);
        assertThat(sent.getStatus()).isEqualTo(PoStatus.SENT);

        ScPurchaseOrder neverSent = approvedPo();
        neverSent.fullyReceive();
        neverSent.revertReceiptStatus(true);
        assertThat(neverSent.getStatus()).isEqualTo(PoStatus.PARTIALLY_RECEIVED);
        neverSent.revertReceiptStatus(false);
        assertThat(neverSent.getStatus()).isEqualTo(PoStatus.APPROVED);
    }

    @Test
    void po_revertReceiptStatus_refusedFromInvoicedOrCancelled() {
        ScPurchaseOrder po = approvedPo();
        po.cancel();
        assertThatThrownBy(() -> po.revertReceiptStatus(false)).isInstanceOf(IllegalStateException.class);
    }

    private ScGoodsReceipt receipt() {
        return ScGoodsReceipt.create(TENANT, "GR-00001", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, USER, "Tester", LocalDate.now(), null);
    }

    private ScPurchaseOrder approvedPo() {
        ScPurchaseOrder po = ScPurchaseOrder.create(TENANT, "PO-00001", UUID.randomUUID(), "Acme", null,
                LocalDate.now(), null, "ZAR", null, null, null, USER, "Tester");
        po.submit();
        po.approve(USER, "Boss");
        return po;
    }

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
}
