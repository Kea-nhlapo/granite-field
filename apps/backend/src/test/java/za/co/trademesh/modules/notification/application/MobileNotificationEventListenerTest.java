package za.co.trademesh.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.trademesh.modules.access.application.BusinessNotificationRecipients;
import za.co.trademesh.modules.handover.application.HandoverNotificationRecipients;
import za.co.trademesh.modules.handover.domain.HandoverParty;
import za.co.trademesh.modules.handover.domain.HandoverState;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.modules.handover.events.HandoverEvent;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.modules.transport.events.TransportEvent;
import za.co.trademesh.shared.events.EventEnvelope;
import za.co.trademesh.shared.events.PublishedEvent;

class MobileNotificationEventListenerTest {

    @Test
    void sendsCapacityMatchesOnlyWhenTheActorHasResults() {
        Fixture fixture = new Fixture();
        UUID actor = UUID.randomUUID();
        fixture.listener.onCapacityMatch(published(
                new TransportEvent.CapacityMatchCompleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0),
                actor));
        verify(fixture.notifications, never()).requestUser(any());

        fixture.listener.onCapacityMatch(published(
                new TransportEvent.CapacityMatchCompleted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3),
                actor));

        var request = capture(fixture.notifications);
        assertThat(request.recipientUserId()).isEqualTo(actor);
        assertThat(request.templateKey()).isEqualTo(NotificationTemplates.CAPACITY_MATCH_FOUND);
    }

    @Test
    void sendsBackhaulMatchesToEveryActiveBusinessRecipient() {
        Fixture fixture = new Fixture();
        UUID businessId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        when(fixture.businesses.findActiveUserIds(businessId)).thenReturn(List.of(owner, member));

        fixture.listener.onBackhaulMatches(published(
                new TelemetryEvent.BackhaulMatchesFound(
                        UUID.randomUUID(), businessId, UUID.randomUUID(), 2, 1_500, new BigDecimal("0.91")),
                null));

        ArgumentCaptor<MobileNotificationRequests.UserMobileRequest> requests =
                ArgumentCaptor.forClass(MobileNotificationRequests.UserMobileRequest.class);
        verify(fixture.notifications, org.mockito.Mockito.times(2)).requestUser(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(MobileNotificationRequests.UserMobileRequest::recipientUserId)
                .containsExactly(owner, member);
        assertThat(requests.getAllValues())
                .extracting(MobileNotificationRequests.UserMobileRequest::templateKey)
                .containsOnly(NotificationTemplates.CAPACITY_MATCH_FOUND);
    }

    @Test
    void sendsHandoverAcceptanceToTheOtherPartyAndFinalizationToBoth() {
        Fixture fixture = new Fixture();
        UUID challengeId = UUID.randomUUID();
        UUID initiator = UUID.randomUUID();
        UUID counterparty = UUID.randomUUID();
        when(fixture.handovers.find(challengeId))
                .thenReturn(Optional.of(new HandoverNotificationRecipients.Participants(initiator, counterparty)));

        fixture.listener.onConfirmationAccepted(published(
                new HandoverEvent.ConfirmationAccepted(challengeId, UUID.randomUUID(), HandoverParty.INITIATOR),
                initiator));
        var accepted = capture(fixture.notifications);
        assertThat(accepted.recipientUserId()).isEqualTo(counterparty);
        assertThat(accepted.templateKey()).isEqualTo(NotificationTemplates.HANDOVER_CONFIRMATION_ACCEPTED);

        org.mockito.Mockito.clearInvocations(fixture.notifications);
        fixture.listener.onHandoverFinalized(published(
                new HandoverEvent.HandoverFinalized(
                        challengeId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        HandoverType.COLLECTION.name(),
                        HandoverState.DISPUTED.name()),
                initiator));
        ArgumentCaptor<MobileNotificationRequests.UserMobileRequest> requests =
                ArgumentCaptor.forClass(MobileNotificationRequests.UserMobileRequest.class);
        verify(fixture.notifications, org.mockito.Mockito.times(2)).requestUser(requests.capture());
        assertThat(requests.getAllValues())
                .extracting(MobileNotificationRequests.UserMobileRequest::recipientUserId)
                .containsExactly(initiator, counterparty);
        assertThat(requests.getAllValues())
                .extracting(MobileNotificationRequests.UserMobileRequest::templateKey)
                .containsOnly(NotificationTemplates.HANDOVER_FINALIZED_DISPUTED);
    }

    @Test
    void sendsEscrowReleaseToEveryActiveBusinessRecipientWithoutPaymentData() {
        Fixture fixture = new Fixture();
        UUID businessId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        when(fixture.businesses.findActiveUserIds(businessId)).thenReturn(List.of(owner, member));

        fixture.listener.onEscrowReleased(published(
                new PaymentEvent.Released(
                        UUID.randomUUID(), UUID.randomUUID(), businessId, new BigDecimal("2500.00"), "ZAR"),
                null));

        ArgumentCaptor<MobileNotificationRequests.UserMobileRequest> requests =
                ArgumentCaptor.forClass(MobileNotificationRequests.UserMobileRequest.class);
        verify(fixture.notifications, org.mockito.Mockito.times(2)).requestUser(requests.capture());
        assertThat(requests.getAllValues())
                .allSatisfy(request -> {
                    assertThat(request.templateKey()).isEqualTo(NotificationTemplates.ESCROW_RELEASED);
                    assertThat(request.templateData()).isEmpty();
                })
                .extracting(MobileNotificationRequests.UserMobileRequest::recipientUserId)
                .containsExactly(owner, member);
    }

    private static MobileNotificationRequests.UserMobileRequest capture(MobileNotificationRequests requests) {
        ArgumentCaptor<MobileNotificationRequests.UserMobileRequest> request =
                ArgumentCaptor.forClass(MobileNotificationRequests.UserMobileRequest.class);
        verify(requests).requestUser(request.capture());
        return request.getValue();
    }

    private static <E extends za.co.trademesh.shared.events.DomainEvent> PublishedEvent<E> published(
            E event, UUID actor) {
        return new PublishedEvent<>(
                new EventEnvelope(
                        UUID.randomUUID(),
                        event.type(),
                        Instant.parse("2026-09-02T12:00:00Z"),
                        Optional.ofNullable(actor).map(UUID::toString),
                        "test",
                        UUID.randomUUID(),
                        event.schemaVersion()),
                event);
    }

    private static final class Fixture {
        private final MobileNotificationRequests notifications = mock(MobileNotificationRequests.class);
        private final HandoverNotificationRecipients handovers = mock(HandoverNotificationRecipients.class);
        private final BusinessNotificationRecipients businesses = mock(BusinessNotificationRecipients.class);
        private final MobileNotificationEventListener listener =
                new MobileNotificationEventListener(notifications, handovers, businesses);
    }
}
