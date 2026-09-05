package greenecomall.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Роль RBAC. Справочная таблица с фиксированным набором строк (заполняется миграцией V002).
 * Коды совпадают с константами {@code greenecomall.common.security.Roles}.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    protected Role() {
    }

    public Role(Short id, String code) {
        this.id = id;
        this.code = code;
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Role role)) {
            return false;
        }
        return Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
