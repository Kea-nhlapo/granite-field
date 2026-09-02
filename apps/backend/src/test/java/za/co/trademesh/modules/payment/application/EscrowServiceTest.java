package za.co.trademesh.modules.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.delivery.events.DeliveryEvent;
import za.co.trademesh.modules.handover.application.DeliveryReleaseGate;
import za.co.trademesh.modules.payment.domain.Escrow;
import za.co.trademesh.modules.payment.domain.EscrowRepository;
import za.co.trademesh.modules.payment.domain.EscrowStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransaction;
import za.co.trademesh.modules.payment.domain.EscrowTransactionStatus;
import za.co.trademesh.modules.payment.domain.EscrowTransactionType;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.modules.shipment.application.ShipmentEscrowCatalog;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.security.SensitiveDataProtector;

class EscrowServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void lockAndReleaseUseIdempotentProviderTransactions() {
        UUID proposalId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        InMemoryEscrowRepository repository = new InMemoryEscrowRepository();
        EscrowContextResolver contexts = mock(EscrowContextResolver.class);
        ShipmentEscrowCatalog shipments = mock(ShipmentEscrowCatalog.class);
        EscrowOutboxRequests requests = mock(EscrowOutboxRequests.class);
        DomainEvents events = mock(DomainEvents.class);
        SensitiveDataProtector protector = new PrefixProtector();
        when(contexts.resolve(proposalId, shipmentId, businessId))
                .thenReturn(new EscrowContextResolver.LockContext(
                        shipmentId,
                        businessId,
                        supplierId,
                        "ZAR",
                        new BigDecimal("8500.00"),
                        "+27825550100",
                        "+27825550200"));
        when(shipments.find(businessId, shipmentId))
                .thenReturn(Optional.of(new ShipmentEscrowCatalog.ShipmentEscrow(
                        shipmentId, businessId, List.of(UUID.randomUUID()), true, false)));
        EscrowService service = new EscrowService(
                repository,
                contexts,
                shipments,
                mock(DeliveryReleaseGate.class),
                protector,
                requests,
                new EscrowProperties(Duration.ofSeconds(2), Duration.ofMinutes(2), Duration.ofMinutes(30)),
                events,
                Clock.fixed(NOW, ZoneOffset.UTC));

        EscrowSnapshot requested =
                service.prepareLock(new DeliveryEvent.DeliveryAccepted(proposalId, shipmentId, businessId));
        UUID lockTransactionId = requested.transactions().getFirst().transactionId();
        service.markPending(lockTransactionId);
        service.complete(lockTransactionId, MomoClient.TransactionStatus.SUCCESSFUL);
        assertThat(service.get(businessId, shipmentId).status()).isEqualTo(EscrowStatus.LOCKED);

        UUID releaseRequestId = UUID.randomUUID();
        EscrowSnapshot release = service.release(businessId, shipmentId, releaseRequestId, new BigDecimal("8000.00"));
        EscrowSnapshot duplicate = service.release(businessId, shipmentId, releaseRequestId, new BigDecimal("8000.00"));
        assertThat(release.transactions()).hasSize(2);
        assertThat(duplicate.transactions()).hasSize(2);
        UUID releaseTransactionId = release.transactions().getLast().transactionId();
        service.markPending(releaseTransactionId);
        service.complete(releaseTransactionId, MomoClient.TransactionStatus.SUCCESSFUL);

