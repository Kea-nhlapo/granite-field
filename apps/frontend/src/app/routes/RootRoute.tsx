import { runtimeConfig } from "../../shared/lib/runtime-config";

export default function RootRoute() {
    return (
        <main
            className="app-state"
            data-api-configured={Boolean(runtimeConfig.apiBaseUrl)}
        >
            <p className="eyebrow">Application status</p>
            <h1>Frontend foundation is ready</h1>
            <p>The web application has loaded successfully.</p>
        </main>
    );
}
