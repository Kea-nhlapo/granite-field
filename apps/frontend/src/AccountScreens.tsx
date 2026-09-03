import { Badge, PersonaCoin, SectionCard, TopBar } from "./ui";
import {
  AlertTriangleIcon,
  CheckmarkIcon,
  ShieldCheckmarkIcon,
} from "./icons";

export function RiskScreen({
  onBack,
  internal,
}: {
  onBack: () => void;
  internal: boolean;
}) {
  if (!internal) {
    return (
      <>
        <TopBar title="Trust & Compliance" onBack={onBack} />
        <div
          className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
          style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
        >
          {/* Trust Score Hero */}
          <SectionCard className="p-6 text-center bg-white border border-[#E5E7EB]">
            <p className="app-overline">
              MoMo Network Trust Index
            </p>
            <div className="flex items-center justify-center gap-1 my-2">
              <span className="app-metric-hero">
                91
              </span>
              <span className="text-xl text-[#8E8E93] font-light">/100</span>
            </div>
            <div className="flex items-center justify-center gap-2">
              <Badge label="Tier-1 Verified Node" color="success" />
              <span className="app-caption text-[#595959]">+4 pts this cycle</span>
            </div>
          </SectionCard>

          {/* Trust Criteria Breakdown */}
          <SectionCard>
            <div className="p-3.5 border-b border-[#E5E7EB]">
              <p className="app-heading">
                Compliance & Reliability Factors
              </p>
            </div>
            {[
              {
                label: "CIPC Corporate Registration",
                desc: "Verified active private entity status",
                ok: true,
              },
              {
                label: "MoMo Settlement Liquidity Record",
                desc: "Zero supplier payment defaults across 14 months",
                ok: true,
              },
              {
                label: "Proof-of-Delivery Handover QR",
                desc: "100% of consignments verified by digital signature",
                ok: true,
              },
              {
                label: "Pending Rate Variance Dispute",
                desc: "1 invoice variance currently under mediation",
                ok: false,
              },
            ].map((h, i, arr) => (
              <div
                key={h.label}
                className={`flex gap-3 p-3.5 items-start ${i < arr.length - 1 ? "border-b border-[#E5E7EB]" : ""}`}
              >
                <div
                  className={`w-6 h-6 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${
                    h.ok
                      ? "bg-[#E3FCEF] text-[#00875A]"
                      : "bg-[#FFF3E0] text-[#F57C00]"
                  }`}
                >
                  {h.ok ? (
                    <CheckmarkIcon size={14} />
                  ) : (
                    <AlertTriangleIcon size={14} />
                  )}
                </div>
                <div>
                  <p className="app-caption-strong text-[#002B49]">
                    {h.label}
                  </p>
                  <p className="app-caption text-[#595959] mt-0.5">{h.desc}</p>
                </div>
              </div>
            ))}
          </SectionCard>
        </div>
      </>
    );
  }

  return (
    <>
      <TopBar
        title="Risk & Fraud Ops"
        onBack={onBack}
        action={<Badge label="Internal Security" color="navy" />}
      />
      <div
        className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        {/* Ops Risk Tiles */}
        <div className="grid grid-cols-2 gap-2.5">
          {[
            {
              label: "Fraud Signals",
              value: "8",
              sub: "4 high priority",
              warn: true,
            },
            {
              label: "Open Claims",
              value: "2",
              sub: "R34,000 at risk",
              warn: true,
            },
            {
              label: "Active Shipments",
              value: "14",
              sub: "Across 3 corridors",
              warn: false,
            },
            {
              label: "Network Stability",
              value: "94%",
              sub: "Normal range",
              warn: false,
            },
          ].map((s) => (
            <SectionCard
              key={s.label}
              className="p-3"
              style={
                s.warn
                  ? { borderLeftWidth: "3px", borderLeftColor: "#D32F2F" }
                  : undefined
              }
            >
              <p className="app-metric text-xl font-bold">
                {s.value}
              </p>
              <p className="app-heading mt-0.5">
                {s.label}
              </p>
              <p className="app-micro">{s.sub}</p>
            </SectionCard>
          ))}
        </div>

        {/* Signals List */}
        <div>
          <p className="app-overline mb-2">
            Automated Anomaly Detections
          </p>
          <SectionCard>
            {[
              {
                signal: "Driver telematics SIM swapped 3× in 30 days",
                severity: "high" as const,
                trips: 1,
              },
              {
                signal: "Delivery QR scanned outside geofence boundary",
                severity: "high" as const,
                trips: 1,
              },
              {
                signal: "Invoice unit rate variance exceeding 15%",
                severity: "medium" as const,
                trips: 2,
              },
              {
                signal: "Unscheduled highway stoppage on N12",
                severity: "low" as const,
                trips: 4,
              },
            ].map((f, i, arr) => (
              <div
                key={i}
                className={`flex gap-3 p-3 items-center ${i < arr.length - 1 ? "border-b border-[#E5E7EB]" : ""}`}
              >
                <div
                  className="w-2 h-2 rounded-full shrink-0"
                  style={{
                    backgroundColor:
                      f.severity === "high"
                        ? "#D32F2F"
                        : f.severity === "medium"
                        ? "#F57C00"
                        : "#595959",
                  }}
                />
                <div className="flex-1 min-w-0">
                  <p className="app-body font-medium leading-snug">
                    {f.signal}
                  </p>
                  <p className="app-micro">
                    {f.trips} trip{f.trips > 1 ? "s" : ""} logged
                  </p>
                </div>
                <Badge
                  label={f.severity.toUpperCase()}
                  color={
                    f.severity === "high"
                      ? "danger"
                      : f.severity === "medium"
                      ? "warning"
                      : "neutral"
                  }
                />
              </div>
            ))}
          </SectionCard>
        </div>

        {/* Regional Risk */}
        <div>
          <p className="app-overline mb-2">
            Regional Freight Incident Exposure
          </p>
          <SectionCard className="p-4 space-y-3">
            {[
              { r: "Johannesburg South (N1 Bypass)", v: 72 },
              { r: "Krugersdorp (N14 West)", v: 61 },
              { r: "Durban Freight Artery (N3)", v: 44 },
              { r: "Midrand Commercial (N1)", v: 28 },
              { r: "Cape Town Coastal Link (N2)", v: 19 },
            ].map((x) => (
              <div key={x.r} className="flex items-center gap-3">
                <span className="app-caption text-[#595959] flex-1 truncate">{x.r}</span>
                <div className="w-24 bg-[#E5E7EB] rounded-full h-1.5 overflow-hidden">
                  <div
                    className="h-1.5 rounded-full"
                    style={{
                      width: `${x.v}%`,
                      backgroundColor:
                        x.v > 60 ? "#D32F2F" : x.v > 40 ? "#F57C00" : "#00875A",
                    }}
                  />
                </div>
                <span className="app-caption-strong text-[#002B49] w-6 text-right">
                  {x.v}
                </span>
              </div>
            ))}
          </SectionCard>
        </div>
      </div>
    </>
  );
}

