package za.co.trademesh.modules.telemetry.application;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(DataSource.class)
class TelemetryRetentionJob {

    private final TelemetryService telemetry;

    TelemetryRetentionJob(TelemetryService telemetry) {
        this.telemetry = telemetry;
    }

    @Scheduled(fixedDelayString = "${trademesh.telemetry.cleanup-interval:PT1H}")
    void cleanUp() {
        telemetry.cleanUp();
    }
}
