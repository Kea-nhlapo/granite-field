import { useEffect, useState } from "react";
import { AnimatePresence, useReducedMotion, type PanInfo } from "motion/react";
import { Boxes, Radar, ShieldCheck, Eye, EyeOff } from "lucide-react";
import type { FormEvent, ReactNode } from "react";
import { PrimaryBtn, SecondaryBtn, SubtleBtn } from "./ui";
import momoLogo from "./assets/mtnmomo-logo.svg";
import familyBg from "./assets/onboarding/family.jpg";
import farmerCornBg from "./assets/onboarding/farmer-corn.png";
import farmerThumbsUpBg from "./assets/onboarding/farmer-thumbsup.png";
import { m, springs } from "./motion";

type Slide = {
    icon: ReactNode;
    title: string;
    highlight: string;
    body: string;
    bg: string;
};

const SLIDES: Slide[] = [
    {
        icon: <Boxes size={96} strokeWidth={1.5} className="text-white" />,
        title: "Welcome to",
        highlight: "TradeMesh",
        body: "Source, trade and grow your business with confidence — all in one place.",
        bg: familyBg,
    },
    {
        icon: <Radar size={96} strokeWidth={1.5} className="text-white" />,
        title: "Track Every",
        highlight: "Shipment",
        body: "Real-time visibility on every route, from pickup to final delivery.",
        bg: farmerCornBg,
    },
    {
        icon: (
            <ShieldCheck size={96} strokeWidth={1.5} className="text-white" />
        ),
        title: "Trade with",
        highlight: "Confidence",
        body: "Verified suppliers and secure escrow payments, powered by MoMo.",
        bg: farmerThumbsUpBg,
    },
];

const SWIPE_THRESHOLD = 60;

function MascotBadge({
    children,
    spin = false,
}: {
    children: ReactNode;
    spin?: boolean;
}) {
    const style = {
        width: 176,
        height: 176,
        borderRadius: "var(--fluent-radius-xl, 16px)",
        background:
            "linear-gradient(135deg, var(--momo-blue, #003E85), var(--momo-navy, #002B49))",
        boxShadow: "0 12px 28px rgba(0, 62, 133, 0.28)",
    };

    const reduceMotion = useReducedMotion();

    if (!spin || reduceMotion) {
        return (
            <div
                className="flex items-center justify-center shrink-0"
                style={style}
            >
                {children}
            </div>
        );
    }

    return (
        <m.div
            className="flex items-center justify-center shrink-0"
            style={style}
            initial={{ rotate: -180, scale: 0.4, opacity: 0 }}
            animate={{ rotate: 0, scale: 1, opacity: 1 }}
            transition={springs.gentle}
        >
            {children}
        </m.div>
    );
}

function SplashIntro() {
    const reduceMotion = useReducedMotion();

    return (
        <m.div
            className="absolute inset-0 flex items-center justify-center z-10"
            style={{
                background:
                    "linear-gradient(160deg, var(--momo-blue, #003E85), var(--momo-navy, #002B49))",
            }}
            initial={{ opacity: 1 }}
            exit={{ opacity: 0, scale: reduceMotion ? 1 : 1.15 }}
            transition={{ duration: 0.4, ease: "easeInOut" }}
        >
            <m.div
                className="w-24 h-24 rounded-2xl flex items-center justify-center"
                style={{
                    background: "rgba(255,255,255,0.12)",
                    border: "1px solid rgba(255,255,255,0.25)",
                }}
                initial={{ scale: 0.85, opacity: 0 }}
                animate={
                    reduceMotion
                        ? { scale: 1, opacity: 1 }
                        : { scale: [0.85, 1.06, 1], opacity: 1 }
                }
                transition={{ duration: 0.9, ease: "easeOut" }}
            >
                <ShieldCheck
                    size={44}
                    strokeWidth={1.75}
                    className="text-white"
                />
            </m.div>
        </m.div>
    );
}

const slideVariants = {
    enter: (direction: number) => ({
        x: direction > 0 ? 80 : -80,
        opacity: 0,
    }),
    center: { x: 0, opacity: 1 },
    exit: (direction: number) => ({
        x: direction > 0 ? -80 : 80,
        opacity: 0,
    }),
};

