package za.co.handyflow.platform.catalogue.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.catalogue.application.internal.CatalogueService.Caller;
import za.co.handyflow.platform.catalogue.domain.model.*;
import za.co.handyflow.platform.catalogue.domain.repository.*;
import za.co.handyflow.platform.catalogue.dto.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CatalogueImportServiceTest {
    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final Caller ADMIN = new Caller(UUID.randomUUID(), true, true, true, true);
    private static final Caller CLERK = new Caller(UUID.randomUUID(), false, false, false, false);

    @Mock CatalogueItemRepository items;
    @Mock CatalogueCategoryRepository categories;
    @Mock CatalogueImportJobRepository jobs;
    @Mock CatalogueService catalogue;
    @Mock CataloguePricingService pricing;
    CatalogueImportService service;
    CatalogueItem cement;

    @BeforeEach
    void setUp() {
        service = new CatalogueImportService(items, categories, jobs, catalogue, pricing);
        cement = item("Cement 50kg", "CEM-50", "89.90");
        when(items.allLive(TENANT)).thenReturn(List.of(cement));
        when(items.parentIdsWithVariants(TENANT)).thenReturn(List.of());
        when(categories.findAllActive(TENANT)).thenReturn(List.of());
        when(pricing.approvalRequired(TENANT)).thenReturn(false);
        when(jobs.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static CatalogueItem item(String name, String sku, String price) {
        var d = new CatalogueItem.Details(name, "Bag", sku, "600", "PPC", CatalogueItemType.PRODUCT, "each",
                new BigDecimal(price), new BigDecimal("50.00"), new BigDecimal("15.00"), false);
        return CatalogueItem.create(TENANT, null, d, CatalogueItemStatus.ACTIVE);
    }

    private static ImportRequest req(String csv, ImportRules.Mode mode, Boolean skip) { return new ImportRequest("items.csv", csv, mode, skip); }

    @Test
    void theDryRunSavesNothingAndListsProblemsFirst() {
        var r = service.preview(TENANT, req("sku,name,price\nCEM-50,Cement 50kg,89.90\nNEW,,5\nNEW2,Brick,2\n", ImportRules.Mode.CREATE_OR_UPDATE, null), ADMIN);
        assertThat(r.total()).isEqualTo(3);
        assertThat(r.create()).isEqualTo(1L);
        assertThat(r.unchanged()).isEqualTo(1L);
        assertThat(r.errors()).isEqualTo(1L);
        assertThat(r.rows().get(0).action()).isEqualTo("ERROR");
        verify(catalogue, never()).createItem(any(), any(), any());
        verify(jobs, never()).save(any());
    }

    @Test
    void aBrokenFileIsReportedWithoutAPlan() {
        var r = service.preview(TENANT, req("price\n1\n", ImportRules.Mode.CREATE_ONLY, null), ADMIN);
        assertThat(r.fileProblems()).isNotEmpty();
        assertThat(r.rows()).isEmpty();
    }

    @Test
    void appliesCreatesAndUpdatesAndRecordsTheImport() {
        var out = service.run(TENANT, req("sku,name,price,brand\nNEW,Brick,2,Corobrik\nCEM-50,,89.90,Sephaku\n", ImportRules.Mode.CREATE_OR_UPDATE, null), ADMIN);
        assertThat(out.created()).isEqualTo(1);
        assertThat(out.updated()).isEqualTo(1);
        var created = ArgumentCaptor.forClass(CreateItemRequest.class);
        verify(catalogue).createItem(eq(TENANT), created.capture(), eq(ADMIN));
        assertThat(created.getValue().name()).isEqualTo("Brick");
        assertThat(created.getValue().unit()).isEqualTo("each");
        assertThat(created.getValue().status()).isEqualTo(CatalogueItemStatus.ACTIVE);
        var updated = ArgumentCaptor.forClass(CreateItemRequest.class);
        verify(catalogue).updateItem(eq(TENANT), eq(cement.getId()), updated.capture(), eq(ADMIN));
        assertThat(updated.getValue().brand()).isEqualTo("Sephaku");
        assertThat(updated.getValue().name()).isEqualTo("Cement 50kg");
        assertThat(updated.getValue().sku()).isEqualTo("CEM-50");
        verify(jobs).save(any());
    }

    @Test
    void refusesToApplyWhileRowsHaveProblems() {
        assertThatThrownBy(() -> service.run(TENANT, req("sku,name,price\nA,,1\n", ImportRules.Mode.CREATE_ONLY, null), ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        verify(catalogue, never()).createItem(any(), any(), any());
    }

    @Test
    void canLeaveProblemRowsOutAndKeepTheRest() {
        var out = service.run(TENANT, req("sku,name,price\nA,,1\nB,Brick,2\n", ImportRules.Mode.CREATE_ONLY, true), ADMIN);
        assertThat(out.created()).isEqualTo(1);
        assertThat(out.skipped()).isEqualTo(1);
        assertThat(out.problems().get(0)).contains("row 2");
    }

    @Test
    void oneRowFailingAtSaveTimeDoesNotStopTheOthers() {
        when(catalogue.createItem(eq(TENANT), any(), any())).thenThrow(new ConflictException("An item named 'Brick' already exists.")).thenReturn(null);
        var out = service.run(TENANT, req("sku,name,price\nA,Brick,2\nB,Block,3\n", ImportRules.Mode.CREATE_ONLY, null), ADMIN);
        assertThat(out.failed()).isEqualTo(1);
        assertThat(out.created()).isEqualTo(1);
        verify(catalogue, times(2)).createItem(eq(TENANT), any(), any());
    }

    @Test
    void priceChangesAreNotImportedWhenApprovalIsRequired() {
        when(pricing.approvalRequired(TENANT)).thenReturn(true);
        var r = service.preview(TENANT, req("sku,price\nCEM-50,99\n", ImportRules.Mode.UPDATE_ONLY, null), ADMIN);
        assertThat(r.errors()).isEqualTo(1L);
    }

    @Test
    void exportLeavesOutCostForPeopleWhoCannotSeePrices() {
        String withCost = service.export(TENANT, ADMIN);
        String without = service.export(TENANT, CLERK);
        assertThat(withCost.split("\r\n")[1]).contains(",89.90,50.00,15,no");
        assertThat(without.split("\r\n")[1]).contains(",89.90,,15,no");
        assertThat(without.split("\r\n")[0]).isEqualTo("sku,name,description,category,unit,brand,barcode,type,status,price,cost,vat_rate,vat_exempt");
    }
}
