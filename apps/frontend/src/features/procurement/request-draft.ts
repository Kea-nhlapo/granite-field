import type { ProductRequestItemRequest } from "../../shared/api/generated";

export const unitOptions: ProductRequestItemRequest["unitOfMeasure"][] = [
    "EACH",
    "CASE",
    "BOX",
    "KG",
    "LITRE",
    "PALLET",
];

export type DraftLine = {
    key: string;
    productCode: string;
    description: string;
    quantity: string;
    unitOfMeasure: ProductRequestItemRequest["unitOfMeasure"];
};

export type RequestDraft = {
    destinationLabel: string;
    deliveryWindowStart: string;
    deliveryWindowEnd: string;
    items: DraftLine[];
};

export function emptyLine(): DraftLine {
    return {
        key: crypto.randomUUID(),
        productCode: "",
        description: "",
        quantity: "",
        unitOfMeasure: "EACH",
    };
}

export function validateRequestDraft(draft: RequestDraft) {
    if (!draft.destinationLabel.trim()) {
        return "Enter a destination.";
    }
    if (!draft.deliveryWindowStart || !draft.deliveryWindowEnd) {
        return "Enter a delivery window.";
    }
    const start = Date.parse(draft.deliveryWindowStart);
    const end = Date.parse(draft.deliveryWindowEnd);
    if (!Number.isFinite(start) || !Number.isFinite(end) || start >= end) {
        return "The delivery window end must be after the start.";
    }
    if (draft.items.length === 0) {
        return "Add at least one line item.";
    }
    for (const item of draft.items) {
        if (!item.description.trim()) {
            return "Each line item needs a description.";
        }
        const quantity = Number(item.quantity);
        if (!Number.isFinite(quantity) || quantity <= 0) {
            return "Each line item needs a quantity greater than zero.";
        }
    }
    return undefined;
}

export function toCreateBody(draft: RequestDraft): {
    requestId: string;
    destinationLabel: string;
    deliveryWindowStart: string;
    deliveryWindowEnd: string;
    items: ProductRequestItemRequest[];
} {
    return {
        requestId: crypto.randomUUID(),
        destinationLabel: draft.destinationLabel.trim(),
        deliveryWindowStart: new Date(draft.deliveryWindowStart).toISOString(),
        deliveryWindowEnd: new Date(draft.deliveryWindowEnd).toISOString(),
        items: draft.items.map((item) => ({
            itemId: crypto.randomUUID(),
            productCode: item.productCode.trim() || undefined,
            description: item.description.trim(),
            quantity: Number(item.quantity),
            unitOfMeasure: item.unitOfMeasure,
        })),
    };
}
