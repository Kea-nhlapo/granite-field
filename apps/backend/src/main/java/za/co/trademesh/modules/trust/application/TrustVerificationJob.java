package za.co.trademesh.modules.trust.application;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(DataSource.class)
class TrustVerificationJob {

    private final TrustScoreService trustScores;

    TrustVerificationJob(TrustScoreService trustScores) {
        this.trustScores = trustScores;
    }

    @Scheduled(fixedDelayString = "${trademesh.trust.scores.verified-interval:PT30S}")
    void refreshDueScores() {
        trustScores.computeDueVerified();
    }
}
