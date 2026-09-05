package greenecomall.auth.web;

import greenecomall.auth.mlm.MlmSsoService;
import greenecomall.auth.web.dto.SsoRequest;
import greenecomall.auth.web.dto.SsoTokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Вход клиента из внешней MLM-системы по SSO-токену. Контракт — docs/TASK-01-auth-service.md §3.2, §4. */
@RestController
@RequestMapping("/auth/sso")
public class MlmSsoController {

    private final MlmSsoService mlmSsoService;

    public MlmSsoController(MlmSsoService mlmSsoService) {
        this.mlmSsoService = mlmSsoService;
    }

    @PostMapping("/mlm")
    public SsoTokenResponse ssoMlm(@Valid @RequestBody SsoRequest request) {
        return SsoTokenResponse.from(mlmSsoService.login(request.token()));
    }
}
