package greenecomall.finance.repo;

import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletOwnerType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerTypeAndOwnerRefAndCurrency(WalletOwnerType ownerType, String ownerRef, String currency);

    @Query("""
            select w from Wallet w, WalletOwner o
            where o.ownerType = w.ownerType and o.ownerRef = w.ownerRef and o.userId = :userId
            order by w.ownerType, w.ownerRef, w.currency""")
    List<Wallet> findOwnedByUser(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Wallet> findWithLockById(UUID id);
}
