package za.co.trademesh.modules.business.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.business.events.BusinessEvent;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class BusinessEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof BusinessEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((BusinessEvent) event) {
            case BusinessEvent.OnboardingStarted started ->
                new EvidenceProjection("BUSINESS_ONBOARDING", started.onboardingId(), null, EvidenceMetadata.of());
            case BusinessEvent.ProfileConfirmed confirmed ->
                new EvidenceProjection(
                        "BUSINESS",
                        confirmed.businessId(),
                        null,
                        EvidenceMetadata.of("onboardingId", confirmed.onboardingId()));
        };
    }
}
