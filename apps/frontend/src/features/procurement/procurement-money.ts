import type { MoneySummary } from "../../shared/api/generated";

export function moneyText(
    currency: string | undefined,
    amount: number | undefined,
) {
    if (!currency || amount === undefined) {
        return "—";
    }
    return `${currency} ${String(amount)}`;
}

export function moneySummaryLines(money: MoneySummary | undefined) {
    return {
        currency: money?.currency,
        subtotal: moneyText(money?.currency, money?.subtotal),
        tax: moneyText(money?.currency, money?.taxAmount),
        total: moneyText(money?.currency, money?.total),
    };
}
