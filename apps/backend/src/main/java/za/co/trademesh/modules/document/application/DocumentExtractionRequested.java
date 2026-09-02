package za.co.trademesh.modules.document.application;

import java.util.UUID;

public record DocumentExtractionRequested(UUID documentId) {

    public static final String TYPE = "document.extraction-requested";
    public static final int SCHEMA_VERSION = 1;
}
