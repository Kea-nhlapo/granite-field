import type { ReactNode } from "react";
import { useReducedMotion } from "motion/react";
import {
    MapPin,
    MessageCircle,
    Navigation2,
    Clock,
    Star,
    Store,
    Truck,
} from "lucide-react";
import { APIProvider, AdvancedMarker, Map as GoogleMap, Polyline } from "@vis.gl/react-google-maps";
import { Badge, PrimaryBtn, SecondaryBtn } from "./ui";
import { ChevronLeftIcon } from "./icons";
import { m, springs } from "./motion";

/** Soweto coordinates standing in for the matched business + supplier pair. */
const STORE_POSITION = { lat: -26.2678, lng: 27.8585 };
const SUPPLIER_POSITION = { lat: -26.2485, lng: 27.8912 };
const MAP_CENTER = { lat: -26.2582, lng: 27.8749 };

/**
 * Decorative, non-geographic street map. This app has no live map provider —
 * every other screen (route scoring, QR token) is a stylized mock in the same
 * spirit, so the backdrop here follows suit rather than pulling in a map SDK.
 */
function MapBackdrop() {
    return (
        <div
            className="absolute inset-0 overflow-hidden"
            style={{ backgroundColor: "#E8EBEE" }}
        >
            <svg
                viewBox="0 0 375 700"
                className="w-full h-full"
                preserveAspectRatio="xMidYMid slice"
            >
                <rect width="375" height="700" fill="#E8EBEE" />
                {/* soft green park block, echoes the reference's Prospect Park */}
                <rect
                    x="20"
                    y="90"
                    width="130"
                    height="150"
                    rx="10"
                    fill="#DCE6DC"
                />
                <rect
                    x="230"
                    y="380"
                    width="120"
                    height="160"
                    rx="10"
                    fill="#DCE6DC"
                />
                {/* city blocks */}
                {Array.from({ length: 26 }).map((_, i) => {
                    const x = (i % 5) * 80 + ((i * 37) % 20);
                    const y = Math.floor(i / 5) * 130 + ((i * 53) % 30);
                    const w = 40 + ((i * 17) % 30);
                    const h = 30 + ((i * 23) % 40);
                    return (
                        <rect
                            key={i}
                            x={x}
                            y={y}
                            width={w}
                            height={h}
                            rx="3"
                            fill="#DADEE2"
                        />
                    );
                })}
                {/* streets */}
                {[70, 150, 230, 310].map((x) => (
                    <line
                        key={`v${x}`}
                        x1={x}
                        y1="0"
                        x2={x}
                        y2="700"
                        stroke="#F5F6F7"
                        strokeWidth="6"
                    />
                ))}
                {[100, 220, 340, 460, 580].map((y) => (
                    <line
                        key={`h${y}`}
                        x1="0"
                        y1={y}
                        x2="375"
                        y2={y}
                        stroke="#F5F6F7"
                        strokeWidth="6"
                    />
                ))}
                <line
                    x1="0"
                    y1="0"
                    x2="375"
                    y2="700"
                    stroke="#F0F2F3"
                    strokeWidth="14"
                    opacity="0.6"
                />
            </svg>
        </div>
    );
}

function PinMarker({
  position,
  label,
  labelColor,
  fill,
  iconColor,
  icon,
}: {
  position: { lat: number; lng: number };
  label: string;
  labelColor: string;
  fill: string;
  iconColor: string;
  icon: ReactNode;
}) {
  return (
    <AdvancedMarker position={position}>
      <div className="flex flex-col items-center gap-1">
        <span
          className="app-micro font-semibold px-2 py-0.5 rounded-full whitespace-nowrap"
          style={{ backgroundColor: "#FFFFFF", color: labelColor, boxShadow: "var(--fluent-depth-card)" }}
        >
          {label}
        </span>
        <span
          className="w-8 h-8 rounded-full flex items-center justify-center border-2 border-white"
          style={{ backgroundColor: fill, color: iconColor, boxShadow: "var(--fluent-depth-card)" }}
        >
          {icon}
        </span>
      </div>
    </AdvancedMarker>
  );
}

/**
 * Live Google Map with the matched business and supplier plotted, connected by
 * a dashed route preview. Requires VITE_GOOGLE_MAPS_API_KEY (browser-restricted
 * key, separate from the backend's server-side Directions key) — see .env.example.
 */
