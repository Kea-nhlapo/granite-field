import { runtimeConfig } from "../lib/runtime-config";

export async function startApiMocks() {
    if (runtimeConfig.apiMode !== "mock") {
        return;
    }

    const { worker } = await import("./mocks/browser");
    await worker.start({ onUnhandledRequest: "bypass", quiet: true });
}
