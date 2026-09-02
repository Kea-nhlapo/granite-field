import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { App } from "./app/App";
import { startApiMocks } from "./shared/api/start-api-mocks";
import "./shared/styles/global.css";

const rootElement = document.getElementById("root");

if (!rootElement) {
    throw new Error("Application root element was not found");
}

void startApiMocks().then(() => {
    createRoot(rootElement).render(
        <StrictMode>
            <App />
        </StrictMode>,
    );
});
