package za.co.trademesh.modules.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.payment.application.MomoClient;
import za.co.trademesh.modules.payment.application.WalletService;
import za.co.trademesh.modules.payment.domain.EscrowTransaction;
import za.co.trademesh.modules.payment.domain.EscrowTransactionStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionType;

final class WalletContracts {

    private WalletContracts() {}

    record BalanceResponse(BigDecimal availableBalance, String currency) {

        static BalanceResponse from(MomoClient.Balance balance) {
            return new BalanceResponse(balance.availableBalance(), balance.currency());
        }
    }

    record WalletTransactionResponse(
            UUID transactionId,
            EscrowTransactionType type,
            BigDecimal amount,
            EscrowTransactionStatus status,
            Instant updatedAt) {

        static WalletTransactionResponse from(EscrowTransaction transaction) {
            return new WalletTransactionResponse(
                    transaction.id(),
                    transaction.type(),
                    transaction.amount(),
                    transaction.status(),
                    transaction.updatedAt());
        }
    }

    record WalletResponse(
            BalanceResponse collections,
            BalanceResponse disbursements,
            List<WalletTransactionResponse> recentTransactions) {

        static WalletResponse from(WalletService.WalletSnapshot snapshot) {
            return new WalletResponse(
                    BalanceResponse.from(snapshot.collections()),
                    BalanceResponse.from(snapshot.disbursements()),
                    snapshot.recentTransactions().stream()
                            .map(WalletTransactionResponse::from)
                            .toList());
        }
    }
}
