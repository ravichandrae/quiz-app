package org.schoolmela.quiz.user;

import java.util.regex.Pattern;
import org.schoolmela.quiz.auth.AuthService;
import org.schoolmela.quiz.config.BootstrapAdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from {@code app.bootstrap-admin.*} if no user has that mobile number yet.
 * An existing account is never modified.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties props;
    private final UserRepository users;
    private final AuthService authService;

    public AdminBootstrap(BootstrapAdminProperties props, UserRepository users, AuthService authService) {
        this.props = props;
        this.users = users;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String mobile = Credentials.blankToNull(props.mobile());
        String pin = Credentials.blankToNull(props.pin());
        if (mobile == null || pin == null) {
            log.info("No bootstrap admin configured (set BOOTSTRAP_ADMIN_MOBILE and BOOTSTRAP_ADMIN_PIN to create one)");
            return;
        }
        if (!Pattern.matches(Credentials.MOBILE_PATTERN, mobile) || !Pattern.matches(Credentials.PIN_PATTERN, pin)) {
            throw new IllegalStateException("Bootstrap admin: mobile must be 10 digits and PIN 4 to 6 digits");
        }
        if (users.existsByMobile(mobile)) {
            return;
        }
        authService.createUser(props.name(), mobile, pin, null, null, Role.ADMIN);
        log.info("Created bootstrap admin with mobile {}", mobile);
    }
}
