import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { createBrowserRouter } from "react-router";

import { App } from "./app/App";
import { appRoutes } from "./app/routes";
import "./index.css";

const rootElement = document.getElementById("root");

if (!rootElement) {
    throw new Error("Application root element was not found");
}

createRoot(rootElement).render(
    <StrictMode>
        <App router={createBrowserRouter(appRoutes)} />
    </StrictMode>,
);
