package za.co.trademesh.modules.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.trademesh.modules.delivery.domain.DeliveryMobileChannel;
import za.co.trademesh.modules.delivery.domain.DeliveryProposal;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalRepository;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalStatus;
import za.co.trademesh.modules.delivery.events.DeliveryEvent;
import za.co.trademesh.modules.notification.application.MobileNotificationRequests;
import za.co.trademesh.modules.notification.application.NotificationRequests;
import za.co.trademesh.modules.shipment.application.ShipmentAccessCatalog;
import za.co.trademesh.shared.events.DomainEvents;

class DeliveryProposalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void previewDoesNotAcceptAndDuplicateConfirmationEmitsOneAcceptedEvent() {
        UUID businessId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ShipmentAccessCatalog shipments = mock(ShipmentAccessCatalog.class);
        when(shipments.findOwned(businessId, shipmentId))
                .thenReturn(Optional.of(new ShipmentAccessCatalog.ShipmentAccess(shipmentId, true)));
        InMemoryProposalRepository proposals = new InMemoryProposalRepository();
        NotificationRequests email = mock(NotificationRequests.class);
        MobileNotificationRequests mobile = mock(MobileNotificationRequests.class);
        DomainEvents events = mock(DomainEvents.class);
        var service = new DeliveryProposalService(
                shipments,
                proposals,
                new DeliveryConfirmationTokens(),
                new DeliveryConfirmationProperties(Duration.ofHours(2), "https://app.example.test/confirm"),
                email,
                mobile,
                events,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var created = service.propose(
                businessId,
                shipmentId,
                new DeliveryProposalService.ProposeDelivery(
                        UUID.randomUUID(), "receiver@example.test", "+27821234567", DeliveryMobileChannel.WHATSAPP),
                actorId);
        ArgumentCaptor<NotificationRequests.EmailRequest> emailRequest =
                ArgumentCaptor.forClass(NotificationRequests.EmailRequest.class);
        verify(email).requestEmail(emailRequest.capture());
        String url = emailRequest.getValue().templateData().get("confirmationUrl");
        String token = url.substring(url.lastIndexOf('/') + 1);

        assertThat(service.preview(token).status()).isEqualTo(DeliveryProposalStatus.PROPOSED);
        assertThat(service.confirm(token).status()).isEqualTo(DeliveryProposalStatus.ACCEPTED);
        assertThat(service.confirm(token).status()).isEqualTo(DeliveryProposalStatus.ACCEPTED);
        assertThat(created.newlyCreated()).isTrue();
        verify(mobile, times(1)).requestDirect(any());
        verify(events, times(1)).publish(any(DeliveryEvent.DeliveryAccepted.class), eq("delivery-confirmation-link"));
    }

    private static final class InMemoryProposalRepository implements DeliveryProposalRepository {

        private final Map<UUID, DeliveryProposal> saved = new HashMap<>();
        private final Map<String, UUID> tokenIds = new HashMap<>();

        @Override
        public Optional<DeliveryProposal> findById(UUID proposalId) {
            return Optional.ofNullable(saved.get(proposalId));
        }

        @Override
        public Optional<DeliveryProposal> findByShipment(UUID businessId, UUID shipmentId) {
            return saved.values().stream()
                    .filter(value -> value.businessId().equals(businessId)
                            && value.shipmentId().equals(shipmentId))
                    .findFirst();
        }

        @Override
        public Optional<DeliveryProposal> findByRequest(UUID businessId, UUID clientRequestId) {
            return saved.values().stream()
                    .filter(value -> value.businessId().equals(businessId)
                            && value.clientRequestId().equals(clientRequestId))
                    .findFirst();
        }

        @Override
        public Optional<DeliveryProposal> findByTokenHash(String tokenHash) {
            return Optional.ofNullable(tokenIds.get(tokenHash)).map(saved::get);
        }

        @Override
        public boolean save(DeliveryProposal proposal, String tokenHash) {
            saved.put(proposal.id(), proposal);
            tokenIds.put(tokenHash, proposal.id());
            return true;
        }

        @Override
        public boolean accept(UUID proposalId, String tokenHash, Instant now) {
            DeliveryProposal current = saved.get(proposalId);
            if (current == null || current.status() != DeliveryProposalStatus.PROPOSED) {
                return false;
            }
            saved.put(
                    proposalId,
                    new DeliveryProposal(
                            current.id(),
                            current.businessId(),
                            current.shipmentId(),
                            current.clientRequestId(),
                            current.inputFingerprint(),
                            current.recipientEmail(),
                            current.recipientPhone(),
                            current.mobileChannel(),
                            DeliveryProposalStatus.ACCEPTED,
                            current.expiresAt(),
                            current.createdAt(),
                            now));
            return true;
        }

        @Override
        public void expire(UUID proposalId, Instant now) {
            throw new UnsupportedOperationException();
        }
    }
}
