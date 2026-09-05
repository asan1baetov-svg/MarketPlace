package greenecomall.auth.repo;

import greenecomall.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    /** Вход по логину, который может быть либо email, либо телефоном. */
    default Optional<User> findByLogin(String login) {
        return findByEmail(login).or(() -> findByPhone(login));
    }
}
