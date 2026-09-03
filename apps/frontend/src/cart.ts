import { useEffect, useState } from "react";

const CART_KEY = "trademesh_cart_v1";
const CART_EVENT = "trademesh_cart_updated";

type Cart = Record<string, number>;

function readCart(): Cart {
    try {
        const raw = localStorage.getItem(CART_KEY);
        return raw ? (JSON.parse(raw) as Cart) : {};
    } catch {
        return {};
    }
}

function writeCart(cart: Cart) {
    try {
        localStorage.setItem(CART_KEY, JSON.stringify(cart));
    } catch {
        // localStorage unavailable (private mode, etc.) — cart just won't persist
    }
    window.dispatchEvent(new CustomEvent(CART_EVENT, { detail: cart }));
}

/** Cart quantities persisted to localStorage so they survive reloads and are shared across screens. */
export function useCart() {
    const [cart, setCart] = useState<Cart>(() => readCart());

    useEffect(() => {
        function onUpdate(e: Event) {
            setCart((e as CustomEvent<Cart>).detail);
        }
        window.addEventListener(CART_EVENT, onUpdate);
        return () => window.removeEventListener(CART_EVENT, onUpdate);
    }, []);

    function addItem(sku: string) {
        const next = { ...readCart(), [sku]: (readCart()[sku] ?? 0) + 1 };
        writeCart(next);
    }

    const totalItems = Object.values(cart).reduce((sum, qty) => sum + qty, 0);

    return { cart, addItem, totalItems };
}
