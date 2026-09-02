import { client } from "./generated/client.gen";
import { runtimeConfig } from "../lib/runtime-config";

let accessToken: string | undefined;

client.setConfig({
    auth: () => accessToken,
    baseUrl: runtimeConfig.apiBaseUrl,
});

export function setApiAccessToken(token: string | null | undefined) {
    accessToken = token?.trim() || undefined;
}

export function getApiAccessToken() {
    return accessToken;
}

export { client as apiClient };
