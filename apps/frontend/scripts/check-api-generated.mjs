import { execFileSync } from "node:child_process";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const generatedDirectory = join(
    projectRoot,
    "src",
    "shared",
    "api",
    "generated",
);
const generator = join(
    projectRoot,
    "node_modules",
    "@hey-api",
    "openapi-ts",
    "bin",
    "run.js",
);

const before = snapshot(generatedDirectory);
execFileSync(process.execPath, [generator, "--silent"], {
    cwd: projectRoot,
    stdio: "inherit",
});
const after = snapshot(generatedDirectory);

const changed = new Set([...before.keys(), ...after.keys()]);
const stale = [...changed].filter(
    (file) => !before.get(file)?.equals(after.get(file)),
);

if (stale.length > 0) {
    console.error(
        "Generated API client is stale. Run `npm run generate:api` and commit:",
    );
    stale.sort().forEach((file) => console.error(`- ${file}`));
    process.exit(1);
}

function snapshot(directory) {
    const files = new Map();
    if (!existsSync(directory)) {
        return files;
    }
    visit(directory);
    return files;

    function visit(current) {
        for (const entry of readdirSync(current, { withFileTypes: true })) {
            const absolute = join(current, entry.name);
            if (entry.isDirectory()) {
                visit(absolute);
            } else {
                files.set(
                    relative(directory, absolute).replaceAll("\\", "/"),
                    readFileSync(absolute),
                );
            }
        }
    }
}
