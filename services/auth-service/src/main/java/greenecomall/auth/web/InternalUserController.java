package greenecomall.auth.web;

import greenecomall.auth.account.InternalUserService;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.web.dto.InternalAddRoleRequest;
import greenecomall.auth.web.dto.InternalCreateUserRequest;
import greenecomall.auth.web.dto.InternalCreateUserResponse;
import greenecomall.auth.web.dto.InternalUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Служебное API для других сервисов (не через gateway) — регистрация магазина/курьера заводит
 * пользователя здесь. Доступ — только по {@code X-Internal-Token}, см. {@code SecurityConfig}.
 * Контракт — docs/TASK-01-auth-service.md §3.3.
 */
@RestController
@RequestMapping("/internal/auth/users")
public class InternalUserController {

    private final InternalUserService internalUserService;

    public InternalUserController(InternalUserService internalUserService) {
        this.internalUserService = internalUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InternalCreateUserResponse create(@Valid @RequestBody InternalCreateUserRequest request) {
        UUID userId = internalUserService.createUser(
                request.email(), request.phone(), request.role(), parseStatus(request.status()));
        return new InternalCreateUserResponse(userId);
    }

    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addRole(@PathVariable UUID id, @Valid @RequestBody InternalAddRoleRequest request) {
        internalUserService.addRole(id, request.role());
    }

    @GetMapping("/{id}")
    public InternalUserResponse get(@PathVariable UUID id) {
        return InternalUserResponse.from(internalUserService.findUser(id));
    }

    private static UserStatus parseStatus(String raw) {
        return raw == null || raw.isBlank() ? null : UserStatus.valueOf(raw.trim().toUpperCase());
    }
}
