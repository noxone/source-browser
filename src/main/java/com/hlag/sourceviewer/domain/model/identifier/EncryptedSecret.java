package com.hlag.sourceviewer.domain.model.identifier;

/**
 * AES-256-GCM encrypted ciphertext of a Git credential secret, stored in the database.
 *
 * <p>The value is formatted as {@code base64(iv):base64(ciphertext)} and is never
 * exposed in API responses. Decryption is performed exclusively by the
 * {@code SecretEncryptor} infrastructure component.</p>
 */
public record EncryptedSecret(String value) implements ValueObject<String> {
    public EncryptedSecret {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("EncryptedSecret must not be blank");
        }
    }
}