        assertThat(service.get(businessId, shipmentId).status()).isEqualTo(EscrowStatus.RELEASED);
        verify(requests, times(2)).submit(any());
        verify(events).publish(any(PaymentEvent.Locked.class));
        verify(events).publish(any(PaymentEvent.Released.class));
    }

    @Test
    void disputedDeliveryMustBeResolvedBeforeReleaseIsQueued() {
        UUID proposalId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID businessId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        InMemoryEscrowRepository repository = new InMemoryEscrowRepository();
        EscrowContextResolver contexts = mock(EscrowContextResolver.class);
        ShipmentEscrowCatalog shipments = mock(ShipmentEscrowCatalog.class);
        DeliveryReleaseGate releaseGate = mock(DeliveryReleaseGate.class);
        EscrowOutboxRequests requests = mock(EscrowOutboxRequests.class);
        when(contexts.resolve(proposalId, shipmentId, businessId))
                .thenReturn(new EscrowContextResolver.LockContext(
                        shipmentId,
                        businessId,
                        UUID.randomUUID(),
                        "ZAR",
                        new BigDecimal("8500.00"),
                        "+27825550100",
                        "+27825550200"));
        when(shipments.find(businessId, shipmentId))
                .thenReturn(Optional.of(new ShipmentEscrowCatalog.ShipmentEscrow(
                        shipmentId, businessId, List.of(orderId), false, true)));
        when(releaseGate.releaseAllowed(businessId, shipmentId, List.of(orderId)))
                .thenReturn(true);
        EscrowService service = new EscrowService(
                repository,
                contexts,
                shipments,
                releaseGate,
                new PrefixProtector(),
                requests,
                new EscrowProperties(Duration.ofSeconds(2), Duration.ofMinutes(2), Duration.ofMinutes(30)),
                mock(DomainEvents.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        EscrowSnapshot requested =
                service.prepareLock(new DeliveryEvent.DeliveryAccepted(proposalId, shipmentId, businessId));
        UUID lockTransactionId = requested.transactions().getFirst().transactionId();
        service.markPending(lockTransactionId);
        service.complete(lockTransactionId, MomoClient.TransactionStatus.SUCCESSFUL);
        UUID resolutionCommand = UUID.randomUUID();

        EscrowSnapshot resolved = service.resolveAndRelease(
                businessId, shipmentId, resolutionCommand, new BigDecimal("7800.00"), actorUserId);

        assertThat(resolved.status()).isEqualTo(EscrowStatus.RELEASE_REQUESTED);
        assertThat(resolved.transactions().getLast().amount()).isEqualByComparingTo("7800.0000");
        verify(releaseGate).resolve(businessId, shipmentId, resolutionCommand, new BigDecimal("7800.00"), actorUserId);
        verify(releaseGate).releaseAllowed(businessId, shipmentId, List.of(orderId));
        verify(requests, times(2)).submit(any());
    }

    private static final class PrefixProtector implements SensitiveDataProtector {

        @Override
        public String protect(String plainText) {
            return "protected:" + plainText;
        }

        @Override
        public String unprotect(String protectedText) {
            return protectedText.substring("protected:".length());
        }
    }

    private static final class InMemoryEscrowRepository implements EscrowRepository {

        private final Map<UUID, Escrow> escrows = new HashMap<>();
        private final Map<UUID, EscrowTransaction> transactions = new HashMap<>();

        @Override
        public boolean saveInitial(Escrow escrow, EscrowTransaction transaction) {
            if (find(escrow.businessId(), escrow.shipmentId()).isPresent()) {
                return false;
            }
            escrows.put(escrow.id(), escrow);
            transactions.put(transaction.id(), transaction);
            return true;
        }

        @Override
        public Optional<Escrow> find(UUID businessId, UUID shipmentId) {
            return escrows.values().stream()
                    .filter(value -> value.businessId().equals(businessId)
                            && value.shipmentId().equals(shipmentId))
                    .findFirst();
        }

        @Override
        public Optional<Escrow> findById(UUID escrowId) {
            return Optional.ofNullable(escrows.get(escrowId));
        }

        @Override
        public Optional<Escrow> findForUpdate(UUID businessId, UUID shipmentId) {
            return find(businessId, shipmentId);
        }

        @Override
        public Optional<EscrowTransaction> findTransaction(UUID transactionId) {
            return Optional.ofNullable(transactions.get(transactionId));
        }

        @Override
        public Optional<EscrowTransaction> findTransactionByCommand(
                UUID escrowId, EscrowTransactionType type, UUID commandId) {
            return transactions.values().stream()
                    .filter(value -> value.escrowId().equals(escrowId)
                            && value.type() == type
                            && value.commandId().equals(commandId))
                    .findFirst();
        }

        @Override
        public Optional<EscrowTransaction> findLatestTransaction(UUID escrowId, EscrowTransactionType type) {
            return transactions.values().stream()
                    .filter(value -> value.escrowId().equals(escrowId) && value.type() == type)
                    .max(Comparator.comparingInt(EscrowTransaction::sequence));
        }

        @Override
        public List<EscrowTransaction> findTransactions(UUID escrowId) {
            List<EscrowTransaction> values = new ArrayList<>(transactions.values().stream()
                    .filter(value -> value.escrowId().equals(escrowId))
                    .toList());
            values.sort(Comparator.comparing(EscrowTransaction::createdAt)
                    .thenComparing(EscrowTransaction::type)
                    .thenComparingInt(EscrowTransaction::sequence));
            return values;
        }

        @Override
        public int nextSequence(UUID escrowId, EscrowTransactionType type) {
            return findLatestTransaction(escrowId, type)
                    .map(value -> value.sequence() + 1)
                    .orElse(0);
        }

        @Override
        public boolean addTransaction(EscrowTransaction transaction) {
            if (findTransactionByCommand(transaction.escrowId(), transaction.type(), transaction.commandId())
                    .isPresent()) {
                return false;
            }
            transactions.put(transaction.id(), transaction);
            return true;
        }

        @Override
        public boolean updateEscrowStatus(
                UUID escrowId, EscrowStatus expected, EscrowStatus target, Instant updatedAt) {
            Escrow current = escrows.get(escrowId);
            if (current == null || current.status() != expected) {
                return false;
            }
            escrows.put(
                    escrowId,
                    new Escrow(
                            current.id(),
                            current.shipmentId(),
                            current.businessId(),
                            current.supplierProfileId(),
                            current.protectedSupplierPhone(),
                            current.currency(),
                            current.agreedAmount(),
                            target,
                            current.createdAt(),
                            updatedAt));
            return true;
        }

        @Override
        public boolean updateTransactionStatus(
                UUID transactionId,
                EscrowTransactionStatus expected,
                EscrowTransactionStatus target,
                String failureCode,
                Instant updatedAt) {
            EscrowTransaction current = transactions.get(transactionId);
            if (current == null || current.status() != expected) {
                return false;
            }
            transactions.put(
                    transactionId,
                    new EscrowTransaction(
                            current.id(),
                            current.escrowId(),
                            current.commandId(),
                            current.type(),
                            current.sequence(),
                            current.providerReference(),
                            current.protectedPhone(),
                            current.amount(),
                            target,
                            current.deadlineAt(),
                            failureCode,
                            current.createdAt(),
                            updatedAt));
            return true;
        }
    }
}
