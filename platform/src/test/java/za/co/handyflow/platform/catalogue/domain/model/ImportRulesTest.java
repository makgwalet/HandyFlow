package za.co.handyflow.platform.catalogue.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.catalogue.domain.model.ImportRules.*;

class ImportRulesTest {
    private static final Options OPEN = new Options(false, true, false);

    private static Existing item(String sku, String name, String price) {
        return new Existing(UUID.randomUUID(), sku, name, "Bag", "Building > Cement", "each", "PPC", "600", CatalogueItemType.PRODUCT,
                CatalogueItemStatus.ACTIVE, new BigDecimal(price), new BigDecimal("50.00"), new BigDecimal("15"), false, 3L, false, false);
    }

    private static Plan plan(String csv, Mode mode, Options opt, Existing... existing) {
        var t = ImportRules.read(csv);
        assertThat(t.problems()).isEmpty();
        return ImportRules.plan(t.rows().stream().map(ImportRules::parse).toList(), mode, List.of(existing), List.of("Building", "Building > Cement", "Services"), opt);
    }

    @Test
    void readsQuotesSemicolonsAndBom() {
        var rows = ImportRules.parseCsv("﻿sku;name\r\nA1;\"Bolt, 10mm\"\r\nA2;\"say \"\"hi\"\"\"\r\n\r\n");
        assertThat(rows.size()).isEqualTo(3);
        assertThat(rows.get(1).get(1)).isEqualTo("Bolt, 10mm");
        assertThat(rows.get(2).get(1)).isEqualTo("say \"hi\"");
    }

    @Test
    void findsColumnsByTheirUsualNames() {
        var t = ImportRules.read("Item Code,Product Name,Selling Price,Colour\nA1,Bolt,\"12,50\",red\n");
        assertThat(t.problems()).isEmpty();
        assertThat(t.unknownColumns()).containsExactly("Colour");
        assertThat(t.rows().get(0).cells().get("sku")).isEqualTo("A1");
        assertThat(t.rows().get(0).cells().get("price")).isEqualTo("12,50");
        assertThat(t.rows().get(0).line()).isEqualTo(2);
    }

    @Test
    void refusesFilesWithoutIdentifyingColumnsOrRows() {
        assertThat(ImportRules.read("price,unit\n1,each\n").problems()).isNotEmpty();
        assertThat(ImportRules.read("sku,name\n").problems()).contains("The file has a header but no rows.");
        assertThat(ImportRules.read("").problems()).contains("The file is empty.");
        assertThat(ImportRules.read("sku,sku\nA,B\n").problems()).isNotEmpty();
    }

    @Test
    void readsMoneyTheSouthAfricanWay() {
        var e = new ArrayList<String>();
        assertThat(ImportRules.money("R1 250,50", "price", e)).isEqualByComparingTo("1250.50");
        assertThat(ImportRules.money("12,5", "price", e)).isEqualByComparingTo("12.50");
        assertThat(ImportRules.money("1,250.00", "price", e)).isEqualByComparingTo("1250.00");
        assertThat(e).isEmpty();
        assertThat(ImportRules.money("-1", "price", e)).isNull();
        assertThat(ImportRules.money("1.234", "price", e)).isNull();
        assertThat(ImportRules.money("abc", "price", e)).isNull();
        assertThat(e.size()).isEqualTo(3);
    }

    @Test
    void createsNewItemsAndSaysWhatIsWrongWithTheRest() {
        var p = plan("sku,name,price,category\nN1,Brick,5.50,Cement\nN2,,3,Cement\nN3,Sand,x,Nowhere\n", Mode.CREATE_OR_UPDATE, OPEN);
        assertThat(p.rows().get(0).action()).isEqualTo(Action.CREATE);
        assertThat(p.rows().get(1).action()).isEqualTo(Action.ERROR);
        assertThat(p.rows().get(1).errors()).contains("A new item needs a name.");
        assertThat(p.rows().get(2).errors().size()).isEqualTo(2);
        assertThat(p.hasErrors()).isTrue();
        assertThat(p.count(Action.CREATE)).isEqualTo(1L);
    }

    @Test
    void updatesByskuAndLeavesEmptyCellsAlone() {
        var cement = item("CEM-50", "Cement 50kg", "89.90");
        var p = plan("sku,name,brand,price\ncem-50,,Sephaku,89.90\n", Mode.CREATE_OR_UPDATE, OPEN, cement);
        var row = p.rows().get(0);
        assertThat(row.action()).isEqualTo(Action.UPDATE);
        assertThat(row.changes()).containsExactly("brand: PPC → Sephaku");
        assertThat(row.itemId()).isEqualTo(cement.id());
        assertThat(row.version()).isEqualTo(3L);
    }

