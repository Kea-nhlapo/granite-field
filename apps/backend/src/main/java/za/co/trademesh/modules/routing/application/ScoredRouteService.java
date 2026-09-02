package za.co.trademesh.modules.routing.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.routing.domain.RouteAssessmentRepository;
import za.co.trademesh.modules.routing.domain.RouteCalculationRepository;

@Service
class ScoredRouteService implements ScoredRouteCatalog {

    private final RouteAssessmentRepository assessments;
    private final RouteCalculationRepository calculations;

    ScoredRouteService(RouteAssessmentRepository assessments, RouteCalculationRepository calculations) {
        this.assessments = assessments;
        this.calculations = calculations;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ScoredRoute> findScoredRoute(UUID requestedByBusinessId, UUID assessmentId, UUID candidateId) {
        var assessment = assessments.findById(requestedByBusinessId, assessmentId);
        if (assessment.isEmpty()) {
            return Optional.empty();
        }
        var score = assessment.get().candidates().stream()
                .filter(candidate -> candidate.candidateId().equals(candidateId))
                .findFirst();
        var calculation =
                calculations.findById(requestedByBusinessId, assessment.get().calculationId());
        if (score.isEmpty() || calculation.isEmpty()) {
            return Optional.empty();
        }
        return calculation.get().candidates().stream()
                .filter(candidate -> candidate.id().equals(candidateId))
                .findFirst()
                .map(candidate -> new ScoredRoute(
                        assessmentId,
                        calculation.get().id(),
                        candidateId,
                        assessment.get().cargoProfile(),
                        assessment.get().algorithmVersion(),
                        score.get().totalScore(),
                        score.get().confidence(),
                        candidate.geometry().stream()
                                .map(point -> new RoutePoint(point.latitude(), point.longitude()))
                                .toList(),
                        candidate.distanceMetres(),
                        candidate.durationSeconds(),
                        candidate.tollEstimateZar()));
    }
}
