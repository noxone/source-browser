package com.hlag.sourceviewer.infrastructure.security;

import com.hlag.sourceviewer.domain.model.identifier.EncryptedSecret;
import com.hlag.sourceviewer.domain.model.identifier.SecretValue;
import com.hlag.sourceviewer.domain.port.outgoing.SecretEncryptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM implementation of {@link SecretEncryptor}.
 *
 * <p>Each encryption generates a fresh 12-byte random IV. The stored value is formatted
 * as {@code base64(iv):base64(ciphertext+authTag)} so that both IV and ciphertext are
 * self-contained in the single database column.</p>
 *
 * <p>The encryption key is read from the {@code sourceviewer.secret-encryption-key}
 * configuration property as a Base64-encoded 32-byte (256-bit) value.
 * Set the {@code SECRET_ENCRYPTION_KEY} environment variable in production.</p>
 */
@ApplicationScoped
public class AesGcmSecretEncryptor implements SecretEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public AesGcmSecretEncryptor(
            @ConfigProperty(name = "sourceviewer.secret-encryption-key") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "sourceviewer.secret-encryption-key must decode to exactly 32 bytes (256 bits), got "
                            + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /** @inheritDoc */
    @Override
    public EncryptedSecret encrypt(SecretValue plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.value().getBytes(StandardCharsets.UTF_8));
            String encoded = Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
            return new EncryptedSecret(encoded);
        } catch (Exception exception) {
            throw new SecretEncryptionException("Failed to encrypt secret", exception);
        }
    }

    /** @inheritDoc */
    @Override
    public SecretValue decrypt(EncryptedSecret ciphertext) {
        try {
            String[] parts = ciphertext.value().split(":", 2);
            if (parts.length != 2) {
                throw new SecretEncryptionException(
                        "Stored encrypted secret has unexpected format (expected iv:ciphertext)", null);
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encryptedBytes = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(encryptedBytes);
            return new SecretValue(new String(plaintext, StandardCharsets.UTF_8));
        } catch (SecretEncryptionException securityException) {
            throw securityException;
        } catch (Exception exception) {
            throw new SecretEncryptionException("Failed to decrypt secret", exception);
        }
    }
}
