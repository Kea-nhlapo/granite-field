package za.co.trademesh.modules.document.domain;

import java.util.List;
import java.util.Optional;

public record DocumentView(
        DocumentRecord document,
        Optional<DocumentExtraction> extraction,
        Optional<DocumentConfirmation> latestConfirmation,
        List<DocumentStateTransition> stateHistory) {

    public DocumentView {
        extraction = extraction == null ? Optional.empty() : extraction;
        latestConfirmation = latestConfirmation == null ? Optional.empty() : latestConfirmation;
        stateHistory = List.copyOf(stateHistory);
    }
}
