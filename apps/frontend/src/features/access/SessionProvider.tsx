import {
    createContext,
    useContext,
    useEffect,
    useMemo,
    useState,
    useSyncExternalStore,
    type ReactNode,
} from "react";

import type { ApiProblem } from "../../shared/api/generated";
import type { RegisterRequest } from "../../shared/api/generated";
import { installSessionRefreshInterceptor } from "./session-interceptor";
import {
    getSessionSnapshot,
    loginWithPassword,
    logoutSession,
    registerWithPassword,
    restoreSession,
    subscribeSession,
    type Session,
} from "./session";

type SessionStatus = "loading" | "anonymous" | "authenticated";

type SessionContextValue = {
    login: (
        email: string,
        password: string,
    ) => Promise<{ session: Session | null; error?: ApiProblem }>;
    logout: () => Promise<void>;
    register: (
        email: string,
        password: string,
        accountType: RegisterRequest["accountType"],
    ) => Promise<{ session: Session | null; error?: ApiProblem }>;
    session: Session | null;
    status: SessionStatus;
};

const SessionContext = createContext<SessionContextValue | null>(null);

type SessionProviderProps = {
    children: ReactNode;
};

export function SessionProvider({ children }: SessionProviderProps) {
    const session = useSyncExternalStore(
        subscribeSession,
        getSessionSnapshot,
        () => null,
    );
    const [bootstrapped, setBootstrapped] = useState(false);

    useEffect(() => {
        installSessionRefreshInterceptor();
        void restoreSession().finally(() => {
            setBootstrapped(true);
        });
    }, []);

    const value = useMemo<SessionContextValue>(
        () => ({
            login: loginWithPassword,
            logout: logoutSession,
            register: registerWithPassword,
            session,
            status: !bootstrapped
                ? "loading"
                : session
                  ? "authenticated"
                  : "anonymous",
        }),
        [bootstrapped, session],
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
