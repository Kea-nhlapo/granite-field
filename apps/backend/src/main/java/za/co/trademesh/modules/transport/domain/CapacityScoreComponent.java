package za.co.trademesh.modules.transport.domain;

public record CapacityScoreComponent(
        String code, double rawValue, double normalizedValue, double weight, double contribution, String explanation) {}
