package za.co.handyflow.platform.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Encrypts a String column on the way to the database and decrypts it on the way back, with
 * {@link FieldEncryptionService} (AES-256-GCM, key {@code app.security.encryption.key}).
 * Values saved before a column was encrypted have no {@link EncryptedValue#PREFIX} and are read as they are;
 * every write stores the encrypted form.
 * <p>
 * Use with {@code @Convert(converter = EncryptedStringConverter.class)}. The service is held in a static field
 * because Hibernate may create the converter itself; it is set when Spring creates this bean at startup.
 * Encrypted columns cannot be searched or sorted in SQL, and the column must be TEXT or long enough for the ciphertext.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static volatile FieldEncryptionService encryption;

    public EncryptedStringConverter() {}

    @Autowired
    public EncryptedStringConverter(FieldEncryptionService service) {
        encryption = service;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return EncryptedValue.wrap(service().encrypt(attribute));
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) return null;
        if (!EncryptedValue.isEncrypted(stored)) return stored; // saved before encryption was switched on
        return service().decrypt(EncryptedValue.unwrap(stored));
    }

    private static FieldEncryptionService service() {
        FieldEncryptionService s = encryption;
        if (s == null) throw new IllegalStateException("FieldEncryptionService is not available yet");
        return s;
    }
}
