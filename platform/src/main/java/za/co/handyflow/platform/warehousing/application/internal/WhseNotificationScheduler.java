package za.co.handyflow.platform.warehousing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.domain.model.WhseBillingInvoice;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;
import za.co.handyflow.platform.warehousing.domain.repository.WhseBillingInvoiceRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseInboundShipmentRepository;
import za.co.handyflow.platform.warehousing.domain.repository.WhseOutboundOrderRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily cross-tenant sweep for three proactive Warehousing alerts, same
 * shape/stagger convention as every other reminder scheduler in this
 * codebase — one cross-tenant query per concern, grouped by tenant in
 * Java (this module's entities carry a raw UUID tenantId, not TenantId
 * directly — TenantId.of(UUID) bridges that), one notification per
 * tenant to its resolved admins, no dedup/idempotency (re-alerting daily
 * on a still-outstanding item is this codebase's established convention):
 * <p>
 * 1. Inbound shipments still EXPECTED/PARTIALLY_RECEIVED past their
 *    expected date — stock a client said was coming hasn't fully arrived.
 * 2. Outbound orders still PENDING/PICKING/PACKED past their requested
 *    ship date — a fulfilment commitment at risk of being missed.
 * 3. Billing invoices SENT/PARTIAL past their due date — overdue
 *    receivables, same shape as every other overdue-invoice sweep in this
 *    codebase.
 * <p>
 * *** ACTION NEEDED IN NotificationType.java *** — see the accompanying
 * Warehousing-NotificationType-patch-instructions.md. This class will not
 * compile until those three constants are added.
 * <p>
 * WHY 08:00? Next open slot in this codebase's 15-minute stagger
 * convention, after CollAgencyNotificationScheduler (07:45).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhseNotificationScheduler {

    private final WhseInboundShipmentRepository inboundShipmentRepository;
    private final WhseOutboundOrderRepository outboundOrderRepository;
    private final WhseBillingInvoiceRepository billingInvoiceRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 0 8 * * *")
    public void checkDeadlines() {
        try {
            checkOverdueInboundShipments();
        } catch (Exception e) {
            log.error("[Warehousing] Overdue inbound shipment sweep failed: {}", e.getMessage(), e);
        }
        try {
            checkOverdueOutboundOrders();
        } catch (Exception e) {
            log.error("[Warehousing] Overdue outbound order sweep failed: {}", e.getMessage(), e);
        }
        try {
            checkOverdueInvoices();
        } catch (Exception e) {
            log.error("[Warehousing] Overdue billing invoice sweep failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public void checkOverdueInboundShipments() {
        LocalDate today = LocalDate.now();
        List<WhseInboundShipment> overdue = inboundShipmentRepository.findOverdueAcrossTenants(today);
        Map<TenantId, List<WhseInboundShipment>> byTenant = overdue.stream()
                .collect(Collectors.groupingBy(s -> TenantId.of(s.getTenantId())));
        byTenant.forEach((tenantId, shipments) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (admins.isEmpty()) {
                log.warn("[Warehousing] {} inbound shipment(s) overdue to receive for tenant={} but no admin recipients could be resolved",
                        shipments.size(), tenantId.getValue());
                return;
            }
            String title = shipments.size() + " inbound shipment(s) overdue to receive";
            String message = shipments.stream()
                    .map(s -> (s.getReferenceNumber() != null ? s.getReferenceNumber() : s.getId().toString())
                            + " — expected " + s.getExpectedDate() + ", status " + s.getStatus())
                    .collect(Collectors.joining(", "));
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId).type(NotificationType.WAREHOUSING_INBOUND_SHIPMENT_OVERDUE)
                    .title(title).message(message)
                    .actionUrl("/warehousing/inbound-shipments").sourceModule("warehousing").recipients(admins).build());
            log.info("[Warehousing] Overdue inbound shipment alert sent tenant={} count={}", tenantId.getValue(), shipments.size());
        });
    }

    @Transactional(readOnly = true)
    public void checkOverdueOutboundOrders() {
        LocalDate today = LocalDate.now();
        List<WhseOutboundOrder> overdue = outboundOrderRepository.findOverdueAcrossTenants(today);
        Map<TenantId, List<WhseOutboundOrder>> byTenant = overdue.stream()
                .collect(Collectors.groupingBy(o -> TenantId.of(o.getTenantId())));
        byTenant.forEach((tenantId, orders) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (admins.isEmpty()) {
                log.warn("[Warehousing] {} outbound order(s) overdue to ship for tenant={} but no admin recipients could be resolved",
                        orders.size(), tenantId.getValue());
                return;
            }
            String title = orders.size() + " outbound order(s) overdue to ship";
            String message = orders.stream()
                    .map(o -> (o.getOrderReference() != null ? o.getOrderReference() : o.getId().toString())
                            + " — requested ship date " + o.getRequestedShipDate() + ", status " + o.getStatus())
                    .collect(Collectors.joining(", "));
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId).type(NotificationType.WAREHOUSING_OUTBOUND_ORDER_OVERDUE)
                    .title(title).message(message)
                    .actionUrl("/warehousing/outbound-orders").sourceModule("warehousing").recipients(admins).build());
            log.info("[Warehousing] Overdue outbound order alert sent tenant={} count={}", tenantId.getValue(), orders.size());
        });
    }

    @Transactional(readOnly = true)
    public void checkOverdueInvoices() {
        LocalDate today = LocalDate.now();
        List<WhseBillingInvoice> overdue = billingInvoiceRepository.findOverdueAcrossTenants(today);
        Map<TenantId, List<WhseBillingInvoice>> byTenant = overdue.stream()
                .collect(Collectors.groupingBy(i -> TenantId.of(i.getTenantId())));
        byTenant.forEach((tenantId, invoices) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (admins.isEmpty()) {
                log.warn("[Warehousing] {} billing invoice(s) overdue for tenant={} but no admin recipients could be resolved",
                        invoices.size(), tenantId.getValue());
                return;
            }
            String title = invoices.size() + " warehousing invoice(s) overdue";
            String message = invoices.stream()
                    .map(i -> i.getInvoiceNumber() + " — R" + i.balance() + " outstanding, due " + i.getDueDate())
                    .collect(Collectors.joining(", "));
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId).type(NotificationType.WAREHOUSING_INVOICE_OVERDUE)
                    .title(title).message(message)
                    .actionUrl("/warehousing/billing-invoices").sourceModule("warehousing").recipients(admins).build());
            log.info("[Warehousing] Overdue billing invoice alert sent tenant={} count={}", tenantId.getValue(), invoices.size());
        });
    }
}
