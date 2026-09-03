import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const frontendRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const generatedMarker = join(
    frontendRoot,
    "src",
    "shared",
    "api",
    "generated",
    "client.gen.ts",
);
const original = readFileSync(generatedMarker);

writeFileSync(
    generatedMarker,
    Buffer.concat([original, Buffer.from("\n// stale-client-probe\n")]),
);

let failedAsExpected = false;
try {
    execFileSync(process.execPath, ["scripts/check-api-generated.mjs"], {
        cwd: frontendRoot,
        encoding: "utf8",
        stdio: ["ignore", "pipe", "pipe"],
    });
} catch (error) {
    const output = [
        error instanceof Error ? error.message : "",
        typeof error === "object" && error && "stdout" in error
            ? String(error.stdout)
            : "",
        typeof error === "object" && error && "stderr" in error
            ? String(error.stderr)
            : "",
    ].join("\n");
    failedAsExpected = output.includes("Generated API client is stale");
} finally {
    writeFileSync(generatedMarker, original);
}

if (!failedAsExpected) {
    console.error(
        "Expected check-api-generated.mjs to fail after a generated-file edit.",
    );
    process.exit(1);
}

console.log("Stale generated-client check failed as expected.");
