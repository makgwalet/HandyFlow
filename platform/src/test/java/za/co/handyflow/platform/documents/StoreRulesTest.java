package za.co.handyflow.platform.documents;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.documents.domain.model.StoreRules;
import za.co.handyflow.platform.documents.domain.model.StoreRules.Status;
import za.co.handyflow.platform.documents.domain.model.StoreRules.Verdict;
import za.co.handyflow.platform.documents.domain.model.StoredObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StoreRulesTest {

    byte[] file = "hello".getBytes(StandardCharsets.UTF_8);
    String sha = StoreRules.sha256(file);

    @Test void theHashIsLowerCaseSha256() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha);
    }

    @Test void emptyAndOversizeFilesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> StoreRules.checkSize(new byte[0], 10));
        assertThrows(IllegalArgumentException.class, () -> StoreRules.checkSize(null, 10));
        assertThrows(IllegalArgumentException.class, () -> StoreRules.checkSize(new byte[11], 10));
        StoreRules.checkSize(new byte[10], 10);
    }

    @Test void anIntactPlainFilePasses() {
        assertEquals(Status.OK, StoreRules.judge(file, sha, false, null, sha).status());
    }

    @Test void aChangedPlainFileIsAMismatch() {
        byte[] changed = "hellO".getBytes(StandardCharsets.UTF_8);
        Verdict v = StoreRules.judge(changed, sha, false, null, sha);
        assertEquals(Status.MISMATCH, v.status());
        assertNotNull(v.detail());
    }

    @Test void anEncryptedFileIsCheckedBeforeAndAfterOpening() {
        byte[] stored = "SEALED".getBytes(StandardCharsets.UTF_8);
        String storedSha = StoreRules.sha256(stored);
        assertEquals(Status.OK, StoreRules.judge(stored, storedSha, true, b -> file, sha).status());
        // stored bytes changed: caught before opening
        assertEquals(Status.MISMATCH, StoreRules.judge("SEALEd".getBytes(), storedSha, true, b -> { fail("opened"); return null; }, sha).status());
        // opens, but to something else
        assertEquals(Status.MISMATCH, StoreRules.judge(stored, storedSha, true, b -> "other".getBytes(), sha).status());
        // will not open
        assertEquals(Status.MISMATCH, StoreRules.judge(stored, storedSha, true, b -> { throw new IllegalStateException("tag"); }, sha).status());
    }

    @Test void nothingReturnedIsMissing() {
        assertEquals(Status.MISSING, StoreRules.judge(null, sha, false, null, sha).status());
    }

    @Test void readFailuresAreSortedIntoMissingOrUnreadable() {
        assertEquals(Status.MISSING, StoreRules.readFailure(new IOException("No file found for storage key: x")));
        assertEquals(Status.MISSING, StoreRules.readFailure(new NoSuchFileException("x")));
        assertEquals(Status.MISSING, StoreRules.readFailure(new FileNotFoundException("x")));
        assertEquals(Status.UNREADABLE, StoreRules.readFailure(new IOException("Connection reset")));
        assertEquals(Status.UNREADABLE, StoreRules.readFailure(null));
    }

    @Test void theAssociatedDataNamesTenantAndRow() {
        UUID t = UUID.randomUUID(), o = UUID.randomUUID();
        assertArrayEquals(StoreRules.aad(t, o), StoreRules.aad(t, o));
        assertNotEquals(new String(StoreRules.aad(t, o)), new String(StoreRules.aad(o, t)));
    }

    @Test void moduleCodesAreCleaned() {
        assertEquals("EVIDENCE", StoreRules.module(" evidence "));
        assertEquals("CLINIC_RECORDS", StoreRules.module("clinic-records"));
        assertThrows(IllegalArgumentException.class, () -> StoreRules.module(" "));
    }

    @Test void aFileGoingBadIsAlertedOnceAndRecoveryClearsIt() {
        StoredObject o = StoredObject.written(UUID.randomUUID(), UUID.randomUUID(), "evidence", "k", "database", "application/pdf",
                5, sha, 5, sha, null);
        assertEquals("UNVERIFIED", o.getVerifyStatus());
        assertFalse(o.isEncrypted());
        Instant now = Instant.now();
        assertFalse(o.checked(new Verdict(Status.OK, null), now));
        assertTrue(o.checked(new Verdict(Status.MISMATCH, "changed"), now), "first failure alerts");
        assertFalse(o.checked(new Verdict(Status.MISSING, "gone"), now), "still bad: no second alert");
        assertEquals(2, o.getFailedChecks());
        assertTrue(o.isFailing());
        assertFalse(o.checked(new Verdict(Status.OK, null), now));
        assertEquals(0, o.getFailedChecks());
        assertTrue(o.checked(new Verdict(Status.UNREADABLE, "io"), now), "bad again after a recovery alerts again");
    }

    @Test void deletionIsKeptOnceWithItsReason() {
        StoredObject o = StoredObject.written(UUID.randomUUID(), UUID.randomUUID(), "documents", "k", "database", null, 5, sha, 5, sha, null);
        Instant first = Instant.parse("2026-11-03T10:00:00Z");
        o.markDeleted("Replaced", first);
        o.markDeleted("Again", Instant.parse("2026-11-04T10:00:00Z"));
        assertTrue(o.isDeleted());
        assertEquals(first, o.getDeletedAt());
        assertEquals("Replaced", o.getDeletedReason());
    }

    @Test void anEncryptedRowCarriesItsKeyId() {
        StoredObject o = StoredObject.written(UUID.randomUUID(), UUID.randomUUID(), "evidence", "k", "database", null, 5, sha, 37, sha, "app-key-1");
        assertTrue(o.isEncrypted());
        assertEquals("app-key-1", o.getKeyId());
    }
}
