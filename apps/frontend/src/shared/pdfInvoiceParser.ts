import pdfjsWorker from "pdfjs-dist/build/pdf.worker.mjs?url";

/** Loaded on first use rather than statically, so its ~2MB doesn't ship in the main bundle. */
async function loadPdfJs() {
    const pdfjsLib = await import("pdfjs-dist");
    pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorker;
    return pdfjsLib;
}

export interface ParsedInvoiceLine {
    description: string;
    qty: number;
    unitPrice: number;
    lineTotal: number;
}

export interface ParsedInvoice {
    /** Reconstructed text lines, in reading order, across every page. */
    rawLines: string[];
    lines: ParsedInvoiceLine[];
    invoiceNumber?: string;
    total?: number;
}

/**
 * Reconstructs reading-order lines from a PDF's positioned text items by
 * grouping items whose baseline y-coordinate is within one text-height of
 * each other, then ordering left-to-right within each group.
 */
async function extractTextLines(file: File): Promise<string[]> {
    const pdfjsLib = await loadPdfJs();
    const buffer = await file.arrayBuffer();
    const pdf = await pdfjsLib.getDocument({ data: buffer }).promise;
    const lines: string[] = [];

    for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
        const page = await pdf.getPage(pageNum);
        const content = await page.getTextContent();

        const items = content.items
            .filter(
                (
                    item,
                ): item is typeof item & { str: string; transform: number[] } =>
                    "str" in item,
            )
            .map((item) => ({
                text: item.str,
                x: item.transform[4],
                y: item.transform[5],
            }))
            .filter((item) => item.text.trim().length > 0);

        items.sort((a, b) => b.y - a.y || a.x - b.x);

        let currentY: number | null = null;
        let currentLine: typeof items = [];
        const flush = () => {
            if (currentLine.length > 0) {
                lines.push(
                    currentLine
                        .map((i) => i.text)
                        .join(" ")
                        .replace(/\s+/g, " ")
                        .trim(),
                );
            }
        };

        for (const item of items) {
            if (currentY === null || Math.abs(item.y - currentY) > 3) {
                flush();
                currentLine = [item];
                currentY = item.y;
            } else {
                currentLine.push(item);
            }
        }
        flush();
    }

    return lines.filter((l) => l.length > 0);
}

const MONEY = "R?\\s?([\\d,]+\\.\\d{2})";
/** description, then a quantity, then a unit price, with an optional trailing line total. */
const LINE_ITEM_PATTERN = new RegExp(
    `^(.{3,60}?)\\s+(\\d{1,5})\\s+${MONEY}(?:\\s+${MONEY})?$`,
);
const INVOICE_NUMBER_PATTERN = /\b(?:invoice|inv)[\s#:.-]*([A-Z0-9-]{4,20})/i;
const TOTAL_PATTERN = new RegExp(
    `\\btotal(?:\\s+due|\\s+amount)?\\s*[:\\s]*${MONEY}`,
    "i",
);

function toNumber(match: string): number {
    return parseFloat(match.replace(/,/g, ""));
}

function parseLines(rawLines: string[]): ParsedInvoiceLine[] {
    const lines: ParsedInvoiceLine[] = [];
    for (const raw of rawLines) {
        const match = raw.match(LINE_ITEM_PATTERN);
        if (!match) continue;

        const [, description, qtyStr, priceStr] = match;
        if (!description || !qtyStr || !priceStr) continue;

        const qty = parseInt(qtyStr, 10);
        const unitPrice = toNumber(priceStr);
        if (qty <= 0 || unitPrice <= 0) continue;

        lines.push({
            description: description.trim(),
            qty,
            unitPrice,
            lineTotal: Math.round(qty * unitPrice * 100) / 100,
        });
    }
    return lines;
}

/**
 * Reads a PDF client-side and formats its content into invoice line items.
 * Best-effort: invoice layouts vary widely, so callers should treat an empty
 * `lines` array as "could not confidently parse" and fall back to `rawLines`.
 */
export async function parseInvoicePdf(file: File): Promise<ParsedInvoice> {
    const rawLines = await extractTextLines(file);
    const fullText = rawLines.join("\n");

    const invoiceNumberMatch = fullText.match(INVOICE_NUMBER_PATTERN);
    const totalMatches = [
        ...fullText.matchAll(new RegExp(TOTAL_PATTERN, "gi")),
    ];
    const lastTotal = totalMatches[totalMatches.length - 1];

    return {
        rawLines,
        lines: parseLines(rawLines),
        invoiceNumber: invoiceNumberMatch?.[1],
        total: lastTotal?.[1] ? toNumber(lastTotal[1]) : undefined,
    };
}
