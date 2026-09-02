const FINDER: number[][] = [
    [1, 1, 1, 1, 1, 1, 1],
    [1, 0, 0, 0, 0, 0, 1],
    [1, 0, 1, 1, 1, 0, 1],
    [1, 0, 1, 1, 1, 0, 1],
    [1, 0, 1, 1, 1, 0, 1],
    [1, 0, 0, 0, 0, 0, 1],
    [1, 1, 1, 1, 1, 1, 1],
];

export function qrModules(payload: string) {
    const size = 29;
    const modules: boolean[][] = Array.from({ length: size }, () =>
        Array.from({ length: size }, () => false),
    );
    placeFinder(modules, 0, 0);
    placeFinder(modules, size - 7, 0);
    placeFinder(modules, 0, size - 7);
    const bytes = Array.from(new TextEncoder().encode(payload || "0"));
    let bit = 0;
    for (let row = 0; row < size; row += 1) {
        const line = modules[row];
        if (!line) {
            continue;
        }
        for (let col = 0; col < size; col += 1) {
            if (isReserved(row, col, size)) {
                continue;
            }
            const value = bytes[Math.floor(bit / 8) % bytes.length] ?? 0;
            line[col] = ((value >> (bit % 8)) & 1) === 1;
            bit += 1;
        }
    }
    return modules;
}

function placeFinder(modules: boolean[][], row: number, col: number) {
    for (let y = 0; y < 7; y += 1) {
        const pattern = FINDER[y];
        const line = modules[row + y];
        if (!pattern || !line) {
            continue;
        }
        for (let x = 0; x < 7; x += 1) {
            line[col + x] = pattern[x] === 1;
        }
    }
}

function isReserved(row: number, col: number, size: number) {
    return (
        (row < 8 && col < 8) ||
        (row < 8 && col >= size - 8) ||
        (row >= size - 8 && col < 8)
    );
}
