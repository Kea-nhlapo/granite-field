import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { createMemoryRouter } from "react-router";
import { expect } from "vitest";

import { App } from "../app/App";
import { appRoutes } from "../app/routes";

export function renderApp(path = "/login") {
    const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
    const user = userEvent.setup();
    const view = render(<App router={router} />);
    return { user, router, ...view };
}

export function renderTree(element: ReactElement) {
    return render(element);
}

export async function signInAs(
    email = "naledi@khanyisa.co.za",
    path = "/login",
) {
    const utils = renderApp(path);
    await screen.findByRole("heading", { name: "Log in" });
    await utils.user.type(screen.getByLabelText("Email"), email);
    await utils.user.type(screen.getByLabelText("Password"), "stockroom");
    await utils.user.click(screen.getByRole("button", { name: "Log in" }));
    return utils;
}

export function assertAccessible(container: HTMLElement) {
    for (const image of container.querySelectorAll("img")) {
        expect(image.getAttribute("alt")).not.toBeNull();
    }
    for (const button of container.querySelectorAll("button")) {
        const name =
            button.getAttribute("aria-label") ||
            button.textContent?.replace(/\s+/g, " ").trim();
        expect(name, "button accessible name").toBeTruthy();
    }
}
