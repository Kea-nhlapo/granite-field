import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const yaml = readFileSync(
    join(root, "packages/api-contracts/openapi/openapi.yaml"),
    "utf8",
);
const client = readFileSync(
    join(root, "apps/frontend/src/shared/api/generated.ts"),
    "utf8",
);

const yamlIds = [
    ...yaml.matchAll(/operationId:\s*([A-Za-z][A-Za-z0-9]*)/g),
].map((match) => match[1]);
const uniqueYamlIds = [...new Set(yamlIds)].sort();

const listed = [
    ...client.matchAll(/OPERATION_IDS = \[([\s\S]*?)\] as const/g),
];
const block = listed[0]?.[1] ?? "";
const clientIds = [...block.matchAll(/"([A-Za-z][A-Za-z0-9]*)"/g)]
    .map((match) => match[1])
    .sort();

const missing = uniqueYamlIds.filter((id) => !clientIds.includes(id));
const extra = clientIds.filter((id) => !uniqueYamlIds.includes(id));

if (missing.length || extra.length) {
    console.error("OpenAPI client is stale.");
    if (missing.length) {
        console.error("Missing in generated.ts:", missing.join(", "));
    }
    if (extra.length) {
        console.error("Extra in generated.ts:", extra.join(", "));
    }
    process.exit(1);
}

console.log(`Checked ${uniqueYamlIds.length} operation IDs.`);
