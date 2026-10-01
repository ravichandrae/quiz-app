package org.schoolmela.quiz.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.schoolmela.quiz.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    /** Lists students, newest first. {@code q} matches part of the name or mobile number. */
    @GetMapping("/users")
    public PageResponse<UserSummary> listStudents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listStudents(q, active, page, size);
    }

    @PatchMapping("/users/{id}")
    public UserSummary update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return service.setActive(Long.valueOf(jwt.getSubject()), id, request.active());
    }

    @PostMapping("/users/{id}/reset-pin")
    public UserSummary resetPin(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ResetPinRequest request) {
        return service.resetPin(Long.valueOf(jwt.getSubject()), id, request.pin());
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary createAdmin(@Valid @RequestBody CreateAdminRequest request) {
        return service.createAdmin(request.name(), request.mobile(), request.pin());
    }

    public record UpdateUserRequest(@NotNull Boolean active) {
    }

    public record ResetPinRequest(
            @NotNull(message = Credentials.PIN_MESSAGE)
            @Pattern(regexp = Credentials.PIN_PATTERN, message = Credentials.PIN_MESSAGE)
            String pin) {
    }

    public record CreateAdminRequest(
            @NotBlank(message = "Please enter a name")
            @Size(max = 100, message = "Name is too long")
            String name,
            @NotNull(message = Credentials.MOBILE_MESSAGE)
            @Pattern(regexp = Credentials.MOBILE_PATTERN, message = Credentials.MOBILE_MESSAGE)
            String mobile,
            @NotNull(message = Credentials.PIN_MESSAGE)
            @Pattern(regexp = Credentials.PIN_PATTERN, message = Credentials.PIN_MESSAGE)
            String pin) {
    }
}
