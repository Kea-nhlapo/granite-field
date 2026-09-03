import type { Navigate } from "./types";
import {
  Badge,
  BottomDock,
  PrimaryBtn,
  Row,
  SecondaryBtn,
  SectionCard,
  TopBar,
} from "./ui";
import { ChevronRightIcon, RouteIcon, TruckIcon } from "./icons";

export function RoutesScreen({ navigate }: { navigate: Navigate }) {
  return (
    <>
      <TopBar title="Route Dispatch" />
      <div
        className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        {/* Consignment Cluster */}
        <SectionCard className="p-4">
          <div className="flex items-center justify-between mb-3">
            <div>
              <p className="app-heading">
                Soweto Consignment Cluster
              </p>
              <p className="app-caption text-[#595959]">
                Consolidated freight pool
              </p>
            </div>
            <Badge label="4 Businesses" color="brand" />
          </div>

          <div className="divide-y divide-[#E5E7EB]">
            {[
              { name: "Mama Nkosi Spaza Supply", weight: "710 kg" },
              { name: "Phindile's Spaza", weight: "240 kg" },
              { name: "Vusi Hardware Store", weight: "400 kg" },
              { name: "Mama D Salon Supplies", weight: "60 kg" },
            ].map((b) => (
              <div key={b.name} className="flex items-center justify-between py-2">
                <div className="flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-[#FFCC00]" />
                  <span className="app-caption-strong">{b.name}</span>
                </div>
                <span className="app-caption text-[#595959]">{b.weight}</span>
              </div>
            ))}
          </div>

          <div
            className="mt-3 pt-3 border-t flex items-center justify-between"
            style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
          >
            <span className="app-caption text-[#595959]">
              Aggregated Load:{" "}
              <strong className="app-caption-strong text-[#002B49]">1,410 kg</strong>
            </span>
            <span className="app-caption-strong text-[#00875A]">
              Pooled Savings: R420–R680
            </span>
          </div>
        </SectionCard>

        {/* Matched Carrier */}
        <div>
          <p className="app-overline mb-2">
            Matched Fleet Carrier
          </p>
          <SectionCard className="p-4">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-lg bg-[#EBF3FC] flex items-center justify-center text-[#003E85]">
                  <TruckIcon size={16} />
                </div>
                <span className="app-caption-strong text-[#003E85]">
                  T-JHB-0047
                </span>
              </div>
              <span className="app-caption text-[#595959]">
                ★ 4.9 • 312 trips
              </span>
            </div>

            <p className="app-heading">Sipho Mthembu</p>
            <p className="app-caption text-[#595959]">
              Germiston → Cape Town Corridor • Spare: 590 kg
            </p>

            {/* Capacity Meter */}
            <div className="mt-3">
              <div className="flex justify-between app-caption mb-1">
                <span className="text-[#595959]">Vehicle Capacity</span>
                <span className="app-caption-strong text-[#002B49]">
                  820 / 2,000 kg (41%)
                </span>
              </div>
              <div className="w-full bg-[#E5E7EB] rounded-full h-1.5 overflow-hidden">
                <div
                  className="h-1.5 rounded-full"
                  style={{
                    width: "41%",
                    backgroundColor: "var(--momo-blue, #003E85)",
                  }}
                />
              </div>
            </div>
          </SectionCard>
        </div>

        {/* Corridor Options */}
        <div>
          <p className="app-overline mb-2">
            Corridor Safety & Routing Options
          </p>
          <div className="space-y-2">
            {[
              {
                route: "A" as const,
                name: "N1 + R21 Bypass",
                time: "2h 14m",
                score: 87,
                tag: "Recommended",
              },
              {
                route: "B" as const,
                name: "N14 Toll Route",
                time: "2h 41m",
                score: 64,
                tag: "High Risk Corridor",
              },
              {
                route: "C" as const,
                name: "N3 Direct Highway",
                time: "1h 58m",
                score: 72,
                tag: "Congestion Advisory",
              },
            ].map((r) => (
              <button
                key={r.route}
                onClick={() =>
                  navigate({ id: "routes_detail", route: r.route })
                }
                className="w-full flex items-center gap-3 bg-white rounded-xl p-3 border border-[#E5E7EB] hover:border-[#003E85] hover:shadow-xs active:bg-[#F8F9FA] transition-all text-left"
              >
                <div
                  className="w-8 h-8 rounded-lg flex items-center justify-center app-caption-strong shrink-0"
                  style={{
                    backgroundColor: "#002B49",
                    color: "#FFCC00",
                    border: "1px solid #001F35",
                  }}
                >
                  {r.route}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="app-heading">
                    {r.name}
                  </p>
                  <p className="app-caption text-[#595959]">
                    {r.time} • {r.tag}
                  </p>
                </div>
                <div className="text-right shrink-0">
                  <span
                    className="app-metric"
                    style={{
                      color:
                        r.score >= 80
                          ? "#00875A"
                          : r.score >= 70
                          ? "#003E85"
                          : "#F57C00",
                    }}
                  >
                    {r.score}
                  </span>
                  <p className="app-micro">Score</p>
                </div>
                <ChevronRightIcon size={16} className="text-[#8E8E93]" />
              </button>
            ))}
          </div>
        </div>
      </div>

      <BottomDock>
        <PrimaryBtn
          label="Assign Carrier & Deploy Route A"
          onClick={() => navigate({ id: "routes_detail", route: "A" })}
        />
      </BottomDock>
    </>
  );
}

function FactorRow({
  label,
  score,
  invert,
}: {
  label: string;
  score: number;
  invert?: boolean;
}) {
  const goodScore = invert ? 100 - score : score;
  const color =
    goodScore >= 70 ? "#00875A" : goodScore >= 45 ? "#F57C00" : "#D32F2F";

  return (
    <div
      className="py-2.5 border-b last:border-0"
      style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
    >
      <div className="flex items-center justify-between mb-1">
        <span className="app-caption text-[#595959]">{label}</span>
        <span className="app-caption-strong text-[#002B49]">
          {score}/100
        </span>
      </div>
      <div className="w-full bg-[#E5E7EB] rounded-full h-1.5 overflow-hidden">
        <div
          className="h-1.5 rounded-full"
          style={{ width: `${score}%`, backgroundColor: color }}
        />
      </div>
    </div>
  );
}

export function RouteDetailScreen({
  route,
  onBack,
}: {
  route: "A" | "B" | "C";
  onBack: () => void;
}) {
  const data = {
    A: {
      name: "N1 + R21 Bypass",
      time: "2h 14m",
      fuel: "68L",
      score: 87,
      hijack: 22,
      traffic: 65,
      coverage: 94,
      road: 88,
      desc: "Avoids high-incident freight theft hotspots near Johannesburg South via R21 bypass corridor. MTN & telematics connectivity verified above 90% across entire transit.",
    },
    B: {
      name: "N14 Toll Route",
      time: "2h 41m",
      fuel: "74L",
      score: 64,
      hijack: 58,
      traffic: 28,
      coverage: 81,
      road: 72,
      desc: "Elevated risk profile reported through Krugersdorp boundary. Lower traffic density but not recommended for high-liquidity FMCG cargo.",
    },
    C: {
      name: "N3 Direct Highway",
      time: "1h 58m",
      fuel: "71L",
      score: 72,
      hijack: 41,
      traffic: 82,
      coverage: 88,
      road: 80,
      desc: "Fastest standard artery, though traffic load significantly increases near Heidelberg toll interchange during afternoon peak hours.",
    },
  }[route];

  return (
    <>
      <TopBar title={`Route ${route} Intelligence`} onBack={onBack} />
      <div
        className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4 pb-32"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        {/* Route Overview Card */}
        <SectionCard className="p-4">
          <div className="flex items-center justify-between mb-1">
            <p className="app-heading">{data.name}</p>
            <div
              className="app-metric text-xl font-bold"
              style={{
                color:
                  data.score >= 80
                    ? "#00875A"
                    : data.score >= 70
                    ? "#003E85"
                    : "#F57C00",
              }}
            >
              {data.score}
            </div>
          </div>
          <p className="app-caption text-[#595959]">
            Est. Duration: {data.time} • Projected Fuel: {data.fuel}
          </p>
          <p className="app-body mt-3 leading-relaxed bg-[#F8F9FA] p-3 rounded-lg border border-[#E5E7EB]">
            {data.desc}
          </p>
        </SectionCard>

        {/* Factors */}
        <SectionCard className="p-4">
          <p className="app-heading mb-2">
            Safety & Logistics Factor Breakdown
          </p>
          <FactorRow
            label="Incident / Hijack Risk (lower is better)"
            score={data.hijack}
            invert
          />
          <FactorRow
            label="Traffic Congestion (lower is better)"
            score={data.traffic}
            invert
          />
          <FactorRow label="MTN Telematics Coverage" score={data.coverage} />
          <FactorRow label="Pavement Quality" score={data.road} />
          <FactorRow
            label="Fuel Efficiency Index"
            score={Math.round(100 - (parseInt(data.fuel, 10) - 60) * 3)}
          />
        </SectionCard>

        {/* Cargo Specification */}
        <SectionCard className="p-4">
          <p className="app-heading mb-2">
            Consignment Specification
          </p>
          <Row label="Cargo Classification" value="FMCG & Dry Groceries" />
          <Row label="Cold Chain Compliance" value="Not required" />
          <Row label="Insurance Underwriting" value="Standard MoMo GIT Escrow" />
          <Row label="Scheduled Dispatch" value="Today 16:00 SAST" />
        </SectionCard>
      </div>

      <BottomDock>
        <PrimaryBtn label={`Confirm & Dispatch Route ${route}`} />
        <SecondaryBtn label="Review Alternate Corridor" onClick={onBack} />
      </BottomDock>
    </>
  );
}
