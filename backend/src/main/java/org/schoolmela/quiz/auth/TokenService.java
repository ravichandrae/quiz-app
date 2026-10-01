package org.schoolmela.quiz.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.schoolmela.quiz.auth.AuthDtos.AuthResponse;
import org.schoolmela.quiz.auth.AuthDtos.UserInfo;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.config.AuthProperties;
import org.schoolmela.quiz.config.SecurityConfig;
import org.schoolmela.quiz.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues access tokens (JWT) and rotating, revocable refresh tokens. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final AuthProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, RefreshTokenRepository refreshTokens, AuthProperties props, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.refreshTokens = refreshTokens;
        this.props = props;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse issueTokens(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.TOKEN_ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .claim(SecurityConfig.ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user, hash(refreshToken), now, now.plus(props.refreshTokenTtl())));

        return new AuthResponse(accessToken, refreshToken, props.accessTokenTtl().toSeconds(), UserInfo.from(user));
    }

    /** Exchanges a refresh token for a new token pair; the old refresh token stops working. */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHashForUpdate(hash(refreshToken))
                .filter(t -> t.isUsableAt(now) && t.getUser().isActive())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Please log in again."));
        token.revoke(now);
        return issueTokens(token.getUser());
    }

    @Transactional
    public void revoke(String refreshToken) {
        refreshTokens.findByTokenHash(hash(refreshToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAll(User user) {
        refreshTokens.revokeAllForUser(user.getId(), clock.instant());
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