    @Test
    void sameValuesMeansUnchanged() {
        var p = plan("sku,name,price\nCEM-50,Cement 50kg,89.90\n", Mode.CREATE_OR_UPDATE, OPEN, item("CEM-50", "Cement 50kg", "89.90"));
        assertThat(p.rows().get(0).action()).isEqualTo(Action.UNCHANGED);
    }

    @Test
    void honoursTheMode() {
        var cement = item("CEM-50", "Cement 50kg", "89.90");
        assertThat(plan("sku,name,price\nCEM-50,Cement 50kg,1\n", Mode.CREATE_ONLY, OPEN, cement).rows().get(0).action()).isEqualTo(Action.ERROR);
        assertThat(plan("sku,name,price\nNEW,Thing,1\n", Mode.UPDATE_ONLY, OPEN, cement).rows().get(0).errors()).contains("No item has the SKU 'NEW'.");
    }

    @Test
    void priceChangesOnExistingItemsWaitForApprovalWhenRequired() {
        var cement = item("CEM-50", "Cement 50kg", "89.90");
        var strict = new Options(true, true, false);
        var row = plan("sku,price\nCEM-50,99\n", Mode.UPDATE_ONLY, strict, cement).rows().get(0);
        assertThat(row.action()).isEqualTo(Action.ERROR);
        assertThat(row.errors().get(0)).contains("need approval");
        assertThat(plan("sku,name,price\nNEW1,Thing,99\n", Mode.CREATE_ONLY, strict).rows().get(0).action()).isEqualTo(Action.CREATE);
    }

    @Test
    void costNeedsPermission() {
        var noCost = new Options(false, false, false);
        var row = plan("sku,name,price,cost\nA,Thing,5,3\n", Mode.CREATE_ONLY, noCost).rows().get(0);
        assertThat(row.action()).isEqualTo(Action.ERROR);
    }

    @Test
    void catchesRepeatsInsideTheFileAndAgainstExistingItems() {
        var cement = item("CEM-50", "Cement 50kg", "89.90");
        var p = plan("sku,name,price,barcode\nA,Bolt,1,111\nA,Nut,1,222\nB,Bolt,1,333\nC,Cement 50kg,1,444\nD,Washer,1,600\nE,Screw,1,111\n", Mode.CREATE_ONLY, OPEN, cement);
        assertThat(p.rows().get(1).errors().get(0)).contains("already on row 2");
        assertThat(p.rows().get(2).errors().get(0)).contains("name 'Bolt' is already on row 2");
        assertThat(p.rows().get(3).errors().get(0)).contains("already exists");
        assertThat(p.rows().get(4).errors().get(0)).contains("barcode '600'");
        assertThat(p.rows().get(5).errors().get(0)).contains("barcode '111' is already on row 2");
    }

    @Test
    void categoriesMatchByPathOrUniqueName() {
        var p = plan("sku,name,price,category\nA,One,1,Building > Cement\nB,Two,1,cement\nC,Three,1,Building / Cement\nD,Four,1,Services\n", Mode.CREATE_ONLY, OPEN);
        assertThat(p.count(Action.CREATE)).isEqualTo(4L);
    }

    @Test
    void newItemsStartAsDraftsWhenApprovalIsRequired() {
        var must = new Options(false, true, true);
        assertThat(plan("sku,name,price\nA,One,1\n", Mode.CREATE_ONLY, must).rows().get(0).notes()).isNotEmpty();
        assertThat(plan("sku,name,price,status\nA,One,1,active\n", Mode.CREATE_ONLY, must).rows().get(0).action()).isEqualTo(Action.ERROR);
        assertThat(plan("sku,name,price,status\nA,One,1,draft\n", Mode.CREATE_ONLY, must).rows().get(0).action()).isEqualTo(Action.CREATE);
    }

    @Test
    void writesFormulaSafeCsv() {
        assertThat(ImportRules.csvLine(List.of("=SUM(A1)", "a,b", "-5", "plain"), Set.of(2))).isEqualTo("'=SUM(A1),\"a,b\",-5,plain");
        var t = ImportRules.read("sku,name\nA,'=SUM(A1)\n");
        assertThat(t.rows().get(0).cells().get("name")).isEqualTo("=SUM(A1)");
    }

    @Test
    void theTemplateReadsBackWithoutErrors() {
        var p = ImportRules.plan(ImportRules.read(ImportRules.template()).rows().stream().map(ImportRules::parse).toList(), Mode.CREATE_ONLY, List.of(), List.of("Building", "Building > Cement"), OPEN);
        assertThat(p.rows().get(0).errors()).isEmpty();
        assertThat(p.rows().get(0).action()).isEqualTo(Action.CREATE);
    }
}
