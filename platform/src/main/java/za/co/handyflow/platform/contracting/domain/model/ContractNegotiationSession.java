package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A short-lived session granted after the counterparty confirmed the one-time code. Stored hashed, like the link. */
@Entity
@Table(name = "contract_negotiation_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractNegotiationSession {
    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "link_id") private UUID linkId;
    @Column(name = "session_hash") private String sessionHash;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "ip_source") private String ipSource;
    @Column(name = "created_at") private Instant createdAt;

    public static ContractNegotiationSession start(UUID tenantId, UUID linkId, String sessionHash, Instant expiresAt, String ip) {
        ContractNegotiationSession s = new ContractNegotiationSession();
        s.id = UUID.randomUUID();
        s.tenantId = tenantId; s.linkId = linkId; s.sessionHash = sessionHash; s.expiresAt = expiresAt; s.ipAddress = ip;
        s.createdAt = Instant.now();
        return s;
    }

    public ContractNegotiationSession withIpSource(String source) { this.ipSource = source; return this; }

    public boolean isActive(Instant now) { return now.isBefore(expiresAt); }
}
