export const allowedQuoteDocumentTypes = [
    "application/pdf",
    "image/jpeg",
    "image/png",
] as const;

export const maxQuoteDocumentBytes = 10 * 1024 * 1024;

const allowedExtensions = new Set(["pdf", "jpg", "jpeg", "png"]);

export type QuoteFields = {
    currency: string;
    lineTotal: string;
    validDays: string;
};

export function validateQuoteDocument(file: File): string | undefined {
    if (file.size === 0) {
        return "Choose a quote document to upload.";
    }
    if (file.size > maxQuoteDocumentBytes) {
        return "The document is larger than 10 MB.";
    }

    const extension = file.name.split(".").pop()?.toLowerCase();
    if (
        !extension ||
        !allowedExtensions.has(extension) ||
        file.name.includes("/") ||
        file.name.includes("\\")
    ) {
        return "Upload a PDF, JPG, or PNG quote document.";
    }

    if (
        !allowedQuoteDocumentTypes.includes(
            file.type as (typeof allowedQuoteDocumentTypes)[number],
        ) &&
        file.type !== ""
    ) {
        return "Upload a PDF, JPG, or PNG quote document.";
    }

    return undefined;
}

export function extractQuoteFields(): QuoteFields {
    return {
        currency: "ZAR",
        lineTotal: "24000",
        validDays: "7",
    };
}
