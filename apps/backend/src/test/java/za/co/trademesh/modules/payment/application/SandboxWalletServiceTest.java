package za.co.trademesh.modules.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.access.application.AccountIdentityService;
import za.co.trademesh.modules.access.application.BusinessNotificationRecipients;
import za.co.trademesh.modules.payment.domain.SandboxWallet;
import za.co.trademesh.modules.payment.domain.SandboxWalletEntry;
import za.co.trademesh.modules.payment.domain.SandboxWalletRepository;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;

class SandboxWalletServiceTest {

    @Test
    void appliesOpeningBalancesAndPaymentEventsExactlyOnce() {
        InMemoryWallets repository = new InMemoryWallets();
        AccountIdentityService accounts = mock(AccountIdentityService.class);
        BusinessNotificationRecipients businesses = mock(BusinessNotificationRecipients.class);
        SupplierDirectory suppliers = mock(SupplierDirectory.class);
        UUID buyer = UUID.randomUUID();
        UUID supplier = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID supplierProfileId = UUID.randomUUID();
        when(accounts.findEnabled(buyer))
                .thenReturn(Optional.of(
                        new AccountIdentityService.AccountIdentity(buyer, Optional.of("owner@example.com"))));
        when(accounts.findEnabled(supplier))
                .thenReturn(Optional.of(
                        new AccountIdentityService.AccountIdentity(supplier, Optional.of("new-supplier@example.com"))));
        when(businesses.findActiveUserIds(businessId)).thenReturn(List.of(buyer));
        when(suppliers.find(supplierProfileId))
                .thenReturn(
                        Optional.of(new SupplierDirectory.SupplierReference(supplierProfileId, true, supplier, null)));
        SandboxWalletService service = new SandboxWalletService(
                repository,
                accounts,
                businesses,
                suppliers,
                Clock.fixed(Instant.parse("2026-09-03T06:00:00Z"), ZoneOffset.UTC));

        assertThat(service.get(buyer).availableBalance()).isEqualByComparingTo("4237.0000");
        assertThat(service.get(supplier).availableBalance()).isEqualByComparingTo("50.0000");

        UUID lockEvent = UUID.randomUUID();
        service.holdForBusiness(lockEvent, businessId, new BigDecimal("100"), "ZAR");
        service.holdForBusiness(lockEvent, businessId, new BigDecimal("100"), "ZAR");
        assertThat(service.get(buyer).availableBalance()).isEqualByComparingTo("4137.0000");
        assertThat(service.get(buyer).heldBalance()).isEqualByComparingTo("100.0000");

        UUID releaseEvent = UUID.randomUUID();
        service.settleAndCreditSupplier(releaseEvent, businessId, supplierProfileId, new BigDecimal("100"), "ZAR");
        service.settleAndCreditSupplier(releaseEvent, businessId, supplierProfileId, new BigDecimal("100"), "ZAR");

        assertThat(service.get(buyer).heldBalance()).isEqualByComparingTo("0.0000");
        assertThat(service.get(supplier).availableBalance()).isEqualByComparingTo("150.0000");
        assertThat(service.get(supplier).entries())
                .extracting(SandboxWalletEntry::type)
                .containsExactlyInAnyOrder(
                        SandboxWalletEntry.EntryType.PAYMENT_RECEIVED, SandboxWalletEntry.EntryType.OPENING_CREDIT);
    }

    @Test
    void givesTheUniversalSupplierTheSeededBalance() {
        InMemoryWallets repository = new InMemoryWallets();
        SandboxWalletService service = new SandboxWalletService(
                repository,
                mock(AccountIdentityService.class),
                mock(BusinessNotificationRecipients.class),
                mock(SupplierDirectory.class),
                Clock.fixed(Instant.parse("2026-09-03T06:00:00Z"), ZoneOffset.UTC));

        assertThat(service.initializeLungile().availableBalance()).isEqualByComparingTo("628330.0000");
    }

    private static final class InMemoryWallets implements SandboxWalletRepository {
        private final Map<UUID, SandboxWallet> wallets = new HashMap<>();
        private final List<SandboxWalletEntry> entries = new ArrayList<>();

        @Override
        public boolean create(
                UUID userId, String displayName, String currency, BigDecimal openingBalance, Instant now) {
            return wallets.putIfAbsent(
                            userId,
                            new SandboxWallet(userId, displayName, currency, openingBalance, BigDecimal.ZERO, now))
                    == null;
        }

        @Override
        public Optional<SandboxWallet> find(UUID userId) {
            return Optional.ofNullable(wallets.get(userId));
        }

        @Override
        public Optional<SandboxWallet> findForUpdate(UUID userId) {
            return find(userId);
        }

        @Override
        public boolean entryExists(String referenceKey) {
            return entries.stream().anyMatch(entry -> entry.referenceKey().equals(referenceKey));
        }

        @Override
        public void update(UUID userId, BigDecimal availableBalance, BigDecimal heldBalance, Instant now) {
            SandboxWallet current = wallets.get(userId);
            wallets.put(
                    userId,
                    new SandboxWallet(
                            userId, current.displayName(), current.currency(), availableBalance, heldBalance, now));
        }

        @Override
        public void add(SandboxWalletEntry entry) {
            entries.add(entry);
        }

        @Override
        public List<SandboxWalletEntry> entries(UUID userId, int limit) {
            return entries.stream()
                    .filter(entry -> entry.userId().equals(userId))
                    .sorted(java.util.Comparator.comparing(SandboxWalletEntry::createdAt)
                            .reversed())
                    .limit(limit)
                    .toList();
        }
    }
}
