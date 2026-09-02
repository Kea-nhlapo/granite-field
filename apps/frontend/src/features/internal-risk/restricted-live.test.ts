import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_RESTRICTED !== "1")(
    "local backend restricted views",
    () => {
        it("exposes generated internal risk and insurance operations", async () => {
            const { insuranceEvidence, riskList } =
                await import("../../shared/api/internal-api");
            expect(riskList).toBeTypeOf("function");
            expect(insuranceEvidence).toBeTypeOf("function");
        });
    },
);
