// security/domain/repository/CheckpointRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface CheckpointRepository extends JpaRepository<Checkpoint, UUID> {

    /**
     * QR lookup — finds a checkpoint by its QR payload.
     *
     * WHY include tenantId in the lookup? (fixes bug #11)
     * The original query had no tenant filter: findByQrCode(qrCode).
     * QR codes are UUIDs (unguessable), so collision across tenants is
     * astronomically unlikely — but it's still a defence-in-depth principle:
     * a scan from tenant A should never resolve a checkpoint from tenant B,
     * even if there was somehow a collision or if a code was shared.
     * One extra indexed column join; zero performance cost.
     */
    @Query("""
        SELECT c FROM Checkpoint c
        WHERE c.qrCode = :qrCode
        AND c.site.tenantId = :tenantId
        AND c.active = true
        """)
    Optional<Checkpoint> findByQrCode(TenantId tenantId, String qrCode);

    /**
     * NFC lookup — finds a checkpoint by its NFC tag UID.
     * Used when scanType = NFC and qrCode is null.
     *
     * WHY? The original scan service did findByQrCode(req.qrCode()) unconditionally.
     * An NFC scan comes in with qrCode=null → throws "Invalid QR code" (bug #6).
     * This method routes NFC scans to the correct identifier.
     */
    @Query("""
        SELECT c FROM Checkpoint c
        WHERE c.nfcTagUid = :nfcTagUid
        AND c.site.tenantId = :tenantId
        AND c.active = true
        """)
    Optional<Checkpoint> findByNfcTagUid(TenantId tenantId, String nfcTagUid);

    /**
     * BLE lookup — finds a checkpoint by its BLE beacon identifier.
     * Used when scanType = BLE and qrCode/nfcTagUid are null.
     */
    @Query("""
        SELECT c FROM Checkpoint c
        WHERE c.bleBeaconId = :bleBeaconId
        AND c.site.tenantId = :tenantId
        AND c.active = true
        """)
    Optional<Checkpoint> findByBleBeaconId(TenantId tenantId, String bleBeaconId);
}
