package za.co.trademesh.modules.insurance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InsuranceRepository {

    boolean saveCase(InsuranceCase insuranceCase);

    Optional<InsuranceCase> findCase(UUID caseId);

    Optional<InsuranceCase> findCaseByRequest(UUID createdByUserId, UUID clientRequestId);

    void saveAccess(InsuranceEvidenceAccess access);

    boolean saveDecision(InsuranceDecision decision);

    Optional<InsuranceDecision> findDecisionByCommand(UUID commandId);

    List<InsuranceDecision> findDecisions(UUID caseId);
}
