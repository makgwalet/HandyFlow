package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The record that a signing attempt was withdrawn: who, when, why, and what was revoked. Written once and never changed
 * (the database refuses updates and deletes). Views, OTP attempts and signatures stay in their own tables.
 */
@Entity
@Table(name = "contract_withdrawals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractWithdrawal {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "contract_id") private UUID contractId;
    @Column(name = "withdrawn_by") private UUID withdrawnBy;
    @Column(name = "withdrawn_at") private Instant withdrawnAt;
    private String reason;
    @Column(name = "body_hash") private String bodyHash;
    @Column(name = "links_revoked") private int linksRevoked;
    @Column(name = "otp_challenges_cleared") private int otpChallengesCleared;
    @Column(name = "parties_signed") private int partiesSigned;
    @Column(name = "parties_total") private int partiesTotal;
    @Column(name = "approval_id") private UUID approvalId;

    public static ContractWithdrawal of(UUID tenantId, UUID contractId, UUID by, String reason, String bodyHash,
                                        int linksRevoked, int otpCleared, int partiesSigned, int partiesTotal) {
        ContractWithdrawal w = new ContractWithdrawal();
        w.id = UUID.randomUUID();
        w.tenantId = tenantId;
        w.contractId = contractId;
        w.withdrawnBy = by;
        w.withdrawnAt = Instant.now();
        w.reason = reason;
        w.bodyHash = bodyHash;
        w.linksRevoked = linksRevoked;
        w.otpChallengesCleared = otpCleared;
        w.partiesSigned = partiesSigned;
        w.partiesTotal = partiesTotal;
        return w;
    }
}
