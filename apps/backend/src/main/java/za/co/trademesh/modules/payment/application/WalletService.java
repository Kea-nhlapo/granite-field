package za.co.trademesh.modules.payment.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import za.co.trademesh.modules.payment.domain.EscrowRepository;
import za.co.trademesh.modules.payment.domain.EscrowTransaction;

@Service
public class WalletService {

    private static final int RECENT_TRANSACTION_LIMIT = 20;

    private final MomoClient momo;
    private final EscrowRepository escrows;

    public WalletService(MomoClient momo, EscrowRepository escrows) {
        this.momo = momo;
        this.escrows = escrows;
    }

    public WalletSnapshot snapshot(UUID businessId) {
        MomoClient.Balance collections = momo.getBalance(MomoClient.Product.COLLECTIONS);
        MomoClient.Balance disbursements = momo.getBalance(MomoClient.Product.DISBURSEMENTS);
        List<EscrowTransaction> transactions =
                escrows.findRecentTransactionsForBusiness(businessId, RECENT_TRANSACTION_LIMIT);
        return new WalletSnapshot(collections, disbursements, transactions);
    }

    public record WalletSnapshot(
            MomoClient.Balance collections,
            MomoClient.Balance disbursements,
            List<EscrowTransaction> recentTransactions) {}
}
