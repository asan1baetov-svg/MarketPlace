package greenecomall.finance.repo;

import greenecomall.finance.domain.WalletOwner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletOwnerRepository extends JpaRepository<WalletOwner, WalletOwner.Key> {
}