export function OnboardingScreen({ onDone }: { onDone: () => void }) {
    const [[index, direction], setIndexState] = useState<[number, number]>([
        0, 0,
    ]);
    const [showSplash, setShowSplash] = useState(true);
    const reduceMotion = useReducedMotion();
    const slide = SLIDES[index] ?? SLIDES[0]!;
    const isLast = index === SLIDES.length - 1;

    useEffect(() => {
        const timer = setTimeout(
            () => setShowSplash(false),
            reduceMotion ? 300 : 1300,
        );
        return () => clearTimeout(timer);
    }, [reduceMotion]);

    function go(newIndex: number, dir: number) {
        if (newIndex < 0 || newIndex >= SLIDES.length) return;
        setIndexState([newIndex, dir]);
    }

    function next() {
        if (isLast) {
            onDone();
            return;
        }
        go(index + 1, 1);
    }

    function cycleNext() {
        go(isLast ? 0 : index + 1, isLast ? -1 : 1);
    }

    function handleDragEnd(_: unknown, info: PanInfo) {
        if (info.offset.x < -SWIPE_THRESHOLD) {
            go(index + 1, 1);
        } else if (info.offset.x > SWIPE_THRESHOLD) {
            go(index - 1, -1);
        }
    }

    return (
        <div
            className="h-full flex flex-col overflow-hidden relative"
            style={{ backgroundColor: "#FFFFFF" }}
        >
            <div className="shrink-0 flex justify-end px-5 pt-5">
                <SubtleBtn label="Skip" onClick={onDone} />
            </div>

            <div className="flex-1 relative overflow-hidden">
                <AnimatePresence initial={false} custom={direction}>
                    <m.div
                        key={index}
                        custom={direction}
                        variants={slideVariants}
                        initial="enter"
                        animate="center"
                        exit="exit"
                        transition={springs.snappy}
                        drag="x"
                        dragConstraints={{ left: 0, right: 0 }}
                        dragElastic={0.6}
                        onDragEnd={handleDragEnd}
                        onClick={cycleNext}
                        role="button"
                        tabIndex={0}
                        aria-label="Next slide"
                        onKeyDown={(e) => {
                            if (e.key === "Enter" || e.key === " ") {
                                e.preventDefault();
                                cycleNext();
                            }
                        }}
                        className="absolute inset-0 flex flex-col items-center justify-center px-8 text-center gap-8 cursor-pointer active:cursor-grabbing"
                    >
                        <div
                            aria-hidden="true"
                            className="absolute inset-0 pointer-events-none"
                            style={{
                                backgroundImage: `url(${slide.bg})`,
                                backgroundSize: "cover",
                                backgroundPosition: "center",
                                opacity: 0.1,
                                zIndex: -1,
                                maskImage:
                                    "radial-gradient(ellipse at center, black 45%, transparent 85%)",
                                WebkitMaskImage:
                                    "radial-gradient(ellipse at center, black 45%, transparent 85%)",
                            }}
                        />

                        <MascotBadge spin={index === 0}>
                            {slide.icon}
                        </MascotBadge>

                        <div className="flex flex-col gap-3">
                            <h1
                                className="text-2xl font-bold leading-tight"
                                style={{ color: "var(--momo-navy, #002B49)" }}
                            >
                                {slide.title}{" "}
                                <span
                                    style={{
                                        color: "var(--momo-blue, #003E85)",
                                    }}
                                >
                                    {slide.highlight}
                                </span>
                            </h1>
                            <p
                                className="text-sm leading-relaxed"
                                style={{
                                    color: "var(--fluent-text-secondary, #595959)",
                                }}
                            >
                                {slide.body}
                            </p>
                        </div>
                    </m.div>
                </AnimatePresence>
            </div>

            <div className="shrink-0 flex items-center justify-center gap-2 pb-8">
                {SLIDES.map((_, i) => (
                    <button
                        key={i}
                        aria-label={`Go to slide ${i + 1}`}
                        onClick={() => go(i, i > index ? 1 : -1)}
                        className="rounded-full transition-all"
                        style={{
                            width: i === index ? 24 : 8,
                            height: 8,
                            backgroundColor:
                                i === index
                                    ? "var(--momo-navy, #002B49)"
                                    : "var(--momo-blue-border, #C7E0F4)",
                        }}
                    />
                ))}
            </div>

            <div
                className="shrink-0 flex flex-col gap-2.5 px-5"
                style={{
                    paddingBottom:
                        "calc(20px + env(safe-area-inset-bottom, 0px))",
                }}
            >
                <PrimaryBtn
                    label={isLast ? "Get Started" : "Continue"}
                    onClick={next}
                    className="h-12 text-base"
                />
                <SecondaryBtn
                    label="Sign In"
                    onClick={onDone}
                    className="h-12 text-base"
                />
                <p
                    className="text-center text-xs pt-1"
                    style={{ color: "var(--fluent-text-tertiary, #8E8E93)" }}
                >
                    By continuing, you agree to TradeMesh's{" "}
                    <span
                        style={{
                            color: "var(--momo-blue, #003E85)",
                            fontWeight: 600,
                        }}
                    >
                        Terms of Service
                    </span>{" "}
                    and{" "}
                    <span
                        style={{
                            color: "var(--momo-blue, #003E85)",
                            fontWeight: 600,
                        }}
                    >
                        Privacy Policy
                    </span>
                    .
                </p>
            </div>

            <AnimatePresence>{showSplash && <SplashIntro />}</AnimatePresence>
        </div>
    );
}

