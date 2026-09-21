package greenecomall.auth.account;

import greenecomall.auth.config.AuthProperties;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.repo.RoleRepository;
import greenecomall.auth.repo.UserRepository;
import greenecomall.auth.support.Contacts;
import greenecomall.common.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Первый администратор: без него в свежую базу некому войти в админку (регистрация снаружи даёт
 * только роль клиента). Создаётся при старте, если в настройках задан
 * {@code auth.bootstrap-admin.email/password} и такого пользователя ещё нет. Пароль берётся
 * из переменной окружения и в репозиторий не попадает; на проде его нужно сменить после первого входа.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties props;

    public BootstrapAdmin(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          AuthProperties props) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin cfg = props.bootstrapAdmin();
        if (cfg == null || cfg.email() == null || cfg.email().isBlank()
                || cfg.password() == null || cfg.password().isBlank()) {
            return;
        }
        String email = Contacts.normalize(cfg.email());
        if (users.existsByEmail(email)) {
            return;
        }
        User admin = User.forLocalRegistration(email, null, passwordEncoder.encode(cfg.password()), "ru");
        admin.activate();
        admin.addRole(requireRole(Roles.ADMIN));
        if (cfg.superAdmin()) {
            admin.addRole(requireRole(Roles.SUPER_ADMIN));
        }
        users.save(admin);
        log.warn("created the first admin account {} from auth.bootstrap-admin — change this password after login", email);
    }

    private Role requireRole(String code) {
        return roles.findByCode(code).orElseThrow(() -> new IllegalStateException("role " + code + " is missing"));
    }
}
