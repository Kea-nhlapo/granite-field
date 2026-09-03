package za.co.trademesh.modules.payment.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
@ConditionalOnProperty(prefix = "trademesh.sandbox-wallet", name = "enabled", havingValue = "true")
class SandboxWalletEventListener {

    private final SandboxWalletService wallets;

    SandboxWalletEventListener(SandboxWalletService wallets) {
        this.wallets = wallets;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEscrowLocked(PublishedEvent<PaymentEvent.Locked> published) {
        PaymentEvent.Locked event = published.event();
        wallets.holdForBusiness(published.envelope().eventId(), event.businessId(), event.amount(), event.currency());
    }
}
