/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_API_BASE_URL?: string;
    readonly VITE_API_MODE?: "live" | "mock";
    readonly VITE_LIVE_ONBOARDING?: string;
    readonly VITE_LIVE_GUEST?: string;
    readonly VITE_LIVE_GUEST_TOKEN?: string;
    readonly VITE_LIVE_DOCUMENTS?: string;
}

interface ImportMeta {
    readonly env: ImportMetaEnv;
}