export function LoginScreen({
    authenticationReady,
    onRegistered,
    onSignedIn,
}: {
    authenticationReady: boolean;
    onRegistered: (
        email: string,
        password: string,
        accountType: "BUSINESS_OWNER" | "SUPPLIER",
    ) => Promise<string | undefined>;
    onSignedIn: (
        email: string,
        password: string,
    ) => Promise<string | undefined>;
}) {
    const [mode, setMode] = useState<"login" | "register">("login");
    const [accountType, setAccountType] = useState<
        "BUSINESS_OWNER" | "SUPPLIER"
    >("BUSINESS_OWNER");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [message, setMessage] = useState<string>();

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!authenticationReady || submitting || !email.trim() || !password) {
            return;
        }
        setSubmitting(true);
        setMessage(undefined);
        try {
            setMessage(
                mode === "login"
                    ? await onSignedIn(email.trim(), password)
                    : await onRegistered(email.trim(), password, accountType),
            );
        } finally {
            setSubmitting(false);
        }
    }

    const inputStyle = {
        height: 48,
        borderColor: "var(--fluent-stroke-default, #D1D5DB)",
        borderRadius: "var(--fluent-radius-md, 8px)",
        color: "var(--fluent-text-primary, #1A1A1A)",
    };

    return (
        <div
            className="h-full flex flex-col overflow-y-auto fluent-scroll"
            style={{ backgroundColor: "#FFFFFF" }}
        >
            <m.div
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, ease: "easeOut" }}
                className="flex-1 flex flex-col px-6 pt-10 pb-6"
            >
                <div
                    className="w-20 h-20 flex items-center justify-center shrink-0 mx-auto"
                    style={{
                        borderRadius: "var(--fluent-radius-lg, 12px)",
                        background:
                            "linear-gradient(135deg, var(--momo-blue, #003E85), var(--momo-navy, #002B49))",
                    }}
                >
                    <ShieldCheck
                        size={38}
                        strokeWidth={1.75}
                        className="text-white"
                    />
                </div>

                <h1
                    className="text-3xl font-bold mt-6 text-center"
                    style={{ color: "var(--momo-navy, #002B49)" }}
                >
                    {mode === "login" ? "Welcome Back!" : "Create Account"}
                </h1>
                <p
                    className="text-sm mt-2 text-center"
                    style={{ color: "var(--fluent-text-secondary, #595959)" }}
                >
                    {mode === "login"
                        ? "Your trade journey continues"
                        : "New accounts start with R50 in the sandbox wallet"}
                </p>

                <div
                    className="mt-5 pl-3 text-sm italic"
                    style={{
                        borderLeft:
                            "3px solid var(--momo-blue-border, #C7E0F4)",
                        color: "var(--fluent-text-secondary, #595959)",
                    }}
                >
                    "Source, trade and grow — a click away."
                </div>

                <form className="flex flex-col gap-3 mt-8" onSubmit={submit}>
                    {mode === "login" && (
                        <button
                            type="button"
                            className="h-10 w-full rounded-lg border border-[#003E85] text-sm font-semibold text-[#003E85] hover:bg-[#EBF3FC]"
                            onClick={() => {
                                setEmail("lungile.mooketsi@trademesh.local");
                                setMessage(undefined);
                            }}
                        >
                            Use Lungile's supplier account
                        </button>
                    )}
                    <input
                        type="email"
                        aria-label="Email address"
                        autoComplete="email"
                        placeholder="Email address"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        className="w-full px-4 border text-sm outline-none focus:border-[var(--momo-blue,#003E85)]"
                        style={inputStyle}
                    />
                    <div className="relative">
                        <input
                            type={showPassword ? "text" : "password"}
                            aria-label="Password"
                            autoComplete={
                                mode === "login"
                                    ? "current-password"
                                    : "new-password"
                            }
                            placeholder="Password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            className="w-full px-4 pr-11 border text-sm outline-none focus:border-[var(--momo-blue,#003E85)]"
                            style={inputStyle}
                        />
                        <button
                            type="button"
                            aria-label={
                                showPassword ? "Hide password" : "Show password"
                            }
                            onClick={() => setShowPassword((v) => !v)}
                            className="absolute right-3 top-1/2 -translate-y-1/2"
                            style={{ color: "var(--momo-blue, #003E85)" }}
                        >
                            {showPassword ? (
                                <EyeOff size={18} strokeWidth={1.75} />
                            ) : (
                                <Eye size={18} strokeWidth={1.75} />
                            )}
                        </button>
                    </div>
                    {mode === "register" && (
                        <select
                            aria-label="Account type"
                            value={accountType}
                            onChange={(event) =>
                                setAccountType(
                                    event.target.value as
                                        "BUSINESS_OWNER" | "SUPPLIER",
                                )
                            }
                            className="w-full px-4 border text-sm bg-white"
                            style={inputStyle}
                        >
                            <option value="BUSINESS_OWNER">SME buyer</option>
                            <option value="SUPPLIER">Supplier</option>
                        </select>
                    )}
                    <div className="mt-3 w-[56%] mx-auto">
                        <PrimaryBtn
                            label={
                                submitting
                                    ? mode === "login"
                                        ? "Signing in…"
                                        : "Creating account…"
                                    : mode === "login"
                                      ? "Continue"
                                      : "Create account"
                            }
                            type="submit"
                            disabled={
                                !authenticationReady ||
                                submitting ||
                                !email.trim() ||
                                !password
                            }
                            className="h-12 text-base"
                        />
                    </div>
                </form>

                <div className="flex justify-end mt-2">
                    <SubtleBtn label="Forgot Password?" />
                </div>

                <p
                    className="text-xs mt-6"
                    style={{ color: "var(--fluent-text-tertiary, #8E8E93)" }}
                >
                    By continuing, you agree to TradeMesh's{" "}
                    <span
                        style={{
                            color: "var(--momo-blue, #003E85)",
                            fontWeight: 600,
                        }}
                    >
                        Terms &amp; Conditions
                    </span>{" "}
                    and{" "}
                    <span
                        style={{
                            color: "var(--momo-blue, #003E85)",
                            fontWeight: 600,
                        }}
                    >
                        Privacy Policy
                    </span>
                    .
                </p>

                {message && (
                    <p
                        role="status"
                        aria-live="polite"
                        className="text-sm mt-4 text-center"
                        style={{ color: "var(--fluent-danger, #D32F2F)" }}
                    >
                        {message}
                    </p>
                )}

                <div className="flex items-center gap-3 mt-4">
                    <div
                        className="h-px flex-1"
                        style={{
                            backgroundColor:
                                "var(--fluent-stroke-divider, #E5E7EB)",
                        }}
                    />
                    <span
                        className="text-xs"
                        style={{
                            color: "var(--fluent-text-tertiary, #8E8E93)",
                        }}
                    >
                        or
                    </span>
                    <div
                        className="h-px flex-1"
                        style={{
                            backgroundColor:
                                "var(--fluent-stroke-divider, #E5E7EB)",
                        }}
                    />
                </div>
                <button
                    type="button"
                    onClick={() =>
                        setMessage(
                            "MoMo sign-in requires the hosted consent challenge. Use password sign-in for this build.",
                        )
                    }
                    className="mt-4 h-12 w-[56%] mx-auto flex items-center justify-center gap-2.5 text-sm font-semibold border"
                    style={{
                        borderRadius: "var(--fluent-radius-md, 8px)",
                        borderColor: "var(--momo-blue, #003E85)",
                        color: "#FFFFFF",
                        backgroundColor: "var(--momo-blue, #003E85)",
                    }}
                >
                    <img
                        src={momoLogo}
                        alt=""
                        aria-hidden="true"
                        className="h-7 w-7 object-contain"
                    />
                    Sign in with MoMo
                </button>

                <div className="flex items-center gap-3 mt-6">
                    <div
                        className="h-px flex-1"
                        style={{
                            backgroundColor:
                                "var(--fluent-stroke-divider, #E5E7EB)",
                        }}
                    />
                    <span
                        className="text-xs"
                        style={{
                            color: "var(--fluent-text-tertiary, #8E8E93)",
                        }}
                    >
                        Not a member?
                    </span>
                    <div
                        className="h-px flex-1"
                        style={{
                            backgroundColor:
                                "var(--fluent-stroke-divider, #E5E7EB)",
                        }}
                    />
                </div>
                <button
                    type="button"
                    onClick={() => {
                        setMode((current) =>
                            current === "login" ? "register" : "login",
                        );
                        setMessage(undefined);
                    }}
                    className="text-center text-sm font-semibold mt-2"
                    style={{ color: "var(--momo-blue, #003E85)" }}
                >
                    {mode === "login" ? "Sign Up" : "Back to sign in"}
                </button>
            </m.div>
        </div>
    );
}
