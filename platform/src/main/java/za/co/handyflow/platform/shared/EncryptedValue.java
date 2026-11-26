package za.co.handyflow.platform.shared;

/**
 * The stored form of an encrypted column value: a version prefix followed by what
 * {@link FieldEncryptionService#encrypt} returns. The prefix lets code tell an encrypted value from a
 * plain one that was saved before the column was encrypted, so old rows keep reading until they are converted.
 */
public final class EncryptedValue {

    public static final String PREFIX = "enc1:";

    private EncryptedValue() {}

    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /** Adds the prefix to ciphertext. Null stays null. */
    public static String wrap(String ciphertext) {
        return ciphertext == null ? null : PREFIX + ciphertext;
    }

    /** Removes the prefix. Only call for a value where {@link #isEncrypted} is true. */
    public static String unwrap(String stored) {
        return stored.substring(PREFIX.length());
    }
}
