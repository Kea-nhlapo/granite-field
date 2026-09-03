package za.co.trademesh.modules.payment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.AccountIdentityService;
import za.co.trademesh.modules.access.application.BusinessNotificationRecipients;
import za.co.trademesh.modules.payment.domain.SandboxWallet;
import za.co.trademesh.modules.payment.domain.SandboxWalletEntry;
import za.co.trademesh.modules.payment.domain.SandboxWalletEntry.EntryType;
import za.co.trademesh.modules.payment.domain.SandboxWalletRepository;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;

@Service
public class SandboxWalletService {

    public static final UUID LUNGILE_USER_ID = UUID.fromString("6c756e67-696c-456d-8000-000000000001");
    public static final UUID LUNGILE_SUPPLIER_PROFILE_ID = UUID.fromString("6c756e67-696c-456d-8000-000000000002");
    public static final String LUNGILE_EMAIL = "lungile.mooketsi@trademesh.local";

    private static final String CURRENCY = "ZAR";
    private static final BigDecimal DEFAULT_OPENING_BALANCE = money("50");
    private static final BigDecimal SME_DEMO_OPENING_BALANCE = money("4237");
    private static final BigDecimal LUNGILE_OPENING_BALANCE = money("628330");

    private final SandboxWalletRepository wallets;
    private final AccountIdentityService accounts;
    private final BusinessNotificationRecipients businessRecipients;
    private final SupplierDirectory suppliers;
    private final Clock clock;

    public SandboxWalletService(
            SandboxWalletRepository wallets,
            AccountIdentityService accounts,
            BusinessNotificationRecipients businessRecipients,
            SupplierDirectory suppliers,
            Clock clock) {
        this.wallets = wallets;
        this.accounts = accounts;
        this.businessRecipients = businessRecipients;
        this.suppliers = suppliers;
        this.clock = clock;
    }

    @Transactional
    public SandboxWalletSnapshot get(UUID userId) {
        SandboxWallet wallet = ensureDefault(userId);
        return snapshot(wallet);
    }

    @Transactional
    public SandboxWalletSnapshot initializeLungile() {
        return snapshot(ensure(LUNGILE_USER_ID, "Lungile Mooketsi", LUNGILE_OPENING_BALANCE));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void holdForBusiness(UUID eventId, UUID businessId, BigDecimal amount, String currency) {
        businessRecipients.findActiveUserIds(businessId).stream()
                .findFirst()
                .ifPresent(userId -> mutate(
                        ensureDefault(userId),
                        "payment-event:" + eventId + ":hold",
                        EntryType.ESCROW_HELD,
                        amount(amount).negate(),
                        amount(amount),
                        "Funds moved into escrow",
                        currency));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<SandboxWalletSnapshot> settleAndCreditSupplier(
            UUID eventId, UUID businessId, UUID supplierProfileId, BigDecimal amount, String currency) {
        BigDecimal payment = amount(amount);
        businessRecipients.findActiveUserIds(businessId).stream().findFirst().ifPresent(userId -> {
            SandboxWallet buyer = ensureDefault(userId);
            BigDecimal settled = buyer.heldBalance().min(payment);
            mutate(
                    buyer,
                    "payment-event:" + eventId + ":settle",
                    EntryType.ESCROW_SETTLED,
                    BigDecimal.ZERO,
                    settled.negate(),
                    "Escrow released to supplier",
                    currency);
        });

        return suppliers
                .find(supplierProfileId)
                .map(SupplierDirectory.SupplierReference::claimedUserId)
                .map(userId -> mutate(
                        ensureDefault(userId),
                        "payment-event:" + eventId + ":supplier-credit",
                        EntryType.PAYMENT_RECEIVED,
                        payment,
                        BigDecimal.ZERO,
                        "Payment received from escrow",
                        currency))
                .map(this::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<UniversalSupplier> universalSupplier() {
        return accounts.findEnabled(LUNGILE_USER_ID)
                .map(account -> new UniversalSupplier(
                        LUNGILE_USER_ID, LUNGILE_SUPPLIER_PROFILE_ID, "Lungile Mooketsi", LUNGILE_EMAIL));
    }

    private SandboxWallet ensureDefault(UUID userId) {
        return wallets.find(userId).orElseGet(() -> {
            AccountIdentityService.AccountIdentity account =
                    accounts.findEnabled(userId).orElseThrow();
            String email = account.normalizedEmail().orElse(null);
            BigDecimal opening = "owner@example.com".equals(email) ? SME_DEMO_OPENING_BALANCE : DEFAULT_OPENING_BALANCE;
            String label = email == null ? "Sandbox account" : email;
            return ensure(userId, label, opening);
        });
    }

    private SandboxWallet ensure(UUID userId, String displayName, BigDecimal openingBalance) {
        Instant now = clock.instant();
        if (wallets.create(userId, displayName, CURRENCY, openingBalance, now)) {
            wallets.add(new SandboxWalletEntry(
                    UUID.randomUUID(),
                    userId,
                    "wallet-opening:" + userId,
                    EntryType.OPENING_CREDIT,
                    openingBalance,
                    BigDecimal.ZERO,
                    openingBalance,
                    BigDecimal.ZERO,
                    "Sandbox opening balance",
                    now));
        }
        return wallets.findForUpdate(userId).orElseThrow();
    }

    private SandboxWallet mutate(
            SandboxWallet initial,
            String reference,
            EntryType type,
            BigDecimal availableDelta,
            BigDecimal heldDelta,
            String description,
            String currency) {
        if (!CURRENCY.equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException("Sandbox wallet supports ZAR only");
        }
        SandboxWallet current = wallets.findForUpdate(initial.userId()).orElseThrow();
        if (wallets.entryExists(reference)) {
            return current;
        }
        BigDecimal available = money(current.availableBalance().add(availableDelta));
        BigDecimal held = money(current.heldBalance().add(heldDelta));
        if (held.signum() < 0) {
            held = BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        }
        Instant now = clock.instant();
        wallets.update(current.userId(), available, held, now);
        wallets.add(new SandboxWalletEntry(
                UUID.randomUUID(),
                current.userId(),
                reference,
                type,
                availableDelta,
                heldDelta,
                available,
                held,
                description,
                now));
        return new SandboxWallet(current.userId(), current.displayName(), current.currency(), available, held, now);
    }

    private SandboxWalletSnapshot snapshot(SandboxWallet wallet) {
        return SandboxWalletSnapshot.from(wallet, wallets.entries(wallet.userId(), 30));
    }

    private static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("Wallet amount must be positive");
        }
        return money(value);
    }

    private static BigDecimal money(String value) {
        return money(new BigDecimal(value));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public record UniversalSupplier(UUID userId, UUID supplierProfileId, String displayName, String loginEmail) {}
}
