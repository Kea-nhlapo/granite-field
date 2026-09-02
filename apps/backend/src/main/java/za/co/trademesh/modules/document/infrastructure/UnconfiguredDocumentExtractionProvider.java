package za.co.trademesh.modules.document.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.document.application.DocumentExtractionProvider;

@Component
@ConditionalOnProperty(
        prefix = "trademesh.documents.extraction",
        name = "provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredDocumentExtractionProvider implements DocumentExtractionProvider {

    @Override
    public String name() {
        return "unconfigured";
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        throw new IllegalStateException("No document extraction provider is configured");
    }
}
