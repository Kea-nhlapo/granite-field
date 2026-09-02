package za.co.trademesh.modules.transport.application;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(DataSource.class)
class CapacityReservationExpiryJob {

    private final CapacityMatchingService matching;

    CapacityReservationExpiryJob(CapacityMatchingService matching) {
        this.matching = matching;
    }

    @Scheduled(fixedDelayString = "${trademesh.capacity-matching.expiry-interval:PT1M}")
    void releaseExpiredReservations() {
        matching.expireDueReservations();
    }
}
