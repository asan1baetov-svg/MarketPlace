package greenecomall.finance.web;

import greenecomall.common.security.Roles;
import greenecomall.common.test.TestJwts;
import greenecomall.common.test.TestSecurityConfig;
import greenecomall.finance.config.FinanceSecurityRules;
import greenecomall.finance.config.SupportConfig;
import greenecomall.finance.domain.Payment;
import greenecomall.finance.domain.PayoutRequest;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.mlm.MlmTariffClient;
import greenecomall.finance.payment.PaymentService;
import greenecomall.finance.wallet.PayoutRequisiteService;
import greenecomall.finance.wallet.PayoutService;
import greenecomall.finance.wallet.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Модель доступа finance-service: платежи и кошельки — только свои, сумма MLM-доступа — из тарифа. */
@WebMvcTest(properties = "gem.security.internal-token=" + TestSecurityConfig.INTERNAL_TOKEN)
@Import({TestSecurityConfig.class, SupportConfig.class, FinanceSecurityRules.class})
class FinanceSecurityWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private MlmTariffClient mlmTariffs;
    @MockitoBean
    private WalletService walletService;
    @MockitoBean
    private PayoutService payoutService;
    @MockitoBean
    private PayoutRequisiteService requisiteService;

    @BeforeEach
    void paymentPageIsAlreadyCreated() {
        when(paymentService.ensureHostedPage(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void mlmAccessPayment_amountComesFromTariff_notFromClient() throws Exception {
        UUID user = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        when(mlmTariffs.payableTariff(tariffId)).thenReturn(new MlmTariffClient.Tariff(tariffId, 150_000, "KGS"));
        when(paymentService.createMlmAccessPayment(any(), any(), any(), anyLong(), any()))
                .thenReturn(Payment.forMlmAccess("mlm-42", tariffId, user, 150_000, "KGS", "mock"));

        mvc.perform(post("/payments/mlm-access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tariffId\":\"" + tariffId + "\",\"amountMinor\":1,\"currency\":\"USD\"}")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TestJwts.mlmToken(user, "mlm-42", Roles.CLIENT_MLM)))
                .andExpect(status().isCreated());
        verify(paymentService).createMlmAccessPayment("mlm-42", tariffId, user, 150_000, "KGS");
    }

    @Test
    void mlmAccessPayment_forNonMlmUser_is403() throws Exception {
        mvc.perform(post("/payments/mlm-access").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tariffId\":\"" + UUID.randomUUID() + "\"}")
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.CLIENT_EXTERNAL)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mlmTariffs, paymentService);
    }

    @Test
    void payment_ofAnotherClient_is403() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentService.get(paymentId))
                .thenReturn(Payment.forOrder(UUID.randomUUID(), UUID.randomUUID(), 1_000, "KGS", "mock"));

        mvc.perform(get("/payments/{id}", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.CLIENT_EXTERNAL)))
                .andExpect(status().isForbidden());
    }

    @Test
    void payout_fromOwnWallet_usesTokenUserAsRequester() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet(WalletOwnerType.SHOP, UUID.randomUUID().toString(), "KGS");
        when(walletService.require(walletId)).thenReturn(wallet);
        when(walletService.isOwnedBy(wallet, owner)).thenReturn(true);
        when(payoutService.request(eq(walletId), eq(5_000L), anyString(), anyString()))
                .thenReturn(new PayoutRequest(walletId, 5_000, "KGS", owner.toString(), "{}"));

        mvc.perform(post("/wallet/payouts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletId\":\"" + walletId + "\",\"amountMinor\":5000,\"bankDetails\":{}}")
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(owner, Roles.SHOP)))
                .andExpect(status().isCreated());
        verify(payoutService).request(eq(walletId), eq(5_000L), eq(owner.toString()), anyString());
    }

    @Test
    void payout_fromSomeoneElsesWallet_is403() throws Exception {
        UUID walletId = UUID.randomUUID();
        when(walletService.require(walletId)).thenReturn(new Wallet(WalletOwnerType.SHOP, "shop-x", "KGS"));

        mvc.perform(post("/wallet/payouts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletId\":\"" + walletId + "\",\"amountMinor\":5000,\"bankDetails\":{}}")
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.SHOP)))
                .andExpect(status().isForbidden());
        verify(payoutService, never()).request(any(), anyLong(), any(), any());
    }

    @Test
    void acquiringWebhook_isReachableWithoutJwt() throws Exception {
        mvc.perform(post("/webhooks/acquiring/mock").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "sig").content("{}"))
                .andExpect(status().is2xxSuccessful());
        verify(paymentService).handleWebhook(any(), eq("{}"));
    }

    @Test
    void orderPaymentCreation_isInternalOnly() throws Exception {
        mvc.perform(post("/internal/payments").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.CLIENT_EXTERNAL)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.CLIENT_EXTERNAL)))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(paymentService);
    }
}
