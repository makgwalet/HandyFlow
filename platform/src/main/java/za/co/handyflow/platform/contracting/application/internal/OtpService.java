package za.co.handyflow.platform.contracting.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OtpService {

    private final SecureRandom random = new SecureRandom();
    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    private record OtpEntry(String hashedOtp, long expiresAt) {}

    public String generateAndStore(String partyId) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        String hash = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(otp, org.springframework.security.crypto.bcrypt.BCrypt.gensalt());
        store.put(partyId, new OtpEntry(hash, System.currentTimeMillis() + 10 * 60 * 1000));
        log.info("OTP generated for partyId={} otp={}", partyId, otp);
        return otp;
    }

    public boolean verify(String partyId, String otp) {
        OtpEntry entry = store.get(partyId);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiresAt()) {
            store.remove(partyId);
            return false;
        }
        boolean valid = org.springframework.security.crypto.bcrypt.BCrypt.checkpw(otp, entry.hashedOtp());
        if (valid) store.remove(partyId);
        return valid;
    }

    public String hashOtp(String otp) {
        return org.springframework.security.crypto.bcrypt.BCrypt.hashpw(otp, org.springframework.security.crypto.bcrypt.BCrypt.gensalt());
    }
}
