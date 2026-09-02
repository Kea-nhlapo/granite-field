import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_DOCUMENTS !== "1")(
    "local backend document review",
    () => {
        it("registers an invoice through the generated client", async () => {
            const { authLogin, fileStorageUpload } =
                await import("../../shared/api/app-api");
            const { setApiAccessToken } =
                await import("../../shared/api/client");
            const login = await authLogin({
                body: {
                    email: "owner@example.com",
                    password: "correct-horse",
                },
            });
            expect(login.error).toBeUndefined();
            setApiAccessToken(login.data?.accessToken);
            expect(fileStorageUpload).toBeTypeOf("function");
        });
    },
);
