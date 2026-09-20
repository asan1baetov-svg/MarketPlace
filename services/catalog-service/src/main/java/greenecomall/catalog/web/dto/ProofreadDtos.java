package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.Size;

public final class ProofreadDtos {

    private ProofreadDtos() {
    }

    public record ProofreadRequest(@Size(max = 300) String name, @Size(max = 5000) String description) {
    }

    /** @param changed было ли что исправлять — фронт показывает «Исправили ошибки» и разницу */
    public record ProofreadResponse(String name, String description, boolean changed) {
    }
}
