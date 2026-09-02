package za.co.trademesh.modules.document.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.document.application.DocumentExtractionProvider;
import za.co.trademesh.modules.document.domain.ExtractedDocumentField;

@Component
@ConditionalOnProperty(prefix = "trademesh.documents.extraction", name = "provider", havingValue = "mock")
class MockDocumentExtractionProvider implements DocumentExtractionProvider {

    @Override
    public String name() {
        return "mock-document-extractor";
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        return new ExtractionResult(
                "mock-1.0",
                "mock-result:" + request.documentId(),
                List.of(
                        new ExtractedDocumentField(
                                "supplier.name", "Demo Supplier", new BigDecimal("0.9800"), 1, "10,10,240,24"),
                        new ExtractedDocumentField(
                                "document.type", request.documentType().name(), BigDecimal.ONE, 1, null),
                        new ExtractedDocumentField(
                                "total.value", "8500.00", new BigDecimal("0.9200"), 1, "300,600,180,30")));
    }
}