function LiveMap({ apiKey }: { apiKey: string }) {
  return (
    <APIProvider apiKey={apiKey}>
      <GoogleMap
        className="absolute inset-0"
        defaultCenter={MAP_CENTER}
        defaultZoom={13}
        mapId="DEMO_MAP_ID"
        disableDefaultUI
        gestureHandling="greedy"
        style={{ width: "100%", height: "100%" }}
      >
        <Polyline
          path={[STORE_POSITION, SUPPLIER_POSITION]}
          strokeOpacity={0}
          strokeColor="#003E85"
          icons={[
            {
              icon: { path: "M 0,-1 0,1", strokeOpacity: 1, strokeColor: "#003E85", scale: 3 },
              offset: "0",
              repeat: "14px",
            },
          ]}
        />

        <PinMarker
          position={STORE_POSITION}
          label="Your Store"
          labelColor="#00875A"
          fill="#00875A"
          iconColor="#FFFFFF"
          icon={<Store size={15} strokeWidth={2} />}
        />

        <PinMarker
          position={SUPPLIER_POSITION}
          label="Thabo Distributors"
          labelColor="#003E85"
          fill="var(--momo-navy, #002B49)"
          iconColor="#FFCC00"
          icon={<Truck size={15} strokeWidth={2} />}
        />
      </GoogleMap>
    </APIProvider>
  );
}

function FloatingIconBtn({
    children,
    onClick,
    ariaLabel,
}: {
    children: ReactNode;
    onClick?: () => void;
    ariaLabel: string;
}) {
    return (
        <button
            onClick={onClick}
            aria-label={ariaLabel}
            className="w-10 h-10 rounded-full flex items-center justify-center shrink-0 transition-transform active:scale-95"
            style={{
                backgroundColor: "rgba(255,255,255,0.92)",
                color: "var(--momo-navy, #002B49)",
                boxShadow:
                    "var(--fluent-depth-hover, 0 4px 12px rgba(0,0,0,0.08))",
                backdropFilter: "blur(6px)",
            }}
        >
            {children}
        </button>
    );
}

function StatTile({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex-1 min-w-0">
            <p className="app-metric font-fluent-mono truncate">{value}</p>
            <p className="app-micro text-[#8E8E93] truncate">{label}</p>
        </div>
    );
}

