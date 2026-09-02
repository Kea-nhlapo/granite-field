package za.co.trademesh.modules.routing.application;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.routing.scoring")
public record RouteScoringProperties(
        String algorithmVersion,
        BigDecimal unknownDataPenalty,
        BigDecimal fuelPriceZarPerLitre,
        BigDecimal fuelLitresPer100Km,
        Map<String, CargoProfile> profiles) {

    public record CargoProfile(Map<String, BigDecimal> weights) {}
}
