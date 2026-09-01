package za.co.trademesh.modules.business.infrastructure;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.business.application.BusinessEventPublisher;
import za.co.trademesh.modules.business.events.BusinessEvent;

@Component
class SpringBusinessEventPublisher implements BusinessEventPublisher {

    private final ApplicationEventPublisher publisher;

    SpringBusinessEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(BusinessEvent event) {
        publisher.publishEvent(event);
    }
}