export function SupplierMatchScreen({ onBack }: { onBack: () => void }) {
  const reduceMotion = useReducedMotion();
  const mapsApiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;

  return (
    <div className="h-full flex flex-col overflow-hidden relative">
      {mapsApiKey ? <LiveMap apiKey={mapsApiKey} /> : <MapBackdrop />}

      {/* Floating header, distinct from the app's yellow TopBar since this screen is map-first */}
      <div
        className="shrink-0 flex items-center justify-between px-4 z-20"
        style={{ paddingTop: "calc(14px + env(safe-area-inset-top, 0px))" }}
      >
        <FloatingIconBtn onClick={onBack} ariaLabel="Back">
          <ChevronLeftIcon size={18} />
        </FloatingIconBtn>

        <div
          className="flex items-center gap-1.5 px-3.5 h-10 rounded-full"
          style={{
            backgroundColor: "rgba(255,255,255,0.92)",
            boxShadow: "var(--fluent-depth-hover, 0 4px 12px rgba(0,0,0,0.08))",
            backdropFilter: "blur(6px)",
          }}
        >
          <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: "#00875A" }} />
          <span className="app-caption-strong text-[#002B49]">Supplier Matched</span>
        </div>

        <FloatingIconBtn ariaLabel="Message supplier">
          <MessageCircle size={17} strokeWidth={1.9} />
        </FloatingIconBtn>
      </div>

      {/* Route + pins overlay — only for the decorative fallback; the live map renders its own markers */}
      <div className="flex-1 relative">
        {!mapsApiKey && (
          <>
            <svg viewBox="0 0 375 500" className="absolute inset-0 w-full h-full pointer-events-none">
              <path
                d="M 90 380 C 140 300, 160 220, 250 120"
                fill="none"
                stroke="var(--momo-blue, #003E85)"
                strokeWidth="3"
                strokeDasharray="2 10"
                strokeLinecap="round"
              />
            </svg>

            {/* Start pin — Your Store */}
            <m.div
              className="absolute flex flex-col items-center gap-1"
              style={{ left: "24%", top: "76%", transform: "translate(-50%, -100%)" }}
              initial={reduceMotion ? undefined : { y: -10, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={springs.gentle}
            >
              <span
                className="app-micro font-semibold px-2 py-0.5 rounded-full whitespace-nowrap"
                style={{ backgroundColor: "#FFFFFF", color: "#00875A", boxShadow: "var(--fluent-depth-card)" }}
              >
                Your Store
              </span>
              <span
                className="w-8 h-8 rounded-full flex items-center justify-center border-2 border-white"
                style={{ backgroundColor: "#00875A", color: "#FFFFFF", boxShadow: "var(--fluent-depth-card)" }}
              >
                <Store size={15} strokeWidth={2} />
              </span>
            </m.div>

            {/* End pin — Supplier warehouse */}
            <m.div
              className="absolute flex flex-col items-center gap-1"
              style={{ left: "67%", top: "24%", transform: "translate(-50%, -100%)" }}
              initial={reduceMotion ? undefined : { y: -10, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              transition={{ ...springs.gentle, delay: reduceMotion ? 0 : 0.1 }}
            >
              <span
                className="app-micro font-semibold px-2 py-0.5 rounded-full whitespace-nowrap"
                style={{ backgroundColor: "#FFFFFF", color: "#003E85", boxShadow: "var(--fluent-depth-card)" }}
              >
                Thabo Distributors
              </span>
              <span
                className="w-8 h-8 rounded-full flex items-center justify-center border-2 border-white"
                style={{ backgroundColor: "var(--momo-navy, #002B49)", color: "#FFCC00", boxShadow: "var(--fluent-depth-card)" }}
              >
                <Truck size={15} strokeWidth={2} />
              </span>
            </m.div>

            {/* Live match pulse, midpoint of the route */}
            <div className="absolute" style={{ left: "46%", top: "50%", transform: "translate(-50%, -50%)" }}>
              {!reduceMotion && (
                <m.span
                  className="absolute inset-0 rounded-full"
                  style={{ backgroundColor: "var(--momo-yellow, #FFCC00)" }}
                  initial={{ scale: 1, opacity: 0.6 }}
                  animate={{ scale: 2.4, opacity: 0 }}
                  transition={{ duration: 1.6, repeat: Infinity, ease: "easeOut" }}
                />
              )}
              <span
                className="block w-3 h-3 rounded-full border-2 border-white"
                style={{ backgroundColor: "var(--momo-yellow, #FFCC00)", boxShadow: "0 2px 6px rgba(0,0,0,0.2)" }}
              />
            </div>
          </>
        )}
      </div>

      {/* Bottom overlay sheet */}
      <m.div
        className="shrink-0 relative z-20 bg-white rounded-t-2xl"
        style={{
          boxShadow: "0 -8px 24px rgba(0,0,0,0.10)",
          paddingBottom: "calc(16px + env(safe-area-inset-bottom, 0px))",
        }}
        initial={reduceMotion ? undefined : { y: 60, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={springs.snappy}
      >
        <div className="flex justify-center pt-2.5 pb-1">
          <span className="w-9 h-1 rounded-full" style={{ backgroundColor: "#E5E7EB" }} />
        </div>

        <div className="px-5 pt-1.5">
          <div className="flex items-center justify-between mb-3">
            <Badge label="Match Confirmed" color="success" />
            <span className="app-micro text-[#8E8E93]">Just now</span>
          </div>

          <div className="flex items-center gap-3">
                        <div
                            className="w-11 h-11 rounded-xl flex items-center justify-center shrink-0 app-caption-strong"
                            style={{
                                backgroundColor: "#EBF3FC",
                                color: "#003E85",
                                border: "1px solid #C7E0F4",
                            }}
                        >
                            T
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="app-heading truncate">
                                Thabo Distributors
                            </p>
                            <div className="flex items-center gap-1 app-caption text-[#595959]">
                                <Star
                                    size={12}
                                    className="text-[#F57C00] fill-[#F57C00]"
                                />
                                <span>4.8</span>
                                <span>•</span>
                                <MapPin size={12} strokeWidth={2} />
                                <span>3.2 km away</span>
                            </div>
                        </div>
                    </div>

                    <div
                        className="flex items-center mt-4 py-3 border-t border-b"
                        style={{
                            borderColor:
                                "var(--fluent-stroke-divider, #E5E7EB)",
                        }}
                    >
                        <StatTile label="Pickup ETA" value="18 min" />
                        <div
                            className="w-px h-8 shrink-0"
                            style={{
                                backgroundColor:
                                    "var(--fluent-stroke-divider, #E5E7EB)",
                            }}
                        />
                        <StatTile label="Distance" value="3.2 km" />
                        <div
                            className="w-px h-8 shrink-0"
                            style={{
                                backgroundColor:
                                    "var(--fluent-stroke-divider, #E5E7EB)",
                            }}
                        />
                        <StatTile label="Trip Time" value="~26 min" />
                    </div>

                    <div className="flex items-start gap-2.5 mt-4">
                        <div className="flex flex-col items-center pt-1 shrink-0">
                            <Navigation2
                                size={13}
                                className="text-[#00875A]"
                                strokeWidth={2.2}
                            />
                            <span
                                className="w-px h-5 my-0.5"
                                style={{ backgroundColor: "#D1D5DB" }}
                            />
                            <Clock
                                size={13}
                                className="text-[#003E85]"
                                strokeWidth={2.2}
                            />
                        </div>
                        <div className="flex-1 min-w-0 space-y-2.5">
                            <p className="app-caption text-[#1A1A1A] truncate">
                                Your Store — Fetsani Rd, Soweto
                            </p>
                            <p className="app-caption text-[#1A1A1A] truncate">
                                Thabo Distributors — 14 Falcon Ave
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-col gap-2.5 mt-5">
                        <PrimaryBtn label="Confirm & Start Trade" />
                        <SecondaryBtn
                            label="Message Supplier"
                            icon={<MessageCircle size={16} strokeWidth={2} />}
                        />
                    </div>
                </div>
            </m.div>
        </div>
    );
}
