import { useEffect, useRef, useState } from "react";
import { AnimatePresence, useReducedMotion } from "motion/react";
import { Mic } from "lucide-react";
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
  CheckmarkIcon,
  CopyIcon,
  PlusIcon,
  SearchIcon,
  ShieldCheckmarkIcon,
} from "./icons";
import { PhoneVerifyField } from "./PhoneVerifyField";
import { m, springs } from "./motion";

type SpeechRecognitionLike = {
  lang: string;
  interimResults: boolean;
  onresult: ((e: { results: { [i: number]: { [j: number]: { transcript: string } } } }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
};

export function SourceScreen({ navigate }: { navigate: Navigate }) {
  const [search, setSearch] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [listening, setListening] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const reduceMotion = useReducedMotion();

  useEffect(() => {
    return () => recognitionRef.current?.stop();
  }, []);

  function closeSearch() {
    setSearchOpen(false);
    setSearch("");
    recognitionRef.current?.stop();
    inputRef.current?.blur();
  }

  function toggleVoice() {
    if (listening) {
      recognitionRef.current?.stop();
      setListening(false);
      return;
    }
    const SpeechRecognitionCtor =
      (window as unknown as { SpeechRecognition?: new () => SpeechRecognitionLike }).SpeechRecognition ||
      (window as unknown as { webkitSpeechRecognition?: new () => SpeechRecognitionLike }).webkitSpeechRecognition;
    if (!SpeechRecognitionCtor) return;

    const recognition = new SpeechRecognitionCtor();
    recognition.lang = "en-ZA";
    recognition.interimResults = false;
    recognition.onresult = (e) => setSearch(e.results[0][0].transcript);
    recognition.onend = () => setListening(false);
    recognitionRef.current = recognition;
    recognition.start();
    setListening(true);
  }

  const products = [
    {
      name: "Sunflower Cooking Oil 5L",
      sku: "OIL-SFW-5L",
      supplier: "Thabo Distributors",
      price: "R89.00",
      stock: "High",
    },
    {
      name: "Super Maize Meal 10kg (Iwisa)",
      sku: "GRN-MZM-10",
      supplier: "Bulk SA Wholesale",
      price: "R112.50",
      stock: "Medium",
    },
    {
      name: "Washing Powder Concentrate 3kg",
      sku: "HLD-WSH-3K",
      supplier: "Nkosi Foods SA",
      price: "R67.00",
      stock: "High",
    },
    {
      name: "Tinned Pilchards in Tomato 400g",
      sku: "TIN-PLH-400",
      supplier: "Thabo Distributors",
      price: "R24.90",
      stock: "Low",
    },
    {
      name: "Long-life Full Cream Milk 1L",
      sku: "DRY-MLK-1L",
      supplier: "Bulk SA Wholesale",
      price: "R18.50",
      stock: "High",
    },
  ];

  return (
    <>
      <TopBar title="Source Inventory" />
      <div
        className="flex-1 fluent-scroll overflow-y-auto"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        <div className="p-4 space-y-4">
          {/* Search Box */}
          <div className="flex items-center gap-2">
            <div className="relative flex-1 flex items-center">
              <SearchIcon
                size={16}
                className="absolute left-3 text-[#595959] pointer-events-none"
              />
              <input
                ref={inputRef}
                value={search}
                onFocus={() => setSearchOpen(true)}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => e.key === "Escape" && closeSearch()}
                placeholder="Search verified products or suppliers..."
                className="w-full app-caption bg-white border border-[#D1D5DB] rounded-lg h-10 pl-9 pr-10 text-[#1A1A1A] placeholder-[#8E8E93] outline-none transition-all focus:border-[#003E85] focus:ring-1 focus:ring-[#003E85]"
              />
              <button
                onClick={toggleVoice}
                aria-label={listening ? "Stop voice search" : "Search by voice"}
                className="absolute right-1.5 w-7 h-7 rounded-full flex items-center justify-center transition-colors shrink-0"
                style={{
                  backgroundColor: listening ? "#FDE8E8" : "transparent",
                  color: listening ? "#D32F2F" : "#8E8E93",
                }}
              >
                {!reduceMotion && listening && (
                  <m.span
                    className="absolute inset-0 rounded-full"
                    style={{ backgroundColor: "#D32F2F" }}
                    initial={{ scale: 1, opacity: 0.4 }}
                    animate={{ scale: 1.8, opacity: 0 }}
                    transition={{ duration: 1.1, repeat: Infinity, ease: "easeOut" }}
                  />
                )}
                <Mic size={15} strokeWidth={2} />
              </button>
            </div>

            {searchOpen && (
              <button
                onClick={closeSearch}
                className="app-caption-strong text-[#003E85] shrink-0 hover:underline"
              >
                Cancel
              </button>
            )}
          </div>

          <AnimatePresence>
            {searchOpen && (
              <m.div
                initial={reduceMotion ? undefined : { height: 0, opacity: 0 }}
                animate={{ height: "auto", opacity: 1 }}
                exit={reduceMotion ? undefined : { height: 0, opacity: 0 }}
                transition={springs.snappy}
                className="overflow-hidden"
              >
                {search.trim() ? (
                  <div>
                    <p className="app-overline mb-2">Results</p>
                    <SectionCard>
                      {products
                        .filter(
                          (p) =>
                            p.name.toLowerCase().includes(search.toLowerCase()) ||
                            p.supplier.toLowerCase().includes(search.toLowerCase())
                        )
                        .map((p, i, arr) => (
                          <div
                            key={p.sku}
                            className={`flex items-center gap-3 px-4 py-3 ${i < arr.length - 1 ? "border-b" : ""}`}
                            style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
                          >
                            <div className="flex-1 min-w-0">
                              <p className="app-heading truncate">{p.name}</p>
                              <p className="app-caption text-[#595959] mt-0.5 truncate">{p.supplier}</p>
                            </div>
                            <p className="app-metric shrink-0">{p.price}</p>
                          </div>
                        ))}
                    </SectionCard>
                  </div>
                ) : (
                  <div>
                    <p className="app-overline mb-2">Most Popular</p>
                    <button
                      onClick={closeSearch}
                      className="w-full flex items-center gap-3 bg-white rounded-xl p-3.5 border border-[#E5E7EB] hover:border-[#003E85] hover:shadow-xs active:bg-[#F8F9FA] transition-all text-left"
                    >
                      <div className="w-9 h-9 rounded-lg bg-[#EBF3FC] text-[#003E85] flex items-center justify-center shrink-0 border border-[#C7E0F4]">
                        <PlusIcon size={16} />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="app-heading truncate">{products[0].name}</p>
                        <p className="app-caption text-[#595959] mt-0.5 truncate">
                          {products[0].supplier} • Ordered most this month
                        </p>
                      </div>
                      <p className="app-metric shrink-0">{products[0].price}</p>
                    </button>
                  </div>
                )}
              </m.div>
            )}
          </AnimatePresence>

          {/* Trusted Suppliers */}
          {!searchOpen && (
          <>
          <div>
            <div className="flex items-center justify-between mb-2">
              <p className="app-overline">
                Nearby Verified Wholesalers
              </p>
              <span className="app-micro text-[#8E8E93]">CIPC & MoMo Verified</span>
            </div>

            <div className="flex gap-2.5 overflow-x-auto pb-1 -mx-1 px-1 fluent-scroll">
              {[
                { name: "Thabo Distributors", dist: "3.2 km", rating: 4.8 },
                { name: "Bulk SA Wholesale", dist: "7.1 km", rating: 4.5 },
                { name: "Joburg Fresh Market", dist: "12.0 km", rating: 4.2 },
              ].map((s) => (
                <div
                  key={s.name}
                  className="shrink-0 bg-white rounded-xl p-3.5 border border-[#E5E7EB] w-40 flex flex-col justify-between shadow-xs"
                >
                  <div>
                    <div className="w-8 h-8 rounded-lg bg-[#EBF3FC] text-[#003E85] app-caption-strong flex items-center justify-center mb-2 border border-[#C7E0F4]">
                      {s.name[0]}
                    </div>
                    <p className="app-heading leading-snug truncate">
                      {s.name}
                    </p>
                    <p className="app-caption text-[#595959] mt-0.5">
                      ★ {s.rating} • {s.dist}
                    </p>
                  </div>

                  <button
                    onClick={() => navigate({ id: "source_match" })}
                    className="mt-3 w-full app-micro font-semibold h-7 rounded-lg border border-[#003E85] bg-transparent text-[#003E85] hover:bg-[#EBF3FC] active:bg-[#D9EAFB] transition-colors"
                  >
                    Direct Connect
                  </button>
                </div>
              ))}
            </div>
          </div>

          {/* Products List */}
          <div>
            <p className="app-overline mb-2">
              Available Product Catalog
            </p>
            <SectionCard>
              {products.map((p, i, arr) => (
                  <div
                    key={p.sku}
                    className={`flex items-center gap-3 px-4 py-3 ${i < arr.length - 1 ? "border-b" : ""}`}
                    style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
                  >
                    <div className="flex-1 min-w-0">
                      <p className="app-heading truncate">
                        {p.name}
                      </p>
                      <div className="flex items-center gap-1.5 mt-0.5">
                        <span className="app-micro text-[#8E8E93]">
                          {p.sku}
                        </span>
                        <span className="app-micro text-[#8E8E93]">•</span>
                        <span className="app-caption text-[#595959]">
                          {p.supplier}
                        </span>
                      </div>
                    </div>

                    <div className="text-right shrink-0">
                      <p className="app-metric">
                        {p.price}
                      </p>
                      <div className="mt-0.5">
                        <Badge
                          label={p.stock}
                          color={
                            p.stock === "High"
                              ? "success"
                              : p.stock === "Medium"
                              ? "warning"
                              : "danger"
                          }
                        />
                      </div>
                    </div>

                    <button
                      className="shrink-0 w-8 h-8 rounded-lg flex items-center justify-center text-[#002B49] bg-[#FFCC00] hover:bg-[#F5C200] active:scale-95 transition-all shadow-xs"
                      title="Add to PO"
                    >
                      <PlusIcon size={16} />
                    </button>
                  </div>
                ))}
            </SectionCard>
          </div>
          </>
          )}
        </div>
      </div>

      <BottomDock>
        <PrimaryBtn
          label="Generate Supplier Onboarding Link"
          onClick={() => navigate({ id: "source_invite" })}
        />
      </BottomDock>
    </>
  );
}

export function SupplierInviteScreen({ onBack }: { onBack: () => void }) {
  const [generated, setGenerated] = useState(false);
  const [copied, setCopied] = useState(false);
  const link = "https://trademesh.network/vendor/SB-INV-7XK9M2";

  return (
    <>
      <TopBar title="Vendor Onboarding" onBack={onBack} />
      <div
        className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4 pb-28"
        style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
      >
        <SectionCard className="p-4">
          <div className="flex items-center gap-2 mb-1">
            <ShieldCheckmarkIcon size={18} className="text-[#003E85]" />
            <p className="app-heading">
              Zero-Friction Partner Portal
            </p>
          </div>
          <p className="app-body text-[#595959] leading-relaxed">
            Invite suppliers to submit invoices and order manifests directly. No account registration is required for them—documents are parsed and validated against your MoMo purchasing tolerances instantly.
          </p>
        </SectionCard>

        <SectionCard className="p-4 space-y-3">
          <div>
            <label className="block app-caption-strong text-[#002B49] mb-1">
              Supplier Enterprise Legal Name
            </label>
            <input
              className="w-full app-caption bg-white border border-[#D1D5DB] rounded-lg h-10 px-3 text-[#1A1A1A] outline-none focus:border-[#003E85] focus:ring-1 focus:ring-[#003E85]"
              placeholder="e.g. Pretoria Bulk Distribution Pty Ltd"
            />
          </div>
          <div>
            <label className="block app-caption-strong text-[#002B49] mb-1">
              WhatsApp Contact or Dispatch Email
            </label>
            <input
              className="w-full app-caption bg-white border border-[#D1D5DB] rounded-lg h-10 px-3 text-[#1A1A1A] outline-none focus:border-[#003E85] focus:ring-1 focus:ring-[#003E85]"
              placeholder="+27 (0)82 000 0000 or orders@supplier.co.za"
            />
          </div>
          <PhoneVerifyField
            label="Supplier MoMo Number (for escrow payouts)"
            placeholder="+27 (0)82 000 0000"
          />
        </SectionCard>

        {generated && (
          <SectionCard className="p-4">
            <div className="flex items-center gap-1.5 app-caption-strong text-[#00875A] mb-2">
              <CheckmarkIcon size={16} />
              <span>Dedicated Upload Link Generated</span>
            </div>
            <div className="flex gap-2 items-center">
              <code className="flex-1 app-micro font-medium bg-[#F8F9FA] border border-[#D1D5DB] rounded-lg px-3 py-2 text-[#002B49] truncate">
                {link}
              </code>
              <button
                onClick={() => {
                  setCopied(true);
                  setTimeout(() => setCopied(false), 2000);
                }}
                className="shrink-0 flex items-center gap-1.5 px-3.5 h-10 rounded-lg app-caption-strong transition-all"
                style={{
                  backgroundColor: copied ? "var(--fluent-success, #00875A)" : "var(--momo-yellow, #FFCC00)",
                  color: copied ? "#FFFFFF" : "var(--momo-navy, #002B49)",
                }}
              >
                {copied ? (
                  <>
                    <CheckmarkIcon size={14} />
                    <span>Copied</span>
                  </>
                ) : (
                  <>
                    <CopyIcon size={14} />
                    <span>Copy</span>
                  </>
                )}
              </button>
            </div>
            <p className="app-micro mt-2">
              Single-use MoMo escrow token • Automatically expires in 72 hours
            </p>
          </SectionCard>
        )}
      </div>

      <BottomDock>
        {!generated ? (
          <PrimaryBtn
            label="Generate Secure Invite Token"
            onClick={() => setGenerated(true)}
          />
        ) : (
          <>
            <PrimaryBtn label="Send via WhatsApp Integration" />
            <SecondaryBtn
              label="Copy Manifest Link"
              onClick={() => setCopied(true)}
            />
          </>
        )}
      </BottomDock>
    </>
  );
}
