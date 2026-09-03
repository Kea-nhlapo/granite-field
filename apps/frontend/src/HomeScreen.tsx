import type { Navigate, Screen } from "./types";
import { Badge, PersonaCoin, SectionCard, TopBar } from "./ui";
import {
  BoxIcon,
  DocumentTextIcon,
  RouteIcon,
  ShieldCheckmarkIcon,
  TruckIcon,
  ChevronRightIcon,
} from "./icons";

export function HomeScreen({ navigate }: { navigate: Navigate }) {
  return (
    <>
      <TopBar
        title="Overview"
        action={
          <PersonaCoin
            initials="MN"
            size={32}
            status="available"
            onClick={() => navigate({ id: "profile" })}
          />
        }
      />

      <div
        className="flex-1 fluent-scroll overflow-y-auto"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        {/* Tenant Hero Profile Card */}
        <div
          className="p-4 bg-white border-b"
          style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
        >
          <div className="flex items-start justify-between">
            <div>
              <p className="app-overline text-[#003E85] mb-0.5">
                MoMo Enterprise Node
              </p>
              <h1 className="app-title leading-snug">
                Mama Nkosi Spaza Supply
              </h1>
              <p className="app-caption mt-0.5">
                Soweto Distribution Node • Johannesburg
              </p>
            </div>
            <div className="text-right shrink-0">
              <Badge label="Trust 91/100" color="success" />
            </div>
          </div>

          {/* Key Metrics Bar */}
          <div
            className="grid grid-cols-3 gap-2 mt-4 pt-3 border-t"
            style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
          >
            {[
              { label: "Active Orders", value: "7", sub: "2 awaiting" },
              { label: "In Transit", value: "3", sub: "On schedule" },
              { label: "Freight Saved", value: "R2,340", sub: "+18% pooled" },
            ].map((m, i) => (
              <div
                key={m.label}
                className={`text-center ${i < 2 ? "border-r" : ""}`}
                style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
              >
                <p className="app-metric">
                  {m.value}
                </p>
                <p className="app-caption font-medium mt-0.5">
                  {m.label}
                </p>
                <p className="app-micro">{m.sub}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="p-4 space-y-4">
          {/* Active Shipment Focus Card */}
          <SectionCard className="p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <TruckIcon size={18} className="text-[#003E85]" />
                <span className="app-heading">
                  Active Consignment
                </span>
              </div>
              <button
                onClick={() => navigate({ id: "track" })}
                className="app-caption-strong text-[#003E85] inline-flex items-center gap-1 hover:underline"
              >
                Live Radar <ChevronRightIcon size={14} />
              </button>
            </div>

            <div className="bg-[#F8F9FA] p-3 rounded-xl border border-[#E5E7EB]">
              <div className="flex items-center justify-between">
                <span className="app-caption text-[#595959]">
                  SB-2026-9901
                </span>
                <Badge label="In Transit" color="brand" />
              </div>

              <p className="app-heading mt-1">
                Germiston Hub → Soweto CBD
              </p>
              <p className="app-caption text-[#595959] mt-0.5">
                4 businesses • Driver Sipho M. • ETA 14:30
              </p>

              {/* Progress bar in MoMo Blue */}
              <div className="mt-3">
                <div className="flex justify-between app-caption mb-1">
                  <span className="text-[#595959]">Consolidation Progress</span>
                  <span className="app-caption-strong text-[#003E85]">
                    68%
                  </span>
                </div>
                <div className="w-full bg-[#E5E7EB] rounded-full h-1.5 overflow-hidden">
                  <div
                    className="h-1.5 rounded-full transition-all"
                    style={{
                      width: "68%",
                      backgroundColor: "var(--momo-blue, #003E85)",
                    }}
                  />
                </div>
              </div>

              <div className="flex gap-2 mt-2.5">
                <Badge label="67 km left" color="neutral" />
                <Badge label="Route A (Fastest)" color="yellow" />
              </div>
            </div>
          </SectionCard>

          {/* Quick Actions */}
          <div>
            <p className="app-overline mb-2.5">
              Core Operations
            </p>
            <div className="grid grid-cols-2 gap-2.5">
              {[
                {
                  label: "Source Stock",
                  icon: <BoxIcon size={20} className="text-[#003E85]" />,
                  desc: "Browse supplier catalog & orders",
                  screen: { id: "source" } as Screen,
                },
                {
                  label: "Upload Invoice",
                  icon: <DocumentTextIcon size={20} className="text-[#003E85]" />,
                  desc: "Parse supplier bill & verify lines",
                  screen: { id: "orders_invoice" } as Screen,
                },
                {
                  label: "Route Dispatch",
                  icon: <RouteIcon size={20} className="text-[#003E85]" />,
                  desc: "Cluster freight & carrier match",
                  screen: { id: "routes" } as Screen,
                },
                {
                  label: "Trust & Risk",
                  icon: <ShieldCheckmarkIcon size={20} className="text-[#003E85]" />,
                  desc: "Fraud signals & credit ratings",
                  screen: { id: "risk" } as Screen,
                },
              ].map((q) => (
                <button
                  key={q.label}
                  onClick={() => navigate(q.screen)}
                  className="bg-white rounded-xl p-3 text-left border border-[#E5E7EB] hover:border-[#003E85] hover:shadow-xs active:bg-[#F8F9FA] transition-all flex flex-col justify-between"
                  style={{ minHeight: "105px" }}
                >
                  <div className="w-8 h-8 rounded-lg bg-[#EBF3FC] flex items-center justify-center mb-2">
                    {q.icon}
                  </div>
                  <div>
                    <p className="app-heading">
                      {q.label}
                    </p>
                    <p className="app-micro text-[#595959] mt-0.5 leading-snug">
                      {q.desc}
                    </p>
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Audit Stream */}
          <SectionCard className="p-4">
            <div className="flex items-center justify-between pb-2 border-b border-[#E5E7EB]">
              <span className="app-heading">
                Audit & Event Stream
              </span>
              <span className="app-micro text-[#8E8E93]">MoMo SCM Verified</span>
            </div>

            <div className="divide-y divide-[#E5E7EB]">
              {[
                {
                  time: "09:14",
                  event: "Invoice from Nkosi Foods flagged with 3 variances",
                  severity: "warning" as const,
                },
                {
                  time: "08:52",
                  event: "Carrier T-JHB-0047 verified for Soweto cluster",
                  severity: "success" as const,
                },
                {
                  time: "08:30",
                  event: "Supplier Thabo Distributors accepted network invite",
                  severity: "success" as const,
                },
              ].map((item, idx) => (
                <div key={idx} className="flex gap-2.5 py-2.5 items-start">
                  <div
                    className="w-2 h-2 rounded-full mt-1.5 shrink-0"
                    style={{
                      backgroundColor:
                        item.severity === "warning" ? "#F57C00" : "#00875A",
                    }}
                  />
                  <div className="flex-1 min-w-0">
                    <p className="app-caption leading-snug text-[#1A1A1A]">
                      {item.event}
                    </p>
                    <p className="app-micro text-[#8E8E93] mt-0.5">
                      {item.time} SAST
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </SectionCard>
        </div>
      </div>
    </>
  );
}
