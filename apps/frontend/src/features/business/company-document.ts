export const allowedCompanyDocumentTypes = [
    "application/pdf",
    "image/jpeg",
    "image/png",
] as const;

export const maxCompanyDocumentBytes = 10 * 1024 * 1024;

const allowedExtensions = new Set(["pdf", "jpg", "jpeg", "png"]);

export function validateCompanyDocument(file: File): string | undefined {
    if (file.size === 0) {
        return "Choose a company document to upload.";
    }
    if (file.size > maxCompanyDocumentBytes) {
        return "The document is larger than 10 MB.";
    }

    const extension = file.name.split(".").pop()?.toLowerCase();
    if (
        !extension ||
        !allowedExtensions.has(extension) ||
        file.name.includes("/") ||
        file.name.includes("\\")
    ) {
        return "Upload a PDF, JPG, or PNG company document.";
    }

    if (
        !allowedCompanyDocumentTypes.includes(
            file.type as (typeof allowedCompanyDocumentTypes)[number],
        ) &&
        file.type !== ""
    ) {
        return "Upload a PDF, JPG, or PNG company document.";
    }

    return undefined;
}
