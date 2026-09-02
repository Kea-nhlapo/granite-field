import { describe, expect, it } from "vitest";

import { extractQuoteFields, validateQuoteDocument } from "./quote-document";

describe("quote document validation", () => {
    it("accepts a PDF quote", () => {
        const file = new File(["%PDF-1"], "quote.pdf", {
            type: "application/pdf",
        });
        expect(validateQuoteDocument(file)).toBeUndefined();
        expect(extractQuoteFields().currency).toBe("ZAR");
    });

    it("rejects an empty file", () => {
        expect(
            validateQuoteDocument(
                new File([], "empty.pdf", { type: "application/pdf" }),
            ),
        ).toBe("Choose a quote document to upload.");
    });
});
