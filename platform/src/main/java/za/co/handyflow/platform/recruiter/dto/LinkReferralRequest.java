package za.co.handyflow.platform.recruiter.dto;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Single combined update — staff can set referredByUserId + bonusAmount
 * together (linking the referral), and/or bonusStatus separately (moving
 * it through PENDING -> APPROVED -> PAID) in the same call. null fields
 * are left unchanged, not cleared — see RecruiterService.updateReferral()
 * for the exact semantics.
 */
public record LinkReferralRequest(
        UUID       referredByUserId,
        BigDecimal bonusAmount,
        String     bonusStatus   // NOT_SET | PENDING | APPROVED | PAID
) {}