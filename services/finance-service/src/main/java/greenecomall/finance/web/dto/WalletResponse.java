package greenecomall.finance.web.dto;

import greenecomall.finance.domain.Wallet;

import java.util.UUID;

public record WalletResponse(
        UUID id, String ownerType, String ownerRef, String currency,
        long balanceMinor, long heldMinor, long availableMinor) {

    public static WalletResponse from(Wallet w) {
        return new WalletResponse(w.getId(), w.getOwnerType().name(), w.getOwnerRef(), w.getCurrency(),
                w.getBalanceMinor(), w.getHeldMinor(), w.available());
    }
}
