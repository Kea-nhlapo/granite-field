package za.co.trademesh.modules.document.application;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.modules.document.domain.ExtractedDocumentField;

public interface DocumentExtractionProvider {

    String name();

    ExtractionResult extract(ExtractionRequest request) throws Exception;

    record ExtractionRequest(
            UUID documentId, DocumentType documentType, String originalFilename, String contentType, byte[] content) {

        public ExtractionRequest {
            content = Arrays.copyOf(content, content.length);
        }

        @Override
        public byte[] content() {
            return Arrays.copyOf(content, content.length);
        }
    }

    record ExtractionResult(String parserVersion, String rawResultReference, List<ExtractedDocumentField> fields) {
        public ExtractionResult {
            fields = List.copyOf(fields);
        }
    }
}
