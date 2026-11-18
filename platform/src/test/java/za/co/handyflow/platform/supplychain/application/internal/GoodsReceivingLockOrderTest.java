package za.co.handyflow.platform.supplychain.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.supplychain.domain.model.ScInventory;
import za.co.handyflow.platform.supplychain.domain.model.ScPoLine;
import za.co.handyflow.platform.supplychain.domain.model.ScStockMovement;
import za.co.handyflow.platform.supplychain.domain.repository.ScInventoryRepository;
import za.co.handyflow.platform.supplychain.dto.PostGrLineRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;

/** Two receipts that touch the same items in opposite line order must lock inventory rows in the same order. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsReceivingLockOrderTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID LOC = UUID.randomUUID();
    private static final UUID ITEM_A = new UUID(0, 1), ITEM_B = new UUID(0, 2), ITEM_C = new UUID(0, 3);

    @Mock ScInventoryRepository inventoryRepo;
    @InjectMocks GoodsReceivingService service;

    private static ScPoLine poLine(UUID item) {
        return ScPoLine.create(TENANT, UUID.randomUUID(), item, "Item", null, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO);
    }

    private static PostGrLineRequest line(ScPoLine l) {
        return new PostGrLineRequest(l.getId(), null, "Item", BigDecimal.ONE, null, null, null, null, null, null);
    }

    @Test
    void linesAreSortedByCatalogueItem_whateverOrderTheyArriveIn() {
        ScPoLine a = poLine(ITEM_A), b = poLine(ITEM_B), c = poLine(ITEM_C);
        List<ScPoLine> po = List.of(a, b, c);
        var first = service.sortedForLocking(List.of(line(c), line(a), line(b)), po);
        var second = service.sortedForLocking(List.of(line(b), line(c), line(a)), po);
        assertThat(first).extracting(PostGrLineRequest::poLineId).containsExactly(a.getId(), b.getId(), c.getId());
        assertThat(second).extracting(PostGrLineRequest::poLineId).isEqualTo(first.stream().map(PostGrLineRequest::poLineId).toList());
    }

    @Test
    void anUnresolvableLineSortsLast_andIsLeftToFailWithItsOwnMessage() {
        ScPoLine a = poLine(ITEM_A);
        PostGrLineRequest unknown = new PostGrLineRequest(UUID.randomUUID(), null, "Ghost", BigDecimal.ONE, null, null, null, null, null, null);
        var sorted = service.sortedForLocking(List.of(unknown, line(a)), List.of(a));
        assertThat(sorted.get(0).poLineId()).isEqualTo(a.getId());
        assertThat(sorted.get(1)).isSameAs(unknown);
    }

    @Test
    void reversalLocksInTheSameOrderPostingDoes() {
        ScInventory invC = ScInventory.create(TENANT, LOC, ITEM_C), invA = ScInventory.create(TENANT, LOC, ITEM_A), invB = ScInventory.create(TENANT, LOC, ITEM_B);
        when(inventoryRepo.findAllById(anyIterable())).thenReturn(List.of(invC, invA, invB));
        ScStockMovement mc = ScStockMovement.record(TENANT, invC, "PURCHASE", BigDecimal.ONE, BigDecimal.ONE, "GOODS_RECEIPT", UUID.randomUUID(), "GRN-1", null, "t");
        ScStockMovement ma = ScStockMovement.record(TENANT, invA, "PURCHASE", BigDecimal.ONE, BigDecimal.ONE, "GOODS_RECEIPT", UUID.randomUUID(), "GRN-1", null, "t");
        ScStockMovement mb = ScStockMovement.record(TENANT, invB, "PURCHASE", BigDecimal.ONE, BigDecimal.ONE, "GOODS_RECEIPT", UUID.randomUUID(), "GRN-1", null, "t");
        ScStockMovement offset = ScStockMovement.record(TENANT, invA, "PURCHASE_REVERSAL", BigDecimal.ONE.negate(), BigDecimal.ONE, "GR_REVERSAL", UUID.randomUUID(), "GRN-1", null, "t");
        var sorted = service.movementsInLockOrder(TENANT, List.of(mc, offset, ma, mb));
        assertThat(sorted).containsExactly(ma, mb, mc);          // negative movements are not reversed again
    }
}
