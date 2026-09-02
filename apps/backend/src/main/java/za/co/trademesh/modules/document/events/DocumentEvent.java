package za.co.trademesh.modules.document.events;

import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface DocumentEvent extends DomainEvent permits DocumentEvent.Confirmed {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record Confirmed(UUID documentId, UUID businessId, UUID confirmationId, int revision) implements DocumentEvent {
        @Override
        public String type() {
            return "document.confirmed";
        }
    }
}
