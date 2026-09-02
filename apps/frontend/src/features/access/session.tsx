import {
    createContext,
    useContext,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from "react";

import { api } from "../../shared/api/client";
import { ApiError, isUnauthorized } from "../../shared/api/errors";
import type { Session } from "../../shared/api/generated";

type SessionContextValue = {
    session: Session | null;
    loading: boolean;
    login: (email: string, password: string) => Promise<void>;
    logout: () => Promise<void>;
    refresh: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
    const [session, setSession] = useState<Session | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        api.getSession()
            .then((next) => {
                if (!cancelled) {
                    setSession(next);
                }
            })
            .catch((error: unknown) => {
                if (!cancelled && isUnauthorized(error)) {
                    setSession(null);
                }
            })
            .finally(() => {
                if (!cancelled) {
                    setLoading(false);
                }
            });
        return () => {
            cancelled = true;
        };
    }, []);

    const value = useMemo<SessionContextValue>(
        () => ({
            session,
            loading,
            async login(email, password) {
                const next = await api.login(email, password);
                setSession(next);
            },
            async logout() {
                await api.logout();
                setSession(null);
            },
            async refresh() {
                try {
                    await api.refreshSession();
                    setSession(await api.getSession());
                } catch (error) {
                    if (error instanceof ApiError && error.status === 401) {
                        setSession(null);
                    }
                    throw error;
                }
            },
        }),
        [loading, session],
    );

    return (
        <SessionContext.Provider value={value}>
            {children}
        </SessionContext.Provider>
    );
}

export function useSession() {
    const value = useContext(SessionContext);
    if (!value) {
        throw new Error("useSession must be used within SessionProvider");
    }
    return value;
}
