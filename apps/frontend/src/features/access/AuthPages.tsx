import { Link } from "react-router";

export function UnauthorizedPage() {
    return (
        <main className="p-8 max-w-md mx-auto">
            <h1 className="text-xl font-semibold">Sign in required</h1>
            <p className="text-sm text-gray-600 mt-2">
                Your session is missing or has expired. Sign in again to
                continue.
            </p>
            <Link
                className="text-sm font-semibold mt-4 inline-block"
                to="/login"
            >
                Go to log in
            </Link>
        </main>
    );
}

export function ForbiddenPage() {
    return (
        <main className="p-8 max-w-md mx-auto">
            <h1 className="text-xl font-semibold">You cannot open this area</h1>
            <p className="text-sm text-gray-600 mt-2">
                You are signed in, but this view is reserved for another role.
            </p>
            <Link className="text-sm font-semibold mt-4 inline-block" to="/app">
                Back to the workspace
            </Link>
        </main>
    );
}
