package greenecomall.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Учётная запись — единственный источник истины по пользователю для всей платформы.
 * У пользователя есть либо email, либо телефон (проверяется на уровне БД).
 * Пароль хранится только как bcrypt/argon2-хэш; у SSO-пользователей MLM он null.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(length = 320, unique = true)
    private String email;

    @Column(length = 32, unique = true)
    private String phone;

    @Column(name = "password_hash", length = 200)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 20)
    private ClientType clientType = ClientType.EXTERNAL;

    @Column(nullable = false, length = 8)
    private String locale = "ru";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Роли грузятся сразу — они нужны почти на каждом обращении к пользователю
     * (сборка access-JWT), а их не больше шести.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public static User forLocalRegistration(String email, String phone, String passwordHash, String locale) {
        User u = new User();
        u.email = email;
        u.phone = phone;
        u.passwordHash = passwordHash;
        u.status = UserStatus.PENDING;
        u.clientType = ClientType.EXTERNAL;
        if (locale != null && !locale.isBlank()) {
            u.locale = locale;
        }
        return u;
    }

    public static User forMlmSso(String email, String phone, String locale) {
        User u = new User();
        u.email = email;
        u.phone = phone;
        u.passwordHash = null;
        u.status = UserStatus.ACTIVE;
        u.clientType = ClientType.INTERNAL_MLM;
        if (locale != null && !locale.isBlank()) {
            u.locale = locale;
        }
        return u;
    }

    /**
     * Служебное создание аккаунта другим сервисом (например, catalog/courier при регистрации
     * магазина/курьера) — без пароля, роль назначается вызывающим кодом.
     */
    public static User forInternalProvisioning(String email, String phone, UserStatus status) {
        User u = new User();
        u.email = email;
        u.phone = phone;
        u.passwordHash = null;
        u.status = status != null ? status : UserStatus.PENDING;
        u.clientType = ClientType.EXTERNAL;
        return u;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public void removeRole(Role role) {
        roles.remove(role);
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void block() {
        this.status = UserStatus.BLOCKED;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public ClientType getClientType() {
        return clientType;
    }

    public String getLocale() {
        return locale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User user)) {
            return false;
        }
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
