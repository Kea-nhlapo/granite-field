import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { SessionProvider } from "./features/access/SessionProvider";
import { MotionProvider } from "./motion";
import { startApiMocks } from "./shared/api/start-api-mocks";
import "./index.css";

const rootElement = document.getElementById("root");

if (!rootElement) {
    throw new Error("Application root element was not found");
}

void startApiMocks().then(() => {
    createRoot(rootElement).render(
        <StrictMode>
            <SessionProvider>
                <MotionProvider>
                    <App />
                </MotionProvider>
            </SessionProvider>
        </StrictMode>,
    );
});
