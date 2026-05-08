package com.hlag.sourceviewer.domain.port.outgoing;

import com.hlag.sourceviewer.domain.model.identifier.EncryptedSecret;
import com.hlag.sourceviewer.domain.model.identifier.SecretValue;

/**
 * Port for encrypting and decrypting Git credential secrets.
 *
 * <p>The implementation resides in the infrastructure layer and uses AES-256-GCM with a
 * key read from the application configuration. The domain and application layers depend
 * only on this interface and never on the cryptographic details.</p>
 */
public interface SecretEncryptor {

    /**
     * Encrypts a plaintext secret and returns its ciphertext representation.
     *
     * @param plaintext the raw secret to protect
     * @return AES-256-GCM ciphertext, formatted as {@code base64(iv):base64(ciphertext)}
     * @throws com.hlag.sourceviewer.infrastructure.security.SecretEncryptionException if encryption fails
     */
    EncryptedSecret encrypt(SecretValue plaintext);

    /**
     * Decrypts a previously encrypted secret and returns the plaintext.
     *
     * @param ciphertext the AES-256-GCM ciphertext to decrypt
     * @return the original plaintext secret
     * @throws com.hlag.sourceviewer.infrastructure.security.SecretEncryptionException if decryption fails
     */
    SecretValue decrypt(EncryptedSecret ciphertext);
}
