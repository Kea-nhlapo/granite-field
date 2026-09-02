import { describe, expect, it } from "vitest";

import { validateCompanyDocument } from "./company-document";

describe("company document validation", () => {
    it("accepts a PDF company document", () => {
        const file = new File(["%PDF-1"], "company.pdf", {
            type: "application/pdf",
        });
        expect(validateCompanyDocument(file)).toBeUndefined();
    });

    it("rejects an empty or oversized file before upload", () => {
        expect(
            validateCompanyDocument(
                new File([], "empty.pdf", { type: "application/pdf" }),
            ),
        ).toBe("Choose a company document to upload.");

        const tooLarge = new File(["%PDF-1"], "big.pdf", {
            type: "application/pdf",
        });
        Object.defineProperty(tooLarge, "size", {
            value: 10 * 1024 * 1024 + 1,
        });
        expect(validateCompanyDocument(tooLarge)).toBe(
            "The document is larger than 10 MB.",
        );
    });

    it("rejects unsupported filenames", () => {
        const file = new File(["plain"], "notes.txt", { type: "text/plain" });
        expect(validateCompanyDocument(file)).toBe(
            "Upload a PDF, JPG, or PNG company document.",
        );
    });
});
