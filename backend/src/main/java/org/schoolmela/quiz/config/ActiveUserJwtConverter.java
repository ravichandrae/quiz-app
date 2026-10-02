package org.schoolmela.quiz.config;

import java.util.List;
import org.schoolmela.quiz.user.User;
import org.schoolmela.quiz.user.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a valid access token into a login, checking the account on every request: a turned-off
 * account stops working at once (not when its token expires), and the role comes from the database.
 */
public class ActiveUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public ActiveUserJwtConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = users.findById(Long.valueOf(jwt.getSubject()))
                .filter(User::isActive)
                .orElseThrow(() -> new DisabledException("Account not found or turned off"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