export function ProfileScreen({
  onBack,
  internal,
  onToggleInternal,
}: {
  onBack: () => void;
  internal: boolean;
  onToggleInternal: () => void;
}) {
  return (
    <>
      <TopBar title="Account & Preferences" onBack={onBack} />
      <div
        className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        {/* Persona Header */}
        <div className="flex flex-col items-center py-4 text-center">
          <PersonaCoin initials="MN" size={56} status="available" />
          <h2 className="app-title mt-2">
            Mama Nkosi Spaza Supply
          </h2>
          <p className="app-caption text-[#595959]">
            Soweto Node #7412 • Member since Jan 2025
          </p>
          <div className="mt-2">
            <Badge label="MoMo Trust: 91/100 (Prime)" color="success" />
          </div>
        </div>

        {/* Ops Mode Switch */}
        <SectionCard>
          <div className="flex items-center justify-between p-3.5 border-b border-[#E5E7EB]">
            <div>
              <p className="app-heading">
                Operations & Fraud Console
              </p>
              <p className="app-caption text-[#595959]">
                Enable risk dashboards and telematics inspection
              </p>
            </div>
            {/* Toggle Switch */}
            <button
              onClick={onToggleInternal}
              className="relative w-11 h-6 rounded-full transition-colors shrink-0"
              style={{
                backgroundColor: internal
                  ? "var(--momo-blue, #003E85)"
                  : "var(--momo-grey-inactive, #CCCCCC)",
              }}
              aria-label="Toggle Internal View"
            >
              <div
                className="absolute top-1 w-4 h-4 bg-white rounded-full shadow-xs transition-all"
                style={{ left: internal ? "calc(100% - 20px)" : 4 }}
              />
            </button>
          </div>

          {[
            { label: "Corporate Registration", value: "CIPC: 2024/003821/07" },
            { label: "Payment Settlement Rail", value: "MoMo PSB Settlement Gate" },
            { label: "Underwriting Class", value: "Transit Security Tier 2" },
            { label: "Cloud Region", value: "South Africa North (Azure ZA)" },
          ].map((r, i, arr) => (
            <div
              key={r.label}
              className={`flex items-center justify-between p-3.5 ${i < arr.length - 1 ? "border-b border-[#E5E7EB]" : ""}`}
            >
              <span className="app-caption text-[#595959]">{r.label}</span>
              <span className="app-caption-strong text-[#002B49]">
                {r.value}
              </span>
            </div>
          ))}
        </SectionCard>

        <p className="app-micro text-center pt-2">
          Powered by <strong className="text-[#003E85]">MoMo PSB</strong> • TradeMesh Enterprise SCM
        </p>
      </div>
    </>
  );
}
