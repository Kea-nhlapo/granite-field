package za.co.trademesh.modules.business.application;

import za.co.trademesh.modules.business.events.BusinessEvent;

public interface BusinessEventPublisher {
    void publish(BusinessEvent event);
}
