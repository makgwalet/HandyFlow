// shared/FieldEncryptionService.java

package za.co.handyflow.platform.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * FieldEncryptionService — application-layer AES-256-GCM encryption for
 * individual sensitive fields, distinct from disk/DB-at-rest encryption.
 *
 * Built specifically to close the gap flagged in V115 (Part 9.3): a raw DB
 * query against security_principals.medical_notes / known_threats should
 * not return plaintext. Disk-level encryption protects against someone
 * stealing the physical disk; it does nothing against a compromised DB
 * credential or an overly broad SELECT — this service is the layer that
 * still protects the data in that scenario.
 *
 * WHY AES-GCM specifically?
 * GCM is authenticated encryption — it detects tampering (a flipped bit in
 * ciphertext fails to decrypt rather than silently producing garbage
 * plaintext), unlike plain AES-CBC. For medical/threat data where integrity
 * matters as much as confidentiality, this is the right mode.
 *
 * WHY one platform-wide key rather than per-tenant keys?
 * Per-tenant key management (rotation, storage, recovery) is a significant
 * operational surface that wasn't in scope for this pass — flagged here as
 * a deliberate simplification, not an oversight. A platform-wide key in an
 * env var/secrets manager is the minimum viable version of this control;
 * per-tenant keys are the natural next step if a client's compliance
 * requirements demand tenant-level key isolation.
 *
 * STORAGE FORMAT: Base64(IV || ciphertext || authTag). The IV is randomly
 * generated per encryption call and prepended to the output — GCM requires
 * a unique IV per encryption under the same key, and storing it alongside
 * the ciphertext is the standard approach (the IV itself isn't secret).
 *
 * KEY ROTATION: not supported by this implementation. Rotating
 * app.security.encryption.key without a migration step would make all
 * previously-encrypted values undecryptable. A rotation-aware version
 * (key versioning prefix on the stored value) is a reasonable follow-up
 * if this becomes a real operational need.
 */
@Slf4j
@Service
public class FieldEncryptionService {

    private static final String  ALGORITHM       = "AES/GCM/NoPadding";
    private static final int     GCM_TAG_LENGTH  = 128;   // bits
    private static final int     GCM_IV_LENGTH   = 12;    // bytes — standard for GCM

    private final SecretKeySpec keySpec;

    public FieldEncryptionService(
            @Value("${app.security.encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.security.encryption.key must decode to exactly 32 bytes (AES-256). "
                            + "Generate one with: openssl rand -base64 32");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plaintext. Returns null if input is null (so callers can pass
     * a nullable field straight through without a null check at every site).
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("[Encryption] Field encryption failed: {}", e.getMessage());
            throw new HandyFlowException(
                    "Failed to encrypt field", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "ENCRYPTION_ERROR");
        }
    }

    /**
     * Decrypts a value produced by encrypt(). Returns null if input is null.
     *
     * WHY does decryption failure throw rather than returning the raw input?
     * If a value somehow isn't valid ciphertext (corrupted storage, a bug
     * that wrote plaintext by mistake), silently returning garbage or the
     * raw bytes risks displaying corrupted medical/threat data as if it were
     * legitimate. Failing loudly is safer for this data class than failing
     * open.
     */
    public String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv         = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[Encryption] Field decryption failed: {}", e.getMessage());
            throw new HandyFlowException(
                    "Failed to decrypt field — data may be corrupted or the encryption key changed",
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "DECRYPTION_ERROR");
        }
    }
}
