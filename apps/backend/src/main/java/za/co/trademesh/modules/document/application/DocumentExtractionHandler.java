package za.co.trademesh.modules.document.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.shared.events.outbox.OutboxHandler;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

@Component
public class DocumentExtractionHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final DocumentProcessingCoordinator coordinator;

    public DocumentExtractionHandler(ObjectMapper objectMapper, DocumentProcessingCoordinator coordinator) {
        this.objectMapper = objectMapper;
        this.coordinator = coordinator;
    }

    @Override
    public String type() {
        return DocumentExtractionRequested.TYPE;
    }

    @Override
    public void handle(OutboxMessage message) throws Exception {
        var request = objectMapper.readValue(message.payload(), DocumentExtractionRequested.class);
        coordinator.process(request.documentId());
    }
}
