import { describe, expect, it } from "vitest";

import { emptyLine, validateRequestDraft } from "./request-draft";

describe("request draft validation", () => {
    it("rejects empty destinations, windows, and quantities", () => {
        expect(
            validateRequestDraft({
                destinationLabel: "",
                deliveryWindowStart: "2026-10-01T08:00",
                deliveryWindowEnd: "2026-10-02T08:00",
                items: [emptyLine()],
            }),
        ).toBe("Enter a destination.");
        expect(
            validateRequestDraft({
                destinationLabel: "Tembisa, Gauteng",
                deliveryWindowStart: "2026-10-02T08:00",
                deliveryWindowEnd: "2026-10-01T08:00",
                items: [
                    {
                        ...emptyLine(),
                        description: "drinks",
                        quantity: "20",
                    },
                ],
            }),
        ).toBe("The delivery window end must be after the start.");
        expect(
            validateRequestDraft({
                destinationLabel: "Tembisa, Gauteng",
                deliveryWindowStart: "2026-10-01T08:00",
                deliveryWindowEnd: "2026-10-02T08:00",
                items: [
                    {
                        ...emptyLine(),
                        description: "drinks",
                        quantity: "0",
                    },
                ],
            }),
        ).toBe("Each line item needs a quantity greater than zero.");
    });
});
