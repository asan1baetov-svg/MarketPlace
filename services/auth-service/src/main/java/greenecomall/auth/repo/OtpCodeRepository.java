package greenecomall.auth.repo;

import greenecomall.auth.domain.OtpCode;
import greenecomall.auth.domain.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    /** Последний выпущенный код по цели+назначению — для контроля частоты запросов. */
    Optional<OtpCode> findFirstByTargetAndPurposeOrderByCreatedAtDesc(String target, OtpPurpose purpose);

    /** Последний ещё не использованный код — кандидат на проверку. */
    Optional<OtpCode> findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String target, OtpPurpose purpose);
}
