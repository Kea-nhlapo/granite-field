package za.co.trademesh.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;

class InfobipStatusServiceTest {

    @Test
    void advancesSuccessStatesWithoutAllowingLateCallbacksToRegressThem() {
        assertThat(InfobipStatusService.shouldAdvance(MobileNotificationStatus.QUEUED, MobileNotificationStatus.SENT))
                .isTrue();
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.SENT, MobileNotificationStatus.DELIVERED))
                .isTrue();
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.DELIVERED, MobileNotificationStatus.READ))
                .isTrue();
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.READ, MobileNotificationStatus.DELIVERED))
                .isFalse();
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.DELIVERED, MobileNotificationStatus.SENT))
                .isFalse();
    }

    @Test
    void acceptsAProviderFailureOnlyBeforeDeliveryAndKeepsFailuresTerminal() {
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.QUEUED, MobileNotificationStatus.REJECTED))
                .isTrue();
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.REJECTED, MobileNotificationStatus.DELIVERED))
                .isFalse();
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.DELIVERED, MobileNotificationStatus.FAILED))
                .isFalse();
        assertThat(InfobipStatusService.shouldAdvance(
                        MobileNotificationStatus.SUBMISSION_UNKNOWN, MobileNotificationStatus.DELIVERED))
                .isTrue();
    }
}
