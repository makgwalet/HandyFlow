package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRate;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRateRepository;
import za.co.handyflow.platform.compliancetender.dto.ImportTenderRatesRequest;
import za.co.handyflow.platform.compliancetender.dto.SaveTenderRateRequest;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenderRateServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID user = UUID.randomUUID();
    private final TenderRateRepository repo = mock(TenderRateRepository.class);
    private final List<TenderRate> store = new ArrayList<>();
    private final TenderRateService service = new TenderRateService(repo);

    @BeforeEach
    void fakeRepository() {
        when(repo.findByKey(eq(tenant), anyString(), anyString(), anyString())).thenAnswer(inv -> store.stream()
                .filter(r -> r.getDescription().equalsIgnoreCase(inv.getArgument(1)) && r.getUnit().equalsIgnoreCase(inv.getArgument(2)) && r.getSupplier().equalsIgnoreCase(inv.getArgument(3)))
                .findFirst());
        when(repo.save(any(TenderRate.class))).thenAnswer(inv -> { TenderRate r = inv.getArgument(0); store.add(r); return r; });
        when(repo.saveAndFlush(any(TenderRate.class))).thenAnswer(inv -> { TenderRate r = inv.getArgument(0); if (!store.contains(r)) store.add(r); return r; });
        when(repo.findByIdForTenant(eq(tenant), any(UUID.class))).thenAnswer(inv -> store.stream().filter(r -> r.getId().equals(inv.getArgument(1))).findFirst());
    }

    private static SaveTenderRateRequest req(String description, String unit, String cost, String supplier) {
        return new SaveTenderRateRequest("MATERIAL", null, description, unit, new BigDecimal(cost), supplier, null, null);
    }

    private ImportTenderRatesRequest csv(String text, String supplier, boolean dryRun) {
        return new ImportTenderRatesRequest(text, supplier, null, dryRun);
    }

    @Test
    @DisplayName("adding a rate saves it cleaned; the same rate again (any capitalisation) is refused")
    void createAndDuplicate() {
        var r = service.create(tenant, req("  Cement   50kg ", "bag", "125.50", "BuildIt"), user);
        assertThat(r.description()).isEqualTo("Cement 50kg");
        assertThat(r.supplier()).isEqualTo("BuildIt");
        assertThatThrownBy(() -> service.create(tenant, req("cement 50kg", "BAG", "130", "buildit"), user))
                .isInstanceOf(BusinessException.class).hasMessageContaining("already in the library");
    }

    @Test
    @DisplayName("changing the cost keeps the old one as the previous cost; changing nothing does not")
    void priceChange() {
        var r = service.create(tenant, req("Sand", "m3", "300", ""), user);
        var same = service.update(tenant, r.id(), req("Sand", "m3", "300.00", ""), user);
        assertThat(same.previousUnitCost()).isNull();
        var moved = service.update(tenant, r.id(), req("Sand", "m3", "320", ""), user);
        assertThat(moved.previousUnitCost()).isEqualByComparingTo("300");
        assertThat(moved.priceChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("a rate cannot be renamed onto another rate that already exists")
    void renameOntoExisting() {
        service.create(tenant, req("Sand", "m3", "300", ""), user);
        var stone = service.create(tenant, req("Stone", "m3", "400", ""), user);
        assertThatThrownBy(() -> service.update(tenant, stone.id(), req("sand", "m3", "400", ""), user)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a rate can be switched off and on through update")
    void activeFlag() {
        var r = service.create(tenant, req("Sand", "m3", "300", ""), user);
        var off = service.update(tenant, r.id(), new SaveTenderRateRequest("MATERIAL", null, "Sand", "m3", new BigDecimal("300"), "", null, false), user);
        assertThat(off.active()).isFalse();
        assertThat(service.update(tenant, r.id(), req("Sand", "m3", "300", ""), user).active()).isFalse();   // left out: unchanged
    }

    @Test
    @DisplayName("an unknown rate is not found")
    void notFound() {
        assertThatThrownBy(() -> service.update(tenant, UUID.randomUUID(), req("x", "m", "1", ""), user)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("an import creates new rates, tagged with the supplier of the list and the import source")
    void importCreates() {
        var result = service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,120\nSand,m3,300\n", "BuildIt", false), user);
        assertThat(result.created()).isEqualTo(2);
        assertThat(store).extracting(TenderRate::getSupplier).containsOnly("BuildIt");
        assertThat(store).extracting(TenderRate::getSource).containsOnly("IMPORT");
        assertThat(store).extracting(TenderRate::getCategory).containsOnly("MATERIAL");
    }

    @Test
    @DisplayName("importing the same list again changes nothing; a new price updates in place and is listed with from and to")
    void importTwice() {
        service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,120\nSand,m3,300\n", "BuildIt", false), user);
        var again = service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,120\nSand,m3,300\n", "BuildIt", false), user);
        assertThat(again.created()).isZero();
        assertThat(again.unchanged()).isEqualTo(2);
        var next = service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,135\nSand,m3,300\n", "BuildIt", false), user);
        assertThat(next.updated()).isEqualTo(1);
        assertThat(next.unchanged()).isEqualTo(1);
        assertThat(next.priceChanges()).hasSize(1);
        assertThat(next.priceChanges().get(0).from()).isEqualByComparingTo("120");
        assertThat(next.priceChanges().get(0).to()).isEqualByComparingTo("135");
        assertThat(store).hasSize(2);
        assertThat(store.stream().filter(r -> r.getDescription().equals("Cement")).findFirst().orElseThrow().getPreviousUnitCost()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("a dry run reports the same counts and saves and changes nothing")
    void dryRun() {
        service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,120\n", "BuildIt", false), user);
        var dry = service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,150\nNails,kg,40\n", "BuildIt", true), user);
        assertThat(dry.dryRun()).isTrue();
        assertThat(dry.created()).isEqualTo(1);
        assertThat(dry.updated()).isEqualTo(1);
        assertThat(store).hasSize(1);
        assertThat(store.get(0).getUnitCost()).isEqualByComparingTo("120");
    }

    @Test
    @DisplayName("the same item from two suppliers is two rates")
    void twoSuppliers() {
        service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,120\n", "BuildIt", false), user);
        service.importCsv(tenant, csv("Description,Unit,Cost\nCement,bag,115\n", "Cashbuild", false), user);
        assertThat(store).hasSize(2);
    }

    @Test
    @DisplayName("a file without a category column leaves an existing rate's category alone, and a re-listed rate is switched back on")
    void keepsCategoryAndReactivates() {
        var labour = service.create(tenant, new SaveTenderRateRequest("LABOUR", null, "Bricklayer", "day", new BigDecimal("900"), "", null, null), user);
        store.get(0).setActive(false, user);
        var r = service.importCsv(tenant, csv("Description,Unit,Cost\nBricklayer,day,950\n", "", false), user);
        assertThat(r.updated()).isEqualTo(1);
        assertThat(store.get(0).getCategory()).isEqualTo("LABOUR");
        assertThat(store.get(0).isActive()).isTrue();
        assertThat(store.get(0).getUnitCost()).isEqualByComparingTo("950");
        assertThat(labour.id()).isEqualTo(store.get(0).getId());
    }

    @Test
    @DisplayName("bad rows are reported with their line and counted as skipped; good rows still import")
    void problems() {
        var r = service.importCsv(tenant, csv("Description,Unit,Cost\nGood,m,5\nBad,m,1.234\n", "S", false), user);
        assertThat(r.created()).isEqualTo(1);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.problems()).hasSize(1);
        assertThat(r.problems().get(0).line()).isEqualTo(3);
    }

    @Test
    @DisplayName("a file that cannot be read at all reports one problem on line 0 and imports nothing")
    void fatalFile() {
        var r = service.importCsv(tenant, csv("nonsense", "S", false), user);
        assertThat(r.created()).isZero();
        assertThat(r.problems()).hasSize(1);
        assertThat(r.problems().get(0).line()).isZero();
        assertThat(store).isEmpty();
    }
}
