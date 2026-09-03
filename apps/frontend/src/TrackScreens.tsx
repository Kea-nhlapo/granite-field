import { useState } from "react";
import type { Navigate } from "./types";
import {
  Badge,
  BottomDock,
  PrimaryBtn,
  SecondaryBtn,
  SectionCard,
  TopBar,
} from "./ui";
import {
  AlertTriangleIcon,
  CheckmarkIcon,
  ChevronRightIcon,
  QrCodeIcon,
  RadarIcon,
  ShieldCheckmarkIcon,
} from "./icons";

export function TrackScreen({ navigate }: { navigate: Navigate }) {
  return (
    <>
      <TopBar title="Consignment Radar" />
      <div
        className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        {/* Active Shipment Status */}
        <SectionCard className="p-4" style={{ borderLeftWidth: "4px", borderLeftColor: "#003E85" }}>
          <div className="flex items-center justify-between">
            <div>
              <p className="app-caption text-[#595959]">
                SB-2026-9901
              </p>
              <p className="app-heading mt-0.5">
                Germiston Hub → Soweto CBD
              </p>
            </div>
            <Badge label="In Transit" color="brand" />
          </div>

          <div className="mt-3">
            <div className="flex justify-between app-caption mb-1">
              <span className="text-[#595959]">Corridor Completion</span>
              <span className="app-caption-strong text-[#003E85]">
                68% (67 km left)
              </span>
            </div>
            <div className="w-full bg-[#E5E7EB] rounded-full h-1.5 overflow-hidden">
              <div
                className="h-1.5 rounded-full"
                style={{
                  width: "68%",
                  backgroundColor: "var(--momo-blue, #003E85)",
                }}
              />
            </div>
          </div>

          <p className="app-caption text-[#595959] mt-2">
            Target ETA: <strong className="app-caption-strong text-[#002B49]">14:30 SAST</strong> • Driver: Sipho Mthembu (T-JHB-0047)
          </p>
        </SectionCard>

        {/* Timeline */}
        <SectionCard className="p-4">
          <p className="app-heading mb-3">
            Live Telematics Milestones
          </p>
          <div className="relative pl-6 space-y-4">
            {/* Timeline vertical bar */}
            <div
              className="absolute left-2.5 top-2 bottom-2 w-0.5"
              style={{ backgroundColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
            />

            {[
              { time: "07:30", label: "Consignment sealed at Germiston Hub", state: "done" },
              { time: "09:14", label: "En route via N1 + R21 Bypass", state: "done" },
              { time: "10:00", label: "Checkpoint: N12/R21 Telematics verified", state: "done" },
              { time: "14:30", label: "Scheduled arrival at Soweto Distribution Node", state: "active" },
              { time: "~15:00", label: "Handover proof-of-delivery scan", state: "pending" },
            ].map((e, i) => (
              <div key={i} className="relative flex items-start gap-3">
                <div
                  className={`absolute -left-6 w-5 h-5 rounded-full flex items-center justify-center shrink-0 z-10 ${
                    e.state === "done"
                      ? "bg-[#00875A] text-white"
                      : e.state === "active"
                      ? "bg-[#003E85] text-white ring-2 ring-[#C7E0F4]"
                      : "bg-white border border-[#D1D5DB] text-[#8E8E93]"
                  }`}
                >
                  {e.state === "done" ? (
                    <CheckmarkIcon size={12} />
                  ) : (
                    <span className="w-1.5 h-1.5 rounded-full bg-current" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <p
                    className={`app-body leading-snug ${
                      e.state === "done"
                        ? "text-[#1A1A1A] font-medium"
                        : e.state === "active"
                        ? "text-[#003E85] font-semibold"
                        : "text-[#8E8E93]"
                    }`}
                  >
                    {e.label}
                  </p>
                  <p className="app-micro mt-0.5">
                    {e.time} SAST
                  </p>
                </div>
              </div>
            ))}
          </div>
        </SectionCard>

        {/* QR Verification Card */}
        <button
          onClick={() => navigate({ id: "track_qr" })}
          className="w-full bg-white rounded-xl p-3.5 border border-[#E5E7EB] hover:border-[#003E85] hover:shadow-xs active:bg-[#F8F9FA] transition-all flex items-center gap-3.5 text-left"
        >
          <div className="w-10 h-10 rounded-lg bg-[#EBF3FC] text-[#003E85] flex items-center justify-center shrink-0 border border-[#C7E0F4]">
            <QrCodeIcon size={22} />
          </div>
          <div className="flex-1 min-w-0">
            <p className="app-heading">
              Delivery Verification QR Token
            </p>
            <p className="app-caption text-[#595959] mt-0.5">
              Present to driver to confirm custody handover & release MoMo escrow
            </p>
          </div>
          <ChevronRightIcon size={18} className="text-[#8E8E93]" />
        </button>

        {/* Telematics Audit */}
        <SectionCard className="p-4">
          <div className="flex items-center gap-1.5 mb-2.5">
            <ShieldCheckmarkIcon size={16} className="text-[#003E85]" />
            <p className="app-heading">
              Cryptographic Trip Telematics Log
            </p>
          </div>
          <div className="divide-y divide-[#E5E7EB]">
            {[
              { event: "Departure QR cryptographic seal signed", time: "07:31", ok: true },
              { event: "Minor corridor speed variance logged (N12)", time: "08:02", ok: false },
              { event: "Telemetry re-aligned: authorized refuel stop", time: "08:08", ok: true },
            ].map((r, i) => (
              <div key={i} className="py-2 flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  {r.ok ? (
                    <span className="w-1.5 h-1.5 rounded-full bg-[#00875A] shrink-0" />
                  ) : (
                    <span className="w-1.5 h-1.5 rounded-full bg-[#F57C00] shrink-0" />
                  )}
                  <span className="app-caption text-[#595959] truncate">{r.event}</span>
                </div>
                <span className="app-micro shrink-0">
                  {r.time}
                </span>
              </div>
            ))}
          </div>
        </SectionCard>
      </div>
    </>
  );
}

export function QRScreen({ onBack }: { onBack: () => void }) {
  const [verified, setVerified] = useState(false);
  const pattern = [
    0, 1, 3, 5, 6, 7, 8, 10, 12, 14, 15, 16, 18, 20, 21, 22, 24, 26, 28, 29, 30,
    32, 34, 35, 36, 37, 40, 42, 43, 45, 47, 48,
  ];

  return (
    <>
      <TopBar title="Custody Verification QR" onBack={onBack} />
      <div
        className="flex-1 fluent-scroll overflow-y-auto flex flex-col items-center justify-center p-6 gap-5"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        {/* QR Code Container */}
        <div className="bg-white p-6 rounded-2xl border border-[#E5E7EB] shadow-sm flex flex-col items-center">
          <div
            className="grid gap-1 p-2 bg-white"
            style={{ gridTemplateColumns: "repeat(7, 1fr)" }}
          >
            {Array.from({ length: 49 }).map((_, i) => {
              const isEdge = i < 7 || i > 41 || i % 7 === 0 || i % 7 === 6;
              const isCornerFinder =
                (i <= 2 || (i >= 4 && i <= 6) || i === 7 || i === 13 || i === 14 || i === 20) ||
                (i >= 35 && i <= 37) || (i >= 42 && i <= 44);
              return (
                <div
                  key={i}
                  className="w-7 h-7 rounded-xs transition-colors"
                  style={{
                    backgroundColor:
                      isCornerFinder
                        ? "#FFCC00"
                        : isEdge || pattern.includes(i)
                        ? "#002B49"
                        : "#FFFFFF",
                  }}
                />
              );
            })}
          </div>

          <p className="text-center app-caption-strong text-[#003E85] mt-3">
            SB-2026-9901 • MOMO-TOKEN #4812-7X
          </p>
        </div>

        <div className="w-full space-y-1 text-center">
          <p className="app-heading">
            Present this code to the fleet driver upon arrival
          </p>
          <p className="app-caption text-[#595959]">
            14 Mofolo Crescent, Soweto Node • Consignment SB-2026-9901
          </p>
          <p className="app-micro mt-1">
            Single-use token • Handover escrow releases immediately upon scan
          </p>
        </div>

        {verified && (
          <div
            className="w-full p-3.5 rounded-xl border text-center transition-all animate-fade-in"
            style={{
              backgroundColor: "#E3FCEF",
              borderColor: "#A3E7C9",
            }}
          >
            <div className="flex items-center justify-center gap-1.5 app-caption-strong text-[#00875A]">
              <CheckmarkIcon size={16} />
              <span>Consignment Custody Handover Confirmed</span>
            </div>
            <p className="app-micro text-[#00875A] mt-0.5">
              15:02:44 SAST • Hash verified: REF-F9K2-901 • Escrow released
            </p>
          </div>
        )}
      </div>

      <BottomDock>
        {!verified ? (
          <PrimaryBtn
            label="Simulate Driver Terminal Scan"
            onClick={() => setVerified(true)}
          />
        ) : (
          <SecondaryBtn label="Return to Live Tracking" onClick={onBack} />
        )}
      </BottomDock>
    </>
  );
}
