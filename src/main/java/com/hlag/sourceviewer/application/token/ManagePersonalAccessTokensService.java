package com.hlag.sourceviewer.application.token;

import com.hlag.sourceviewer.domain.model.identifier.PersonalAccessTokenIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.PrincipalName;
import com.hlag.sourceviewer.domain.model.identifier.TokenHash;
import com.hlag.sourceviewer.domain.model.token.PersonalAccessToken;
import com.hlag.sourceviewer.domain.port.incoming.ManagePersonalAccessTokensUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.PersonalAccessTokenStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Application service for managing personal access tokens.
 */
@ApplicationScoped
public class ManagePersonalAccessTokensService implements ManagePersonalAccessTokensUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ManagePersonalAccessTokensService.class);
    private static final String TOKEN_PREFIX = "svt_";
    private static final int TOKEN_BYTES = 32;

    private final PersonalAccessTokenStore tokenStore;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public ManagePersonalAccessTokensService(PersonalAccessTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /** @inheritDoc */
    @Override
    public List<PersonalAccessToken> listTokens(PrincipalName owner) {
        return tokenStore.findByOwner(owner);
    }

    /** @inheritDoc */
    @Override
    public TokenCreationResult createToken(CreateTokenCommand command) {
        logger.info("Creating personal access token '{}' for '{}'",
                command.name().value(), command.owner().value());

        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TokenHash hash = new TokenHash(sha256Hex(rawToken));

        var token = new PersonalAccessToken(
                command.owner(),
                command.name(),
                hash,
                Instant.now(),
                command.expiresAt()
        );
        tokenStore.insert(token);
        return new TokenCreationResult(token, rawToken);
    }

    /** @inheritDoc */
    @Override
    public void revokeToken(PersonalAccessTokenIdentifier identifier, PrincipalName owner) {
        logger.info("Revoking personal access token {} for '{}'", identifier.value(), owner.value());
        tokenStore.delete(identifier, owner);
    }

    /**
     * Returns the SHA-256 hex digest of the given string.
     *
     * @param input the raw token value to hash
     * @return 64-character lowercase hex string
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
