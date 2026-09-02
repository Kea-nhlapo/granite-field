package za.co.trademesh.modules.routing.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.CandidateRouteScore;
import za.co.trademesh.modules.routing.domain.RouteAssessment;
import za.co.trademesh.modules.routing.domain.RouteAssessmentRepository;
import za.co.trademesh.modules.routing.domain.RouteCalculation;
import za.co.trademesh.modules.routing.domain.RouteCalculationRepository;
import za.co.trademesh.modules.routing.domain.RouteFactor;
import za.co.trademesh.modules.routing.domain.RouteFactorScore;
import za.co.trademesh.modules.routing.domain.RouteOption;
import za.co.trademesh.modules.routing.events.RoutingEvent;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class RouteScoringService {

    private static final int SCALE = 6;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(SCALE);

    private final RouteCalculationRepository calculations;
    private final RouteAssessmentRepository assessments;
    private final RouteFactorDataProvider factorData;
    private final RouteScoringProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public RouteScoringService(
            RouteCalculationRepository calculations,
            RouteAssessmentRepository assessments,
            RouteFactorDataProvider factorData,
            RouteScoringProperties properties,
            DomainEvents events,
            Clock clock) {
        this.calculations = calculations;
        this.assessments = assessments;
        this.factorData = factorData;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        validateProperties(properties);
    }

    @Transactional
    public RouteAssessment score(UUID businessId, UUID calculationId, ScoreRoutes command, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID actor = requiredId(actorUserId);
        UUID calculationKey = requiredId(calculationId);
        if (command == null || command.requestId() == null || command.cargoProfile() == null) {
            throw RoutingException.invalidScoreRequest();
        }
        RouteCalculation calculation =
                calculations.findById(owner, calculationKey).orElseThrow(RoutingException::calculationNotFound);
        String cargoProfile = profileName(command.cargoProfile());
        EnumMap<RouteFactor, BigDecimal> weights = effectiveWeights(cargoProfile, command.weightOverrides());
        String fingerprint = fingerprint(calculationKey, cargoProfile, weights);
        var existing = assessments.findByRequestId(owner, command.requestId());
        if (existing.isPresent()) {
            if (!existing.get().inputFingerprint().equals(fingerprint)) {
                throw RoutingException.scoreRequestConflict();
            }
            return existing.get();
        }

        Map<UUID, EnumMap<RouteFactor, BigDecimal>> measurements = measurements(calculation);
        EnumMap<RouteFactor, Range> ranges = ranges(measurements);
        List<ScoreDraft> drafts = calculation.candidates().stream()
                .map(candidate -> scoreCandidate(candidate, measurements.get(candidate.id()), ranges, weights))
                .toList();
        EnumMap<RouteOption, UUID> optionCandidates = optionCandidates(calculation, measurements, drafts);
        UUID recommendedCandidateId = optionCandidates.get(RouteOption.RECOMMENDED);
        UUID fastestCandidateId = optionCandidates.get(RouteOption.FASTEST);
        List<CandidateRouteScore> scores = drafts.stream()
                .map(draft -> finishScore(
                        draft, cargoProfile, optionCandidates, fastestCandidateId, calculation, measurements))
                .toList();
        RouteAssessment assessment = new RouteAssessment(
                UUID.randomUUID(),
                owner,
                calculation.id(),
                command.requestId(),
                fingerprint,
                cargoProfile,
                properties.algorithmVersion().strip(),
                weights,
                recommendedCandidateId,
                scores,
                actor,
                databaseTime(clock.instant()));
        if (!assessments.save(assessment)) {
            return assessments
                    .findByRequestId(owner, command.requestId())
                    .filter(saved -> saved.inputFingerprint().equals(fingerprint))
                    .orElseThrow(RoutingException::scoreRequestConflict);
        }
        CandidateRouteScore recommended = scores.stream()
                .filter(score -> score.candidateId().equals(recommendedCandidateId))
                .findFirst()
                .orElseThrow();
        events.publish(
                new RoutingEvent.RouteChoicesScored(
                        assessment.id(),
                        calculation.id(),
                        owner,
                        cargoProfile,
                        properties.algorithmVersion().strip(),
                        recommendedCandidateId,
                        recommended.totalScore(),
                        recommended.confidence()),
                actor.toString());
        return assessment;
    }

    @Transactional(readOnly = true)
    public RouteAssessment get(UUID businessId, UUID assessmentId) {
        return assessments
                .findById(requiredId(businessId), requiredId(assessmentId))
                .orElseThrow(RoutingException::assessmentNotFound);
    }

    private Map<UUID, EnumMap<RouteFactor, BigDecimal>> measurements(RouteCalculation calculation) {
        Map<UUID, EnumMap<RouteFactor, BigDecimal>> values = new java.util.LinkedHashMap<>();
        for (CandidateRoute candidate : calculation.candidates()) {
            Map<RouteFactor, RouteFactorDataProvider.Measurement> provided = factorData.measure(calculation, candidate);
            if (provided == null || provided.entrySet().stream().anyMatch(entry -> entry.getKey() == null)) {
                throw RoutingException.invalidFactorData();
            }
            EnumMap<RouteFactor, BigDecimal> candidateValues = new EnumMap<>(RouteFactor.class);
            for (var entry : provided.entrySet()) {
                BigDecimal value =
                        entry.getValue() == null ? null : entry.getValue().value();
                if (!validMeasurement(entry.getKey(), value)) {
                    throw RoutingException.invalidFactorData();
                }
                candidateValues.put(entry.getKey(), value.setScale(SCALE, RoundingMode.HALF_UP));
            }
            requireProviderMeasurement(candidateValues, RouteFactor.TIME);
            requireProviderMeasurement(candidateValues, RouteFactor.DISTANCE);
            requireProviderMeasurement(candidateValues, RouteFactor.FUEL);
            requireProviderMeasurement(candidateValues, RouteFactor.TOLLS);
            values.put(candidate.id(), candidateValues);
        }
        return Map.copyOf(values);
    }

    private static void requireProviderMeasurement(Map<RouteFactor, BigDecimal> measurements, RouteFactor factor) {
        if (!measurements.containsKey(factor)) {
            throw RoutingException.invalidFactorData();
        }
    }

    private static boolean validMeasurement(RouteFactor factor, BigDecimal value) {
        if (value == null || value.signum() < 0 || value.precision() - value.scale() > 14) {
            return false;
        }
        return switch (factor) {
            case SAFETY_EXPOSURE, ROAD_QUALITY, CONNECTIVITY -> value.compareTo(BigDecimal.valueOf(100)) <= 0;
            default -> true;
        };
    }

    private static EnumMap<RouteFactor, Range> ranges(Map<UUID, EnumMap<RouteFactor, BigDecimal>> values) {
        EnumMap<RouteFactor, Range> ranges = new EnumMap<>(RouteFactor.class);
        for (RouteFactor factor : RouteFactor.values()) {
            List<BigDecimal> available = values.values().stream()
                    .map(candidate -> candidate.get(factor))
                    .filter(Objects::nonNull)
                    .toList();
            if (!available.isEmpty()) {
                BigDecimal minimum =
                        available.stream().min(BigDecimal::compareTo).orElseThrow();
                BigDecimal maximum =
                        available.stream().max(BigDecimal::compareTo).orElseThrow();
                ranges.put(factor, new Range(minimum, maximum));
            }
        }
        return ranges;
    }

    private ScoreDraft scoreCandidate(
            CandidateRoute candidate,
            Map<RouteFactor, BigDecimal> measurements,
            Map<RouteFactor, Range> ranges,
            Map<RouteFactor, BigDecimal> weights) {
        List<RouteFactorScore> factorScores = new ArrayList<>();
        BigDecimal total = ZERO;
        BigDecimal availableWeight = ZERO;
        BigDecimal totalWeight = weights.values().stream().reduce(ZERO, BigDecimal::add);
        for (RouteFactor factor : RouteFactor.values()) {
            BigDecimal rawValue = measurements.get(factor);
            boolean available = rawValue != null;
            BigDecimal normalized = available
                    ? normalize(rawValue, ranges.get(factor), factor.higherIsBetter())
                    : properties.unknownDataPenalty().setScale(SCALE, RoundingMode.HALF_UP);
            BigDecimal weight = weights.get(factor);
            BigDecimal contribution = normalized.multiply(weight).setScale(SCALE, RoundingMode.HALF_UP);
            total = total.add(contribution);
            if (available) {
                availableWeight = availableWeight.add(weight);
            }
            factorScores.add(
                    new RouteFactorScore(factor, rawValue, factor.unit(), normalized, weight, contribution, available));
        }
        BigDecimal confidence = availableWeight.divide(totalWeight, SCALE, RoundingMode.HALF_UP);
        return new ScoreDraft(
                candidate, total.min(ONE).setScale(SCALE, RoundingMode.HALF_UP), confidence, factorScores);
    }

    private static BigDecimal normalize(BigDecimal value, Range range, boolean higherIsBetter) {
        if (range == null || range.maximum().compareTo(range.minimum()) == 0) {
            return ZERO;
        }
        BigDecimal distanceFromBest =
                higherIsBetter ? range.maximum().subtract(value) : value.subtract(range.minimum());
        return distanceFromBest.divide(range.maximum().subtract(range.minimum()), SCALE, RoundingMode.HALF_UP);
    }

    private EnumMap<RouteOption, UUID> optionCandidates(
            RouteCalculation calculation,
            Map<UUID, EnumMap<RouteFactor, BigDecimal>> measurements,
            List<ScoreDraft> drafts) {
        EnumMap<RouteOption, UUID> options = new EnumMap<>(RouteOption.class);
        Comparator<CandidateRoute> sequenceTie = Comparator.comparingInt(CandidateRoute::sequence);
        options.put(
                RouteOption.FASTEST,
                calculation.candidates().stream()
                        .min(Comparator.comparingLong(CandidateRoute::durationSeconds)
                                .thenComparing(sequenceTie))
                        .orElseThrow()
                        .id());
        options.put(
                RouteOption.LOWEST_COST,
                calculation.candidates().stream()
                        .min(Comparator.comparing((CandidateRoute candidate) -> operatingCost(candidate, measurements))
                                .thenComparing(sequenceTie))
                        .orElseThrow()
                        .id());
        bestAvailable(calculation, measurements, RouteFactor.SAFETY_EXPOSURE, false)
                .ifPresent(candidate -> options.put(RouteOption.SAFEST, candidate.id()));
        bestAvailable(calculation, measurements, RouteFactor.CONNECTIVITY, true)
                .ifPresent(candidate -> options.put(RouteOption.BEST_CONNECTIVITY, candidate.id()));
        options.put(
                RouteOption.RECOMMENDED,
                drafts.stream()
                        .min(Comparator.comparing(ScoreDraft::totalScore)
                                .thenComparing(ScoreDraft::confidence, Comparator.reverseOrder())
                                .thenComparingInt(draft -> draft.candidate().sequence()))
                        .orElseThrow()
                        .candidate()
                        .id());
        return options;
    }

    private BigDecimal operatingCost(
            CandidateRoute candidate, Map<UUID, EnumMap<RouteFactor, BigDecimal>> measurements) {
        return measurements
                .get(candidate.id())
                .get(RouteFactor.FUEL)
                .multiply(properties.fuelPriceZarPerLitre())
                .add(candidate.tollEstimateZar());
    }

    private static java.util.Optional<CandidateRoute> bestAvailable(
            RouteCalculation calculation,
            Map<UUID, EnumMap<RouteFactor, BigDecimal>> measurements,
            RouteFactor factor,
            boolean highestWins) {
        Comparator<CandidateRoute> comparator = Comparator.comparing(
                candidate -> measurements.get(candidate.id()).get(factor));
        if (highestWins) {
            comparator = comparator.reversed();
        }
        comparator = comparator.thenComparingInt(CandidateRoute::sequence);
        return calculation.candidates().stream()
                .filter(candidate -> measurements.get(candidate.id()).containsKey(factor))
                .min(comparator);
    }

    private static CandidateRouteScore finishScore(
            ScoreDraft draft,
            String cargoProfile,
            Map<RouteOption, UUID> optionCandidates,
            UUID fastestCandidateId,
            RouteCalculation calculation,
            Map<UUID, EnumMap<RouteFactor, BigDecimal>> measurements) {
        EnumSet<RouteOption> options = EnumSet.noneOf(RouteOption.class);
        optionCandidates.forEach((option, candidateId) -> {
            if (draft.candidate().id().equals(candidateId)) {
                options.add(option);
            }
        });
        List<String> reasons = reasons(draft, cargoProfile, options, fastestCandidateId, calculation, measurements);
        return new CandidateRouteScore(
                draft.candidate().id(),
                draft.candidate().label(),
                draft.totalScore(),
                draft.confidence(),
                options,
                draft.factors(),
                reasons);
    }

    private static List<String> reasons(
            ScoreDraft draft,
            String cargoProfile,
            EnumSet<RouteOption> options,
            UUID fastestCandidateId,
            RouteCalculation calculation,
            Map<UUID, EnumMap<RouteFactor, BigDecimal>> measurements) {
        List<String> reasons = new ArrayList<>();
        if (options.contains(RouteOption.RECOMMENDED)) {
            reasons.add("Best weighted fit for the " + display(cargoProfile) + " profile.");
        }
        if (options.contains(RouteOption.FASTEST)) {
            reasons.add("Fastest available route.");
        }
        if (options.contains(RouteOption.LOWEST_COST)) {
            reasons.add("Lowest estimated fuel and toll cost.");
        }
        if (options.contains(RouteOption.SAFEST)) {
            reasons.add("Lowest measured safety exposure.");
        }
        if (options.contains(RouteOption.BEST_CONNECTIVITY)) {
            reasons.add("Best measured network coverage.");
        }
        if (options.contains(RouteOption.RECOMMENDED) && !draft.candidate().id().equals(fastestCandidateId)) {
            CandidateRoute fastest = calculation.candidates().stream()
                    .filter(candidate -> candidate.id().equals(fastestCandidateId))
                    .findFirst()
                    .orElseThrow();
            long extraMinutes =
                    Math.max(1, Math.round((draft.candidate().durationSeconds() - fastest.durationSeconds()) / 60.0));
            reasons.add("About " + extraMinutes + " minutes slower than the fastest route.");
        }
        List<RouteFactor> missing = draft.factors().stream()
                .filter(factor -> !factor.dataAvailable() && factor.weight().signum() > 0)
                .map(RouteFactorScore::factor)
                .toList();
        if (!missing.isEmpty()) {
            reasons.add(missingReason(missing));
        }
        if (reasons.isEmpty()) {
            reasons.add("Not the best fit for the current weights.");
        }
        return List.copyOf(reasons);
    }

    private static String missingReason(List<RouteFactor> missing) {
        String fields = missing.stream()
                .map(RouteScoringService::displayFactor)
                .collect(java.util.stream.Collectors.joining(", "));
        return fields + (missing.size() == 1 ? " data is" : " data are") + " unavailable, so confidence is lower.";
    }

    private EnumMap<RouteFactor, BigDecimal> effectiveWeights(
            String cargoProfile, Map<RouteFactor, BigDecimal> overrides) {
        RouteScoringProperties.CargoProfile configured = properties.profiles().entrySet().stream()
                .filter(entry -> profileName(entry.getKey()).equals(cargoProfile))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(RoutingException::unknownCargoProfile);
        EnumMap<RouteFactor, BigDecimal> values = configuredWeights(configured);
        if (overrides != null) {
            for (var entry : overrides.entrySet()) {
                if (entry.getKey() == null
                        || entry.getValue() == null
                        || entry.getValue().signum() < 0) {
                    throw RoutingException.invalidScoreWeights();
                }
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return normalizeWeights(values);
    }

    private static EnumMap<RouteFactor, BigDecimal> configuredWeights(RouteScoringProperties.CargoProfile configured) {
        if (configured == null || configured.weights() == null) {
            throw RoutingException.invalidScoreWeights();
        }
        EnumMap<RouteFactor, BigDecimal> weights = new EnumMap<>(RouteFactor.class);
        try {
            configured.weights().forEach((name, value) -> weights.put(RouteFactor.valueOf(profileName(name)), value));
        } catch (IllegalArgumentException invalidFactor) {
            throw RoutingException.invalidScoreWeights();
        }
        if (weights.size() != RouteFactor.values().length
                || weights.values().stream().anyMatch(value -> value == null || value.signum() < 0)) {
            throw RoutingException.invalidScoreWeights();
        }
        return weights;
    }

    private static EnumMap<RouteFactor, BigDecimal> normalizeWeights(EnumMap<RouteFactor, BigDecimal> values) {
        BigDecimal total = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw RoutingException.invalidScoreWeights();
        }
        EnumMap<RouteFactor, BigDecimal> normalized = new EnumMap<>(RouteFactor.class);
        BigDecimal assigned = ZERO;
        RouteFactor[] factors = RouteFactor.values();
        for (int index = 0; index < factors.length; index++) {
            RouteFactor factor = factors[index];
            BigDecimal weight = index == factors.length - 1
                    ? ONE.subtract(assigned)
                    : values.get(factor).divide(total, SCALE, RoundingMode.DOWN);
            if (weight.signum() < 0 || weight.compareTo(ONE) > 0) {
                throw RoutingException.invalidScoreWeights();
            }
            normalized.put(factor, weight);
            assigned = assigned.add(weight);
        }
        return normalized;
    }

    private static String profileName(String value) {
        if (value == null || value.isBlank()) {
            throw RoutingException.unknownCargoProfile();
        }
        String normalized =
                value.strip().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (normalized.length() > 64 || !normalized.matches("[A-Z0-9_]+")) {
            throw RoutingException.unknownCargoProfile();
        }
        return normalized;
    }

    private String fingerprint(UUID calculationId, String cargoProfile, Map<RouteFactor, BigDecimal> weights) {
        String value = calculationId + "|" + cargoProfile + "|"
                + properties.algorithmVersion().strip() + "|"
                + java.util.Arrays.stream(RouteFactor.values())
                        .map(factor -> factor.name() + "=" + weights.get(factor).toPlainString())
                        .toList();
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String display(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String displayFactor(RouteFactor factor) {
        return switch (factor) {
            case TIME -> "Travel time";
            case DISTANCE -> "Distance";
            case FUEL -> "Fuel";
            case TOLLS -> "Toll";
            case SAFETY_EXPOSURE -> "Safety exposure";
            case ROAD_QUALITY -> "Road quality";
            case CONNECTIVITY -> "Connectivity";
        };
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw RoutingException.invalidScoreRequest();
        }
        return value;
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static void validateProperties(RouteScoringProperties properties) {
        if (properties == null
                || properties.algorithmVersion() == null
                || properties.algorithmVersion().isBlank()
                || properties.algorithmVersion().strip().length() > 64
                || properties.unknownDataPenalty() == null
                || properties.unknownDataPenalty().signum() < 0
                || properties.unknownDataPenalty().compareTo(BigDecimal.ONE) > 0
                || properties.fuelPriceZarPerLitre() == null
                || properties.fuelPriceZarPerLitre().signum() <= 0
                || properties.fuelLitresPer100Km() == null
                || properties.fuelLitresPer100Km().signum() <= 0
                || properties.profiles() == null
                || properties.profiles().isEmpty()) {
            throw new IllegalStateException("Route scoring configuration is invalid");
        }
        try {
            properties.profiles().forEach((name, profile) -> {
                profileName(name);
                normalizeWeights(configuredWeights(profile));
            });
        } catch (RoutingException invalidConfiguration) {
            throw new IllegalStateException("Route scoring configuration is invalid", invalidConfiguration);
        }
    }

    public record ScoreRoutes(UUID requestId, String cargoProfile, Map<RouteFactor, BigDecimal> weightOverrides) {
        public ScoreRoutes {
            weightOverrides = weightOverrides == null
                    ? null
                    : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(weightOverrides));
        }
    }

    private record Range(BigDecimal minimum, BigDecimal maximum) {}

    private record ScoreDraft(
            CandidateRoute candidate, BigDecimal totalScore, BigDecimal confidence, List<RouteFactorScore> factors) {}
}
