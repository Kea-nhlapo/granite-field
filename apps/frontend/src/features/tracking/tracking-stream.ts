import type { LivePositionResponse } from "../../shared/api/generated";

export type LiveCursor = {
    lastRecordedAt?: string;
    seenReadingIds: Set<string>;
};

export function acceptLiveUpdate(
    cursor: LiveCursor,
    next: LivePositionResponse,
): LivePositionResponse | undefined {
    const readingId = next.readingId;
    if (readingId && cursor.seenReadingIds.has(readingId)) {
        return undefined;
    }
    if (
        next.recordedAt &&
        cursor.lastRecordedAt &&
        next.recordedAt < cursor.lastRecordedAt
    ) {
        return undefined;
    }
    if (readingId) {
        cursor.seenReadingIds.add(readingId);
    }
    cursor.lastRecordedAt = next.recordedAt ?? cursor.lastRecordedAt;
    return next;
}

export function nextBackoffMs(attempt: number) {
    return Math.min(4000, 250 * 2 ** Math.max(0, attempt));
}

export async function pollLiveTelemetry(options: {
    load: () => Promise<{
        data?: LivePositionResponse;
        error?: { status?: number };
    }>;
    signal: AbortSignal;
    onUpdate: (position: LivePositionResponse) => void;
    onStatus: (status: "live" | "reconnecting" | "failed") => void;
    intervalMs?: number;
    sleep?: (ms: number, signal: AbortSignal) => Promise<void>;
}) {
    const cursor: LiveCursor = { seenReadingIds: new Set() };
    const intervalMs = options.intervalMs ?? 2000;
    const sleep =
        options.sleep ??
        ((ms, signal) =>
            new Promise<void>((resolve, reject) => {
                const timer = setTimeout(resolve, ms);
                const onAbort = () => {
                    clearTimeout(timer);
                    reject(new DOMException("Aborted", "AbortError"));
                };
                if (signal.aborted) {
                    onAbort();
                    return;
                }
                signal.addEventListener("abort", onAbort, { once: true });
            }));
    let failures = 0;
    while (!options.signal.aborted) {
        const result = await options.load();
        if (options.signal.aborted) {
            return;
        }
        if (result.error) {
            if (result.error.status === 404) {
                failures = 0;
                options.onStatus("live");
                await sleep(intervalMs, options.signal).catch(() => undefined);
                continue;
            }
            failures += 1;
            if (failures >= 6) {
                options.onStatus("failed");
                return;
            }
            options.onStatus("reconnecting");
            await sleep(nextBackoffMs(failures), options.signal).catch(
                () => undefined,
            );
            continue;
        }
        failures = 0;
        options.onStatus("live");
        if (result.data) {
            const accepted = acceptLiveUpdate(cursor, result.data);
            if (accepted) {
                options.onUpdate(accepted);
            }
        }
        await sleep(intervalMs, options.signal).catch(() => undefined);
    }
}
