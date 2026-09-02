package za.co.trademesh.modules.document.domain;

import java.math.BigDecimal;

public record ExtractedDocumentField(
        String path, String value, BigDecimal confidence, Integer sourcePage, String sourceRegion) {}
