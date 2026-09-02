package za.co.trademesh.modules.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentConfirmation(
        UUID id,
        UUID documentId,
        UUID requestId,
        int revision,
        UUID confirmedByUserId,
        List<ConfirmedDocumentField> fields,
        Instant createdAt) {

    public DocumentConfirmation {
        fields = List.copyOf(fields);
    }
}
