import { insuranceEvidence } from "../../shared/api/internal-api";

export function loadInsuranceEvidence(caseId: string) {
    return insuranceEvidence({
        path: { caseId },
    });
}
