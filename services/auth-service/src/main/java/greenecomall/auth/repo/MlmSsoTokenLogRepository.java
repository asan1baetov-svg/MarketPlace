package greenecomall.auth.repo;

import greenecomall.auth.domain.MlmSsoTokenLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Ключ — jti входящего SSO-токена. Наличие записи = токен уже предъявлялся. */
public interface MlmSsoTokenLogRepository extends JpaRepository<MlmSsoTokenLog, String> {
}
