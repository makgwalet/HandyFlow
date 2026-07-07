package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_signatures")
@Getter
@NoArgsConstructor
public class ContractSignature {

    @Id UUID id;
    @Column(name = "tenant_id")    UUID tenantId;
    @Column(name = "contract_id")  UUID contractId;
    @Column(name = "party_id")     UUID partyId;
    @Column(name = "otp_code_hash") String otpCodeHash;
    @Column(name = "phone_last4")  String phoneLast4;
    @Column(name = "ip_address")   String ipAddress;
    @Column(name = "user_agent")   String userAgent;
    @Column(name = "signed_at")    Instant signedAt;
    @Column(name = "signature_data") String signatureData;

    // NEW: distinguishes a signature captured in-person by an authenticated
    // staff member (witnessing the party sign on a shared device) from the
    // normal remote flow where the party signs entirely themselves via an
    // emailed link. Null for the remote flow — only set when
    // ContractingService.signInPerson() records the signature.
    @Column(name = "witnessed_by_user_id")
    UUID witnessedByUserId;

    public static ContractSignature create(TenantId tenantId, UUID contractId,
                                           UUID partyId, String otpCodeHash,
                                           String phoneLast4, String ipAddress,
                                           String userAgent, String signatureData,
                                           UUID witnessedByUserId) {
        ContractSignature s = new ContractSignature();
        s.id            = UUID.randomUUID();
        s.tenantId      = tenantId.getValue();
        s.contractId    = contractId;
        s.partyId       = partyId;
        s.otpCodeHash   = otpCodeHash;
        s.phoneLast4    = phoneLast4;
        s.ipAddress     = ipAddress;
        s.userAgent     = userAgent;
        s.signatureData = signatureData;
        s.witnessedByUserId = witnessedByUserId;
        s.signedAt      = Instant.now();
        return s;
    }
}