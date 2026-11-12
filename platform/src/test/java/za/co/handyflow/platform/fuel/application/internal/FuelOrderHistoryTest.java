package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelOrderAcceptance;
import za.co.handyflow.platform.fuel.domain.model.FuelOrderRevision;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.fuel.domain.repository.FuelOrderAcceptanceRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelOrderRevisionRepository;
import za.co.handyflow.platform.fuel.dto.EnquiryDtos.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelOrderHistoryTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelOrderRevisionRepository revisions;
    @Mock FuelOrderAcceptanceRepository acceptances;
    @InjectMocks FuelOrderHistory history;

    FuelSalesOrder order;

    @BeforeEach
    void setUp() {
        order = FuelSalesOrder.create(T, "SO-2026-00001", UUID.randomUUID(), UUID.randomUUID(), null, "DIESEL", bd("1000"), bd("20.00"), null, null, "REF", null, null);
    }

    @Test @DisplayName("each revision takes the next number and snapshots the order's current terms")
    void numbersRevisions() {
        when(revisions.latestNo(T, order.getId())).thenReturn(0, 1);
        history.recordRevision(T, order, null);
        order.revise(null, "DIESEL", bd("2000"), bd("21.00"), null, null, "REF", null);
        history.recordRevision(T, order, null);
        verify(revisions).save(argThat((FuelOrderRevision r) -> r.getRevisionNo() == 1 && r.getLitres().compareTo(bd("1000")) == 0));
        verify(revisions).save(argThat((FuelOrderRevision r) -> r.getRevisionNo() == 2 && r.getLitres().compareTo(bd("2000")) == 0 && r.getPricePerLitre().compareTo(bd("21.00")) == 0));
    }

    @Test @DisplayName("the history shows each version's price change against the one before")
    void priceChange() {
        FuelOrderRevision r1 = FuelOrderRevision.snapshot(order, 1, null);
        order.revise(null, "DIESEL", bd("1000"), bd("21.50"), null, null, "REF", null);
        FuelOrderRevision r2 = FuelOrderRevision.snapshot(order, 2, null);
        when(revisions.findByOrder(T, order.getId())).thenReturn(List.of(r1, r2));
        when(acceptances.findByOrder(T, order.getId())).thenReturn(Optional.empty());
        OrderHistory h = history.history(T, order.getId());
        assertNull(h.revisions().get(0).priceChange());
        assertEquals(0, bd("1.50").compareTo(h.revisions().get(1).priceChange()));
        assertNull(h.acceptance());
    }

    @Test @DisplayName("an acceptance is tied to the latest revision, and a second one is refused")
    void acceptance() {
        when(revisions.latestNo(T, order.getId())).thenReturn(3);
        when(acceptances.findByOrder(T, order.getId())).thenReturn(Optional.empty());
        history.recordAcceptance(T, order, new AcceptanceRequest("Thabo Nkosi", "email", "RE: quote", null), TODAY, null);
        verify(acceptances).save(argThat((FuelOrderAcceptance a) -> a.getRevisionNo() == 3 && a.getChannel().equals("EMAIL") && a.getAcceptedOn().equals(TODAY)));
        when(acceptances.findByOrder(T, order.getId())).thenReturn(Optional.of(mock(FuelOrderAcceptance.class)));
        assertThrows(ConflictException.class, () -> history.recordAcceptance(T, order, new AcceptanceRequest("Thabo Nkosi", "EMAIL", null, null), TODAY, null));
    }

    @Test @DisplayName("an acceptance needs a name, a known channel and a date that is not in the future")
    void acceptanceRules() {
        UUID o = order.getId();
        assertThrows(IllegalArgumentException.class, () -> FuelOrderAcceptance.record(T, o, 1, " ", "EMAIL", null, null, TODAY, null));
        assertThrows(IllegalArgumentException.class, () -> FuelOrderAcceptance.record(T, o, 1, "Thabo", "SMOKE", null, null, TODAY, null));
        assertThrows(IllegalArgumentException.class, () -> FuelOrderAcceptance.record(T, o, 1, "Thabo", "EMAIL", null, TODAY.plusDays(1), TODAY, null));
        assertEquals(TODAY, FuelOrderAcceptance.record(T, o, 1, "Thabo", "phone", " ", null, TODAY, null).getAcceptedOn());
    }

    @Test @DisplayName("an order with no revision yet still accepts against revision 1")
    void firstRevisionFloor() {
        when(revisions.latestNo(T, order.getId())).thenReturn(0);
        when(acceptances.findByOrder(T, order.getId())).thenReturn(Optional.empty());
        history.recordAcceptance(T, order, new AcceptanceRequest("Thabo Nkosi", "PHONE", null, null), TODAY, null);
        verify(acceptances).save(argThat((FuelOrderAcceptance a) -> a.getRevisionNo() == 1));
    }
}
