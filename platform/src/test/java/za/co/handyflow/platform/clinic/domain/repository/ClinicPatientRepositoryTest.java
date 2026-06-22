package za.co.handyflow.platform.clinic.domain.repository;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * JPA slice test — spins up an in-memory H2 DB (or Testcontainers PostgreSQL).
 * Tests the JPQL queries in ClinicPatientRepository are syntactically correct
 * and return the expected data.
 *
 * WHY DataJpaTest?
 * Loads only the JPA layer (entities, repositories, Flyway migrations).
 * No web layer, no services — fast (~2s) and focused.
 *
 * NOTE: If you use PostgreSQL-specific features (text[], JSONB) that H2
 * doesn't support, switch to @Testcontainers with a real PostgreSQL image.
 * The Testcontainers variant is shown in comments below.
 */
@DataJpaTest
@ActiveProfiles("test")
class ClinicPatientRepositoryTest {

    @Autowired ClinicPatientRepository repo;

    // TenantId has a private constructor — create via Mockito mock
    // and stub getValue() which is what SpEL :#{#tenantId.value} and service internals call.
    static final UUID TENANT_UUID = UUID.fromString("9ecb3dc7-75d4-4e56-b0a2-c95d3c7c584f");
    static final TenantId TENANT;
    static {
        TENANT = Mockito.mock(TenantId.class);
        Mockito.when(TENANT.getValue()).thenReturn(TENANT_UUID);
    }

    // Also need a tenant row in the DB — use @Sql to insert it
    // or have your test migration handle it.
    // See test/resources/db/migration/V999__test_data.sql

    ClinicPatient save(String first, String last, String accountType) {
        var p = ClinicPatient.create(TENANT, first, last,
                null, null, null, "+27820000001", null, null, null);
        if (!"INDIVIDUAL".equals(accountType)) p.setAccountType(accountType);
        return repo.save(p);
    }

    @BeforeEach
    void setup() {
        repo.deleteAll();
    }

    @Test
    @DisplayName("findAllActive returns only non-deleted patients for tenant")
    void findAllActiveReturnsOnlyActive() {
        var active  = save("Jane","Dlamini","INDIVIDUAL");
        var deleted = save("Sipho","Nkosi","INDIVIDUAL");
        deleted.softDelete(UUID.randomUUID());
        repo.save(deleted);

        var result = repo.findAllActive(TENANT, PageRequest.of(0,20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFirstName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("searchActive finds patients by last name substring")
    void searchActiveByLastName() {
        save("Sipho","Nkosi","INDIVIDUAL");
        save("Jane","Dlamini","INDIVIDUAL");

        var result = repo.searchActive(TENANT, "nkosi", PageRequest.of(0,20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLastName()).isEqualTo("Nkosi");
    }

    @Test
    @DisplayName("searchActive is case-insensitive")
    void searchIsNotCaseSensitive() {
        save("Sipho","Nkosi","INDIVIDUAL");

        var upper = repo.searchActive(TENANT, "NKOSI", PageRequest.of(0,20));
        var lower = repo.searchActive(TENANT, "nkosi", PageRequest.of(0,20));
        var mixed = repo.searchActive(TENANT, "Nkosi", PageRequest.of(0,20));

        assertThat(upper.getTotalElements()).isEqualTo(1);
        assertThat(lower.getTotalElements()).isEqualTo(1);
        assertThat(mixed.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("findActiveById returns empty for deleted patient")
    void findActiveByIdExcludesDeleted() {
        var patient = save("Jane","Dlamini","INDIVIDUAL");
        patient.softDelete(UUID.randomUUID());
        repo.save(patient);

        var result = repo.findActiveById(TENANT, patient.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByTenantIdAndId finds including soft-deleted")
    void findByTenantIdAndIdIncludesDeleted() {
        var patient = save("Jane","Dlamini","INDIVIDUAL");
        patient.softDelete(UUID.randomUUID());
        repo.save(patient);

        var result = repo.findByTenantIdAndId(TENANT, patient.getId());

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findDependantsByPrincipalId returns only linked dependants")
    void findDependantsByPrincipalId() {
        var principal = save("Jane","Dlamini","PRINCIPAL");
        var dependant1 = save("Thomas","Dlamini","DEPENDANT");
        dependant1.setPrincipalId(principal.getId());
        dependant1.setRelationship("SPOUSE");
        repo.save(dependant1);

        var dependant2 = save("Alex","Dlamini","DEPENDANT");
        dependant2.setPrincipalId(principal.getId());
        dependant2.setRelationship("CHILD");
        repo.save(dependant2);

        // Save unrelated patient
        save("Sipho","Nkosi","INDIVIDUAL");

        var result = repo.findDependantsByPrincipalId(TENANT, principal.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClinicPatient::getLastName)
                .containsOnly("Dlamini");
    }

    @Test
    @DisplayName("findActiveByTenantId excludes archived patients")
    void findActiveByTenantIdExcludesArchived() {
        save("Jane","Dlamini","INDIVIDUAL");
        var archived = save("Sipho","Nkosi","INDIVIDUAL");
        archived.setArchivedAt(Instant.now());
        repo.save(archived);

        var result = repo.findActiveByTenantId(TENANT, PageRequest.of(0,20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getFirstName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("findByTenantId includes archived patients")
    void findByTenantIdIncludesArchived() {
        save("Jane","Dlamini","INDIVIDUAL");
        var archived = save("Sipho","Nkosi","INDIVIDUAL");
        archived.setArchivedAt(Instant.now());
        repo.save(archived);

        var result = repo.findByTenantId(TENANT, PageRequest.of(0,20));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search excludes archived but searchIncludingArchived includes them")
    void searchArchivedBehaviour() {
        save("Jane","Dlamini","INDIVIDUAL");
        var archived = save("Jane","Nkosi","INDIVIDUAL");
        archived.setArchivedAt(Instant.now());
        repo.save(archived);

        var active = repo.search(TENANT, "Jane", PageRequest.of(0,20));
        var all    = repo.searchIncludingArchived(TENANT, "Jane", PageRequest.of(0,20));

        assertThat(active.getTotalElements()).isEqualTo(1);
        assertThat(all.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findAllByIds batch-loads exactly the requested IDs")
    void findAllByIdsBatchLoads() {
        var p1 = save("Jane","Dlamini","INDIVIDUAL");
        var p2 = save("Sipho","Nkosi","INDIVIDUAL");
        save("Fatima","Moosa","INDIVIDUAL"); // not requested

        var result = repo.findAllByIds(TENANT, java.util.Set.of(p1.getId(), p2.getId()));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClinicPatient::getLastName)
                .containsExactlyInAnyOrder("Dlamini","Nkosi");
    }
}
