package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerAccount;
import za.co.handyflow.platform.fuel.domain.model.FuelEnquiry;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerAccountRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelEnquiryRepository;
import za.co.handyflow.platform.fuel.dto.EnquiryDtos.*;
import za.co.handyflow.platform.fuel.dto.OrderRequest;
import za.co.handyflow.platform.fuel.dto.OrderResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelEnquiryServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelEnquiryRepository enquiries;
    @Mock FuelCustomerAccountRepository accounts;
    @Mock FuelOrderService orders;
    @Mock EnquiryNumberGenerator numbers;
    @Mock CrmFacade crm;
    @InjectMocks FuelEnquiryService service;

    FuelCustomerAccount account;
    FuelEnquiry enquiry;

    private EnquiryRequest req(UUID accountId, String name, String litres) {
        return new EnquiryRequest(accountId, name, null, null, "DIESEL", bd(litres), null, "phone", null);
    }

    @BeforeEach
    void setUp() {
        account = FuelCustomerAccount.create(T, UUID.randomUUID(), "MINING_COMPANY", bd("1000000"), 30, null, null);
        enquiry = FuelEnquiry.create(T, "EQ-2026-00001", account.getId(), null, null, null, "DIESEL", bd("5000"), null, "PHONE", null, USER);
        when(accounts.findActiveById(eq(T), any())).thenReturn(Optional.of(account));
        when(enquiries.findOwnedForUpdate(eq(T), any())).thenReturn(Optional.of(enquiry));
        when(enquiries.save(any())).thenAnswer(i -> i.getArgument(0));
        when(numbers.generate(T)).thenReturn("EQ-2026-00002");
        when(crm.findCustomerById(any(), any())).thenReturn(Optional.empty());
    }

    @Test @DisplayName("an enquiry needs a customer account or a name, a fuel type and litres, and a known source")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> service.create(T, req(null, " ", "100"), USER));
        assertThrows(IllegalArgumentException.class, () -> service.create(T, new EnquiryRequest(account.getId(), null, null, null, "DIESEL", bd("100"), null, "CARRIER_PIGEON", null), USER));
        verify(enquiries, never()).save(any());
        EnquiryResponse r = service.create(T, req(null, "Walk-in farmer", "100"), USER);
        assertEquals("EQ-2026-00002", r.enquiryNumber());
        assertEquals("NEW", r.status());
        assertEquals("PHONE", r.source());
    }

    @Test @DisplayName("an account from another company is not found")
    void accountScoping() {
        UUID foreign = UUID.randomUUID();
        when(accounts.findActiveById(T, foreign)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.create(T, req(foreign, null, "100"), USER));
    }

    @Test @DisplayName("quoting raises an ordinary quotation from the enquiry and links it")
    void quotes() {
        OrderResponse q = new OrderResponse(UUID.randomUUID(), "SO-2026-00007", account.getId(), null, null, null, null, "DIESEL", bd("5000"), bd("21.00"),
                bd("105000"), "QUOTE", null, null, null, null, null, null, null, null, null, null, null, null, null);
        when(orders.create(eq(T), any(OrderRequest.class), eq(USER))).thenReturn(q);
        EnquiryResponse r = service.quote(T, enquiry.getId(), new QuoteRequest(null, bd("21.00"), null, null), USER);
        assertEquals("QUOTED", r.status());
        assertEquals("SO-2026-00007", r.quoteOrderNumber());
        ArgumentCaptor<OrderRequest> c = ArgumentCaptor.forClass(OrderRequest.class);
        verify(orders).create(eq(T), c.capture(), eq(USER));
        assertEquals(account.getId(), c.getValue().accountId());
        assertEquals(0, bd("5000").compareTo(c.getValue().litres()));
        assertEquals("EQ-2026-00001", c.getValue().customerReference());
    }

    @Test @DisplayName("an enquiry cannot be quoted twice, and one with no account cannot be quoted at all")
    void quoteGuards() {
        FuelEnquiry noAccount = FuelEnquiry.create(T, "EQ-2026-00003", null, "Walk-in", null, null, "DIESEL", bd("100"), null, "PHONE", null, USER);
        when(enquiries.findOwnedForUpdate(eq(T), eq(noAccount.getId()))).thenReturn(Optional.of(noAccount));
        assertThrows(IllegalStateException.class, () -> service.quote(T, noAccount.getId(), null, USER));
        enquiry.lost("They went elsewhere");
        assertThrows(IllegalStateException.class, () -> service.quote(T, enquiry.getId(), null, USER));
        verifyNoInteractions(orders);
    }

    @Test @DisplayName("lost and cancelled need a reason, are final, and a closed enquiry cannot be edited")
    void closing() {
        assertThrows(IllegalArgumentException.class, () -> service.lost(T, enquiry.getId(), "no"));
        assertEquals("NEW", enquiry.getStatus());
        assertEquals("LOST", service.lost(T, enquiry.getId(), "Price too high").status());
        assertThrows(IllegalStateException.class, () -> service.cancel(T, enquiry.getId(), "Changed my mind"));
        assertThrows(IllegalStateException.class, () -> service.update(T, enquiry.getId(), req(account.getId(), null, "9999")));
    }

    @Test @DisplayName("another company's enquiry is not found")
    void tenantScoping() {
        UUID foreign = UUID.randomUUID();
        when(enquiries.findOwnedForUpdate(T, foreign)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.lost(T, foreign, "reason here"));
    }
}
