package za.co.handyflow.platform.contracting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_parties")
@Getter
@NoArgsConstructor
public class ContractParty {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "contract_id")   UUID contractId;
    @Column(name = "party_type")    String partyType;
    @Column(name = "party_role")    String partyRole;
    @Column(name = "full_name")     String fullName;
    String email;
    String phone;
    @Column(name = "id_number")     String idNumber;
    @Column(name = "company_name")  String companyName;
    @Column(name = "signing_order") int signingOrder = 1;
    @Column(name = "signing_status") String signingStatus = "PENDING";
    @Column(name = "signed_at")     Instant signedAt;
    @Column(name = "sign_ip_address") String signIpAddress;
    @Column(name = "sign_user_agent") String signUserAgent;
    @Column(name = "otp_sent_at")   Instant otpSentAt;
    @Column(name = "otp_verified_at") Instant otpVerifiedAt;
    @Column(name = "created_at")    Instant createdAt;
    @Column(name = "updated_at")    Instant updatedAt;

    @Column(name = "signing_token", length = 512)
   private String signingToken;

   @Column(name = "signing_token_sent_at")
   private Instant signingTokenSentAt;

   @Column(name = "email_sent_at")
   private Instant emailSentAt;

   @Column(name = "declined_at")
   private Instant declinedAt;

   @Column(name = "decline_reason")
   private String declineReason;

   @Column(name = "viewed_at")
   private Instant viewedAt;

    public static ContractParty create(TenantId tenantId, UUID contractId,
                                       String partyType, String partyRole,
                                       String fullName, String email,
                                       String phone, String companyName,
                                       int signingOrder) {
        ContractParty p = new ContractParty();
        p.id           = UUID.randomUUID();
        p.tenantId     = tenantId.getValue();
        p.contractId   = contractId;
        p.partyType    = partyType;
        p.partyRole    = partyRole;
        p.fullName     = fullName;
        p.email        = email;
        p.phone        = phone;
        p.companyName  = companyName;
        p.signingOrder = signingOrder;
        p.signingStatus = "PENDING";
        p.createdAt    = Instant.now();
        p.updatedAt    = Instant.now();
        return p;
    }

    public void markOtpSent() {
        this.signingStatus = "SENT";
        this.otpSentAt     = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public void markSigned(String ipAddress, String userAgent) {
        this.signingStatus  = "SIGNED";
        this.signedAt       = Instant.now();
        this.otpVerifiedAt  = Instant.now();
        this.signIpAddress  = ipAddress;
        this.signUserAgent  = userAgent;
        this.updatedAt      = Instant.now();
    }

    public void decline() {
        this.signingStatus = "DECLINED";
        this.updatedAt     = Instant.now();
    }

    public void setSigningStatus(String status) { this.signingStatus = status; }
   public void setDeclinedAt(Instant t)        { this.declinedAt = t; }
   public void setDeclineReason(String r)      { this.declineReason = r; }
   public void setViewedAt(Instant t)          { this.viewedAt = t; }
   public void setSigningToken(String token)   { this.signingToken = token; }
   public void setEmailSentAt(Instant t)       { this.emailSentAt = t; }
}