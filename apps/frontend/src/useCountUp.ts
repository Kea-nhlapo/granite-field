import { useEffect, useRef, useState } from "react";

/** Eases a displayed number toward `target` whenever it changes — used for "the price changes live" moments. */
export function useCountUp(target: number, durationMs = 700) {
    const [value, setValue] = useState(target);
    const frame = useRef<number | undefined>(undefined);
    const from = useRef(target);

    useEffect(() => {
        const start = performance.now();
        const startValue = from.current;
        const delta = target - startValue;

        if (delta === 0) return;

        function tick(now: number) {
            const elapsed = now - start;
            const t = Math.min(1, elapsed / durationMs);
            const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
            setValue(startValue + delta * eased);
            if (t < 1) {
                frame.current = requestAnimationFrame(tick);
            } else {
                from.current = target;
            }
        }

        frame.current = requestAnimationFrame(tick);
        return () => {
            if (frame.current) cancelAnimationFrame(frame.current);
        };
    }, [target, durationMs]);

    return value;
}
