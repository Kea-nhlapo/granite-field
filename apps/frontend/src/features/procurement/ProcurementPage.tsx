import { useRef, useState } from "react";

import { api } from "../../shared/api/client";
import { ApiError } from "../../shared/api/errors";
import {
    formatMoney,
    type Order,
    type Quote,
    type StockRequestItem,
} from "../../shared/api/generated";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { PrimaryBtn, SectionCard, TopBar } from "../../ui";

export function ProcurementPage({ onBack }: { onBack: () => void }) {
    const idempotencyKey = useRef("idem-stock-1");
    const [items, setItems] = useState<StockRequestItem[]>([
        { sku: "OIL-SFW-5L", quantity: "50", unit: "bottle" },
    ]);
    const [destination, setDestination] = useState("Soweto CBD");
    const [deliveryWindow, setDeliveryWindow] = useState("Tue 08:00–14:00");
    const [quote, setQuote] = useState<Quote | null>(null);
    const [order, setOrder] = useState<Order | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    function addItem() {
        setItems((current) => [
            ...current,
            { sku: "GRN-MZM-10", quantity: "30", unit: "bag" },
        ]);
    }

    function requestQuote() {
        setError(null);
        try {
            api.createStockRequest({
                items,
                destination,
                window: deliveryWindow,
            });
            const quoteId = destination === "expired" ? "expired" : "QUO-1001";
            setQuote(api.getQuote(quoteId));
        } catch (caught) {
            setError(
                caught instanceof ApiError ? caught.message : "Request failed.",
            );
        }
    }

    function confirm() {
        if (!quote || busy) return;
        setBusy(true);
        setError(null);
        try {
            const result = api.confirmOrder(quote.id, idempotencyKey.current);
            setOrder(result);
        } catch (caught) {
            setError(
                caught instanceof ApiError ? caught.message : "Confirm failed.",
            );
        } finally {
            setBusy(false);
        }
    }

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Stock request" onBack={onBack} />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4">
                {items.length === 0 ? (
                    <StatusMessage>No items yet. Add a product.</StatusMessage>
                ) : (
                    items.map((item, index) => (
                        <SectionCard
                            key={`${item.sku}-${index}`}
                            className="p-3"
                        >
                            <p className="text-xs">
                                {item.sku} · {item.quantity} {item.unit}
                            </p>
                        </SectionCard>
                    ))
                )}
                <button className="text-sm font-semibold" onClick={addItem}>
                    Add another item
                </button>
                <label className="block text-sm">
                    Destination
                    <input
                        className="mt-1 w-full h-10 border rounded-xl px-3"
                        aria-label="Destination"
                        value={destination}
                        onChange={(event) => setDestination(event.target.value)}
                    />
                </label>
                <label className="block text-sm">
                    Delivery window
                    <input
                        className="mt-1 w-full h-10 border rounded-xl px-3"
                        aria-label="Delivery window"
                        value={deliveryWindow}
                        onChange={(event) =>
                            setDeliveryWindow(event.target.value)
                        }
                    />
                </label>
                <PrimaryBtn label="Request quote" onClick={requestQuote} />
                {quote ? (
                    <SectionCard className="p-4 space-y-2">
                        <p className="text-sm font-semibold">
                            Quote {quote.id} · {quote.supplier}
                        </p>
                        <p className="text-xs">
                            Valid until {quote.validUntil}
                        </p>
                        <p className="text-sm font-mono-data">
                            Total {formatMoney(quote.total)}
                        </p>
                        {quote.lines.map((line) => (
                            <p key={line.sku} className="text-xs">
                                {line.sku}: requested {line.requested}, quoted{" "}
                                {line.quoted}
                            </p>
                        ))}
                        <p className="text-xs">
                            You are accepting this snapshot, including line
                            differences.
                        </p>
                        <PrimaryBtn
                            label={busy ? "Confirming" : "Confirm order"}
                            disabled={busy}
                            onClick={confirm}
                        />
                    </SectionCard>
                ) : null}
                {order ? (
                    <StatusMessage>
                        {`Order ${order.id} ${order.state.toLowerCase()}.`}
                    </StatusMessage>
                ) : null}
                {error ? (
                    <StatusMessage tone="error">{error}</StatusMessage>
                ) : null}
            </div>
        </div>
    );
}
