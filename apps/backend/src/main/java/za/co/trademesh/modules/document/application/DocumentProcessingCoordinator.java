package za.co.trademesh.modules.document.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import za.co.trademesh.modules.document.domain.DocumentExtraction;
import za.co.trademesh.modules.document.domain.ExtractedDocumentField;
import za.co.trademesh.shared.storage.FileStorageService;

@Service
public class DocumentProcessingCoordinator {

    private static final int MAX_FIELDS = 500;

    private final DocumentProcessingTransactions transactions;
    private final DocumentExtractionProvider provider;
    private final FileStorageService storage;
    private final Clock clock;

    public DocumentProcessingCoordinator(
            DocumentProcessingTransactions transactions,
            DocumentExtractionProvider provider,
            FileStorageService storage,
            Clock clock) {
        this.transactions = transactions;
        this.provider = provider;
        this.storage = storage;
        this.clock = clock;
    }

    public void process(UUID documentId) throws Exception {
        DocumentProcessingTransactions.ProcessingClaim claim = transactions.claim(documentId);
        if (claim.completed()) {
            return;
        }

        try {
            var document = claim.document();
            var source = storage.getMetadata(document.businessId(), document.storedFileId());
            byte[] content = storage.readAvailableContent(document.businessId(), document.storedFileId());
            var result = provider.extract(new DocumentExtractionProvider.ExtractionRequest(
                    document.id(), document.type(), source.originalFilename(), source.contentType(), content));
            validate(provider.name(), result);

            DocumentExtraction extraction = new DocumentExtraction(
                    deterministicExtractionId(documentId),
                    documentId,
                    provider.name().strip(),
                    result.parserVersion().strip(),
                    result.rawResultReference().strip(),
                    result.fields(),
                    clock.instant());
            transactions.complete(documentId, claim.token(), extraction);
        } catch (Exception failure) {
            transactions.fail(documentId, claim.token(), failure);
            throw failure;
        }
    }

    private static void validate(String providerName, DocumentExtractionProvider.ExtractionResult result) {
        if (blankOrLong(providerName, 128)
                || result == null
                || blankOrLong(result.parserVersion(), 128)
                || blankOrLong(result.rawResultReference(), 512)
                || result.fields() == null
                || result.fields().isEmpty()
                || result.fields().size() > MAX_FIELDS) {
            throw DocumentException.invalidProviderResult();
        }
        HashSet<String> paths = new HashSet<>();
        for (ExtractedDocumentField field : result.fields()) {
            if (field == null
                    || blankOrLong(field.path(), 255)
                    || field.value() == null
                    || field.value().length() > 4000
                    || field.confidence() == null
                    || field.confidence().compareTo(BigDecimal.ZERO) < 0
                    || field.confidence().compareTo(BigDecimal.ONE) > 0
                    || (field.sourcePage() != null && field.sourcePage() < 1)
                    || (field.sourceRegion() != null && field.sourceRegion().length() > 512)
                    || !paths.add(field.path().strip())) {
                throw DocumentException.invalidProviderResult();
            }
        }
    }

    private static boolean blankOrLong(String value, int maximum) {
        return value == null || value.isBlank() || value.length() > maximum;
    }

    private static UUID deterministicExtractionId(UUID documentId) {
        return UUID.nameUUIDFromBytes(("document-extraction:" + documentId).getBytes(StandardCharsets.UTF_8));
    }
}
