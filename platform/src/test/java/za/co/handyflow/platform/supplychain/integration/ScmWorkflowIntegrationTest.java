package za.co.handyflow.platform.supplychain.integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.BudgetService;
import za.co.handyflow.platform.supplychain.application.internal.GoodsReceivingService;
import za.co.handyflow.platform.supplychain.application.internal.PoAmendmentService;
import za.co.handyflow.platform.supplychain.application.internal.QuarantineService;
import za.co.handyflow.platform.supplychain.application.internal.ReplenishmentService;
import za.co.handyflow.platform.supplychain.application.internal.ScmAnalyticsService;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionService;
import za.co.handyflow.platform.supplychain.domain.repository.ScPurchaseOrderRepository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.supplychain.integration.ScmIntegrationSupport.insert;
import static za.co.handyflow.platform.supplychain.integration.ScmIntegrationSupport.row;

/**
 * The races and refusals the SCM audit asked to see proven against a real database:
 * concurrent PO approval vs a hard-stop budget, amendment withdrawal when the approval engine is unavailable, goods-receipt
 * reversal after a partial invoice, and a requisition submitted twice at once. Needs Docker; skipped, not failed, without it.
 * The approval engine is a mock, so only this module's own locking and rules are under test.
 *
 * NOT RUN where it was written (no Docker or Maven there): run it with `mvn -Dtest=ScmWorkflowIntegrationTest test`.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ScmWorkflowIntegrationTest {

    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @MockBean ApprovalFacade approvalFacade;
    @Autowired JdbcTemplate jdbc;
    @Autowired BudgetService budgets;
    @Autowired RequisitionService requisitions;
    @Autowired PoAmendmentService amendments;
    @Autowired GoodsReceivingService receiving;
    @Autowired ScPurchaseOrderRepository poRepo;
    @Autowired QuarantineService quarantine;
    @Autowired ReplenishmentService replenishment;
    @Autowired ScmAnalyticsService analytics;

    final UUID tenantId = UUID.randomUUID();
    final TenantId tenant = TenantId.of(tenantId);
    final UUID user = UUID.randomUUID();
    final UUID supplier = UUID.randomUUID();

    // ── Seed helpers ─────────────────────────────────────────────────────────

    UUID purchaseOrder(String number, BigDecimal total, String project) {
        UUID id = UUID.randomUUID();
        insert(jdbc, "sc_purchase_orders", row("id", id, "tenant_id", tenantId, "order_number", number, "supplier_id", supplier, "supplier_name", "Alpha",
                "order_date", Date.valueOf(LocalDate.of(2026, 11, 18)), "subtotal", total, "vat_amount", BigDecimal.ZERO, "total_amount", total,
                "status", "APPROVED", "project_ref", project));
        return id;
    }

    void hardStopBudget(String project, int amount) {
        insert(jdbc, "sc_budgets", row("id", UUID.randomUUID(), "tenant_id", tenantId, "name", "Project " + project, "scope_type", "PROJECT", "scope_value", project,
                "period_start", Date.valueOf("2026-01-01"), "period_end", Date.valueOf("2026-12-31"), "amount", BigDecimal.valueOf(amount),
                "control_mode", "HARD_STOP", "status", "ACTIVE", "currency", "ZAR"));
    }

    /** Runs both tasks at the same instant and returns what each threw (null for success). */
    interface Task { void run() throws Exception; }

    static List<Throwable> race(Task a, Task b) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CyclicBarrier(2);
        List<Future<Throwable>> fs = new ArrayList<>();
        for (Task c : List.of(a, b)) fs.add(pool.submit(() -> { gate.await(); try { c.run(); return (Throwable) null; } catch (Throwable t) { return t; } }));
        List<Throwable> out = new ArrayList<>();
        for (var f : fs) out.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdownNow();
        return out;
    }

    // ── 1. Two approvals racing for the last of a hard-stop budget ───────────

    @Test
    void twoPurchaseOrdersRacingForOneBudgetCannotBothCommit() throws Exception {
        hardStopBudget("PRJ-RACE", 1000);
        UUID po1 = purchaseOrder("PO-RACE-1", BigDecimal.valueOf(600), "PRJ-RACE");
        UUID po2 = purchaseOrder("PO-RACE-2", BigDecimal.valueOf(600), "PRJ-RACE");

        var results = race(() -> budgets.commitForPo(tenant, poRepo.findById(po1).orElseThrow(), user, "A"),
                           () -> budgets.commitForPo(tenant, poRepo.findById(po2).orElseThrow(), user, "B"));

        long failed = results.stream().filter(Objects::nonNull).count();
        assertThat(failed).as("exactly one of the two is refused").isEqualTo(1);
        Throwable t = results.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        assertThat(t).isInstanceOf(HandyFlowException.class);
        assertThat(((HandyFlowException) t).getErrorCode()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(jdbc.queryForObject("SELECT COALESCE(sum(amount),0) FROM sc_budget_commitments WHERE tenant_id = ?", BigDecimal.class, tenantId))
                .as("only the winner's R600 is committed").isEqualByComparingTo("600");
    }

    // ── 2. Withdrawing an amendment when the approval engine fails ───────────

    @Test
    void amendmentCanBeWithdrawnEvenWhenTheApprovalEngineFails() {
        UUID po = purchaseOrder("PO-AMD-1", BigDecimal.valueOf(500), null);
        UUID amendment = UUID.randomUUID();
        insert(jdbc, "sc_po_amendments", row("id", amendment, "tenant_id", tenantId, "purchase_order_id", po, "amendment_number", 1, "base_revision", 1,
                "status", "PENDING_APPROVAL", "material", true, "reason", "More tyres", "previous_total", BigDecimal.valueOf(500), "proposed_total", BigDecimal.valueOf(800),
                "requested_by", user));
        when(approvalFacade.getLatestRequestForEntity(any(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("approval engine down"));

        amendments.withdraw(tenant, amendment, user, "Requester", "Changed my mind");

        assertThat(jdbc.queryForObject("SELECT status FROM sc_po_amendments WHERE id = ?", String.class, amendment)).isEqualTo("WITHDRAWN");
    }

    @Test
    void amendmentCannotBeWithdrawnByAnotherPerson() {
        UUID po = purchaseOrder("PO-AMD-2", BigDecimal.valueOf(500), null);
        UUID amendment = UUID.randomUUID();
        insert(jdbc, "sc_po_amendments", row("id", amendment, "tenant_id", tenantId, "purchase_order_id", po, "amendment_number", 1, "base_revision", 1,
                "status", "PENDING_APPROVAL", "material", true, "reason", "More tyres", "previous_total", BigDecimal.valueOf(500), "proposed_total", BigDecimal.valueOf(800),
                "requested_by", user));
        assertThatThrownBy(() -> amendments.withdraw(tenant, amendment, UUID.randomUUID(), "Someone else", "no"))
                .isInstanceOf(HandyFlowException.class).extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("NOT_REQUESTER");
        assertThat(jdbc.queryForObject("SELECT status FROM sc_po_amendments WHERE id = ?", String.class, amendment)).isEqualTo("PENDING_APPROVAL");
    }

    // ── 3. Reversing a goods receipt after a partial invoice ─────────────────

    @Test
    void receiptCannotBeReversedOnceInvoicesClaimWhatItReceived() {
        UUID po = purchaseOrder("PO-GR-1", BigDecimal.valueOf(1000), null);
        UUID poLine = UUID.randomUUID(), gr = UUID.randomUUID();
        insert(jdbc, "sc_po_lines", row("id", poLine, "purchase_order_id", po, "tenant_id", tenantId, "item_name", "Tyre", "qty_ordered", BigDecimal.TEN,
                "qty_received", BigDecimal.valueOf(5), "unit_cost", BigDecimal.valueOf(100), "vat_rate", BigDecimal.ZERO));
        insert(jdbc, "sc_goods_receipts", row("id", gr, "tenant_id", tenantId, "receipt_number", "GR-1", "purchase_order_id", po, "supplier_id", supplier,
                "received_to", UUID.randomUUID(), "received_date", Date.valueOf("2026-11-18"), "status", "POSTED"));
        insert(jdbc, "sc_gr_lines", row("id", UUID.randomUUID(), "goods_receipt_id", gr, "po_line_id", poLine, "tenant_id", tenantId, "item_name", "Tyre",
                "qty_ordered", BigDecimal.TEN, "qty_received", BigDecimal.valueOf(5), "unit_cost", BigDecimal.valueOf(100)));
        // A header-only invoice for R500 (the 5 received) that is not linked to the receipt itself: the value check must still stop the reversal.
        insert(jdbc, "sc_supplier_invoices", row("id", UUID.randomUUID(), "tenant_id", tenantId, "invoice_number", "SI-1", "supplier_id", supplier,
                "purchase_order_id", po, "invoice_date", Date.valueOf("2026-11-18"), "due_date", Date.valueOf("2026-12-18"), "received_date", Date.valueOf("2026-11-18"),
                "subtotal", BigDecimal.valueOf(500), "total_amount", BigDecimal.valueOf(500), "status", "RECEIVED"));

        assertThatThrownBy(() -> receiving.reverse(tenant, gr, user, "Receiver", "Wrong delivery"))
                .isInstanceOf(HandyFlowException.class).extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("GR_INVOICED");

        assertThat(jdbc.queryForObject("SELECT status FROM sc_goods_receipts WHERE id = ?", String.class, gr)).as("still posted").isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sc_stock_movements WHERE tenant_id = ? AND reference_type = 'GR_REVERSAL'", Integer.class, tenantId))
                .as("no offsetting stock movement written").isZero();
    }

    // ── 4. A requisition submitted twice at once ─────────────────────────────

    @Test
    void requisitionSubmittedTwiceAtOnceOpensOneApprovalRequest() throws Exception {
        UUID req = UUID.randomUUID();
        insert(jdbc, "sc_requisitions", row("id", req, "tenant_id", tenantId, "requisition_number", "REQ-DBL-1", "requisition_type", "OPERATIONAL", "status", "DRAFT",
                "title", "Gloves", "business_reason", "Site safety", "department", "Workshop", "estimated_total", BigDecimal.valueOf(250), "requested_by", user,
                "requested_by_name", "Requester"));
        insert(jdbc, "sc_requisition_lines", row("id", UUID.randomUUID(), "tenant_id", tenantId, "requisition_id", req, "line_number", 1, "item_name", "Gloves",
                "qty_requested", BigDecimal.TEN, "estimated_unit_cost", BigDecimal.valueOf(25), "vat_rate", BigDecimal.valueOf(15)));
        ApprovalRequestResponse pending = mock(ApprovalRequestResponse.class);
        String status = "SUBMITTED";
        when(pending.status()).thenReturn(status);
        when(approvalFacade.submit(any(), anyString(), anyString(), any(), any(), any())).thenReturn(pending);

        var results = race(() -> requisitions.submit(tenant, req, user, "Requester"), () -> requisitions.submit(tenant, req, user, "Requester"));

        assertThat(results.stream().filter(Objects::isNull).count()).as("one submit succeeds").isEqualTo(1);
        assertThat(results.stream().filter(Objects::nonNull).count()).as("the other is refused, not silently repeated").isEqualTo(1);
        verify(approvalFacade, times(1)).submit(any(), anyString(), anyString(), eq(req), any(), any());
        assertThat(jdbc.queryForObject("SELECT status FROM sc_requisitions WHERE id = ?", String.class, req)).isEqualTo("PENDING_APPROVAL");
    }

    // ── 5. Stock that fails inspection leaves available stock, and every way out is accounted for ──

    @Test
    void failedStockIsQuarantinedThenReleasedWrittenOffAndReturned() {
        UUID location = UUID.randomUUID(), item = UUID.randomUUID(), receipt = UUID.randomUUID(), inspection = UUID.randomUUID();
        insert(jdbc, "sc_stock_locations", row("id", location, "tenant_id", tenantId, "name", "Main store"));
        insert(jdbc, "sc_inventory", row("id", UUID.randomUUID(), "tenant_id", tenantId, "location_id", location, "catalogue_item_id", item, "qty_on_hand", BigDecimal.TEN));
        insert(jdbc, "sc_goods_receipts", row("id", receipt, "tenant_id", tenantId, "receipt_number", "GR-Q1", "purchase_order_id", UUID.randomUUID(), "supplier_id", supplier,
                "received_to", location, "received_date", Date.valueOf("2026-11-18"), "status", "POSTED"));

        int held = quarantine.hold(tenant, user, "Inspector", inspection, receipt,
                List.of(new QuarantineService.Failed(item, "Tyre", BigDecimal.valueOf(5), BigDecimal.valueOf(100), "CRACKED")));
        assertThat(held).isEqualTo(1);
        assertThat(onHand(location, item)).as("5 left in the main store").isEqualByComparingTo("5");
        UUID quarantineLocation = jdbc.queryForObject("SELECT id FROM sc_stock_locations WHERE tenant_id = ? AND is_quarantine", UUID.class, tenantId);
        assertThat(onHand(quarantineLocation, item)).isEqualByComparingTo("5");
        UUID hold = jdbc.queryForObject("SELECT id FROM sc_quarantine_holds WHERE tenant_id = ? AND inspection_id = ?", UUID.class, tenantId, inspection);

        quarantine.release(tenant, hold, BigDecimal.ONE, user, "Inspector", "Re-checked, within tolerance");
        quarantine.writeOff(tenant, hold, BigDecimal.ONE, user, "Inspector", "Crushed");
        BigDecimal taken = quarantine.takeForReturn(tenantId, inspection, item, BigDecimal.valueOf(10), UUID.randomUUID(), "RET-1", user, "Inspector");

        assertThat(taken).as("only the 3 still held can go back to the supplier").isEqualByComparingTo("3");
        assertThat(onHand(location, item)).as("the released unit is back").isEqualByComparingTo("6");
        assertThat(onHand(quarantineLocation, item)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT status FROM sc_quarantine_holds WHERE id = ?", String.class, hold)).isEqualTo("CLOSED");
        assertThatThrownBy(() -> quarantine.release(tenant, hold, BigDecimal.ONE, user, "Inspector", "again"))
                .isInstanceOf(HandyFlowException.class).extracting(e -> ((HandyFlowException) e).getErrorCode()).isEqualTo("MORE_THAN_HELD");
    }

    private BigDecimal onHand(UUID location, UUID item) {
        return jdbc.queryForObject("SELECT qty_on_hand FROM sc_inventory WHERE tenant_id = ? AND location_id = ? AND catalogue_item_id = ?", BigDecimal.class, tenantId, location, item);
    }

    // ── 6. Replenishment prefers a contract price in force; analytics count only approved or paid invoices ──

    @Test
    void replenishmentSuggestsTheContractSupplierAndPrice() {
        UUID location = UUID.randomUUID(), item = UUID.randomUUID(), contract = UUID.randomUUID(), contractSupplier = UUID.randomUUID();
        insert(jdbc, "sc_stock_locations", row("id", location, "tenant_id", tenantId, "name", "Main store"));
        insert(jdbc, "sc_inventory", row("id", UUID.randomUUID(), "tenant_id", tenantId, "location_id", location, "catalogue_item_id", item,
                "qty_on_hand", BigDecimal.ZERO, "reorder_point", BigDecimal.TEN, "reorder_qty", BigDecimal.valueOf(20)));
        insert(jdbc, "sc_contracts", row("id", contract, "tenant_id", tenantId, "contract_number", "CON-1", "supplier_id", contractSupplier, "supplier_name", "Contract Co",
                "title", "Tyres", "status", "ACTIVE", "start_date", Date.valueOf(LocalDate.now().minusDays(30)), "end_date", Date.valueOf(LocalDate.now().plusDays(300))));
        insert(jdbc, "sc_contract_lines", row("id", UUID.randomUUID(), "tenant_id", tenantId, "contract_id", contract, "catalogue_item_id", item, "description", "Tyre",
                "agreed_unit_price", BigDecimal.valueOf(80), "min_qty", BigDecimal.valueOf(1000)));      // minimum order not reached: must not apply
        insert(jdbc, "sc_contract_lines", row("id", UUID.randomUUID(), "tenant_id", tenantId, "contract_id", contract, "catalogue_item_id", item, "description", "Tyre",
                "agreed_unit_price", BigDecimal.valueOf(90), "min_qty", BigDecimal.TEN));

        var rec = replenishment.recommendations(tenant).stream().filter(r -> r.catalogueItemId().equals(item)).findFirst().orElseThrow();

        assertThat(rec.urgency()).isEqualTo("STOCKOUT");
        assertThat(rec.priceSource()).isEqualTo("CONTRACT");
        assertThat(rec.contractNumber()).isEqualTo("CON-1");
        assertThat(rec.suggestedSupplierId()).isEqualTo(contractSupplier);
        assertThat(rec.suggestedUnitCost()).isEqualByComparingTo("90");
    }

    @Test
    void spendCountsOnlyApprovedAndPaidInvoicesAndShowsTheNoPoShare() {
        UUID po = purchaseOrder("PO-SP-1", BigDecimal.valueOf(100), null);
        String[][] rows = {{"SP-1", "APPROVED", "100", "po"}, {"SP-2", "PAID", "300", "nopo"}, {"SP-3", "RECEIVED", "999", "po"}};
        for (String[] r : rows) {
            insert(jdbc, "sc_supplier_invoices", row("id", UUID.randomUUID(), "tenant_id", tenantId, "invoice_number", r[0], "supplier_id", supplier,
                    "purchase_order_id", "po".equals(r[3]) ? po : null, "invoice_date", Date.valueOf(LocalDate.now().minusDays(5)), "due_date", Date.valueOf(LocalDate.now().plusDays(25)),
                    "received_date", Date.valueOf(LocalDate.now().minusDays(5)), "subtotal", new BigDecimal(r[2]), "total_amount", new BigDecimal(r[2]), "status", r[1]));
        }

        var spend = analytics.spend(tenant, null, null);

        assertThat(spend.invoiced()).as("the RECEIVED invoice is not spend yet").isEqualByComparingTo("400");
        assertThat(spend.paid()).isEqualByComparingTo("300");
        assertThat(spend.invoicedWithoutPo()).isEqualByComparingTo("300");
        assertThat(spend.withoutPoSharePct()).isEqualTo(75.0);
        assertThat(spend.bySupplier()).singleElement().satisfies(s -> assertThat(s.sharePct()).isEqualTo(100.0));
    }
}
