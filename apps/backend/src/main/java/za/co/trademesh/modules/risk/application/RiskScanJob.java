package za.co.trademesh.modules.risk.application;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(DataSource.class)
class RiskScanJob {

    private final RiskService risk;

    RiskScanJob(RiskService risk) {
        this.risk = risk;
    }

    @Scheduled(fixedDelayString = "${trademesh.risk.scan-interval:PT1M}")
    void scan() {
        risk.evaluateTimeBasedRules();
    }
}
