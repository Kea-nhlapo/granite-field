import { describe, expect, it } from "vitest";

import {
    acceptLiveUpdate,
    nextBackoffMs,
    type LiveCursor,
} from "./tracking-stream";

describe("tracking live cursor", () => {
    it("rejects duplicates and stale recordedAt values", () => {
        const cursor: LiveCursor = { seenReadingIds: new Set() };
        const first = acceptLiveUpdate(cursor, {
            readingId: "a",
            recordedAt: "2026-09-02T12:00:10Z",
            latitude: -26.1,
            longitude: 28.23,
        });
        expect(first?.latitude).toBe(-26.1);
        expect(
            acceptLiveUpdate(cursor, {
                readingId: "a",
                recordedAt: "2026-09-02T12:00:20Z",
                latitude: -26.2,
                longitude: 28.23,
            }),
        ).toBeUndefined();
        expect(
            acceptLiveUpdate(cursor, {
                readingId: "b",
                recordedAt: "2026-09-02T12:00:05Z",
                latitude: -26.3,
                longitude: 28.23,
            }),
        ).toBeUndefined();
        expect(nextBackoffMs(1)).toBeGreaterThan(nextBackoffMs(0));
    });
});
