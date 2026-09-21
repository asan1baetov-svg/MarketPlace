package greenecomall.auth.account;

import greenecomall.auth.config.AuthProperties;
import greenecomall.auth.domain.Role;
import greenecomall.auth.domain.User;
import greenecomall.auth.domain.UserStatus;
import greenecomall.auth.repo.RoleRepository;
import greenecomall.auth.repo.UserRepository;
import greenecomall.common.security.Roles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Первый вход в админку: аккаунт заводится из настроек, если его ещё нет. */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminTest {

    @Mock private UserRepository users;
    @Mock private RoleRepository roles;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private BootstrapAdmin bootstrap(String email, String password, boolean superAdmin) {
        lenient().when(roles.findByCode(Roles.ADMIN)).thenReturn(Optional.of(new Role((short) 5, Roles.ADMIN)));
        lenient().when(roles.findByCode(Roles.SUPER_ADMIN))
                .thenReturn(Optional.of(new Role((short) 6, Roles.SUPER_ADMIN)));
        AuthProperties props = new AuthProperties(null, null, null, null, null, null,
                new AuthProperties.BootstrapAdmin(email, password, superAdmin));
        return new BootstrapAdmin(users, roles, encoder, props);
    }

    @Test
    void createsActiveAdminWithHashedPassword() {
        when(users.existsByEmail("admin@greenecomall.kg")).thenReturn(false);

        bootstrap("  Admin@GreenEcoMall.kg ", "s3cret-pass", true).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User admin = saved.getValue();
        assertThat(admin.getEmail()).isEqualTo("admin@greenecomall.kg");
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isNotEqualTo("s3cret-pass");
        assertThat(encoder.matches("s3cret-pass", admin.getPasswordHash())).isTrue();
        Set<String> codes = admin.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder(Roles.ADMIN, Roles.SUPER_ADMIN);
    }

    @Test
    void onlyAdminRoleWhenSuperAdminIsOff() {
        when(users.existsByEmail("admin@greenecomall.kg")).thenReturn(false);

        bootstrap("admin@greenecomall.kg", "pass", false).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRoles()).extracting(Role::getCode).containsExactly(Roles.ADMIN);
    }

    @Test
    void doesNothingWithoutSettingsOrWhenAdminAlreadyExists() {
        bootstrap(null, null, true).run(null);
        bootstrap("admin@greenecomall.kg", "  ", true).run(null);
        when(users.existsByEmail("admin@greenecomall.kg")).thenReturn(true);
        bootstrap("admin@greenecomall.kg", "pass", true).run(null);

        verify(users, never()).save(any());
    }
}
