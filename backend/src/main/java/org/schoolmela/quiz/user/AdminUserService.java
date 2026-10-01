package org.schoolmela.quiz.user;

import static org.schoolmela.quiz.user.Credentials.blankToNull;

import java.util.Locale;
import org.schoolmela.quiz.auth.AuthService;
import org.schoolmela.quiz.auth.TokenService;
import org.schoolmela.quiz.common.ApiException;
import org.schoolmela.quiz.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private final UserRepository users;
    private final AuthService authService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository users, AuthService authService, TokenService tokenService,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.authService = authService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> listStudents(String query, Boolean active, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        Specification<User> spec = (root, cq, cb) -> cb.equal(root.get("role"), Role.STUDENT);
        if (active != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("active"), active));
        }
        String q = blankToNull(query);
        if (q != null) {
            String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(root.get("mobile"), pattern)));
        }
        return PageResponse.from(users.findAll(spec, pageRequest), UserSummary::from);
    }

    /**
     * Turns an account on or off. Turning it off ends the user's sessions (their current access
     * token stays valid until it expires); turning it on also clears any lockout.
     */
    @Transactional
    public UserSummary setActive(Long adminId, Long userId, boolean active) {
        if (adminId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CHANGE_SELF", "You cannot turn off your own account.");
        }
        User user = findUser(userId);
        user.setActive(active);
        if (active) {
            user.clearFailedLogins();
        } else {
            tokenService.revokeAll(user);
        }
        return UserSummary.from(user);
    }

    /**
     * Sets a new PIN for a user who forgot theirs. Also clears any lockout and logs the user out
     * everywhere, so only someone with the new PIN can get in.
     */
    @Transactional
    public UserSummary resetPin(Long adminId, Long userId, String pin) {
        if (adminId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_CHANGE_SELF",
                    "You cannot reset your own PIN here. Ask another admin.");
        }
        User user = findUser(userId);
        user.changePin(passwordEncoder.encode(pin));
        tokenService.revokeAll(user);
        return UserSummary.from(user);
    }

    private User findUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    }

    @Transactional
    public UserSummary createAdmin(String name, String mobile, String pin) {
        return UserSummary.from(authService.createUser(name, mobile, pin, null, null, Role.ADMIN));
    }
}
