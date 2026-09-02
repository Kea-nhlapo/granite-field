package za.co.trademesh.modules.document.domain;

import java.util.UUID;

public record DocumentComparisonSource(
        UUID documentId, DocumentType documentType, UUID confirmationId, int confirmationRevision) {}
