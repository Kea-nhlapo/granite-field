import {
    createDarkTheme,
    createLightTheme,
    type BrandVariants,
    type Theme,
} from "@fluentui/react-components";

/** MTN MoMo yellow, navy, and blue used across the product UI. */
export const momoYellow = "#ffcc00";
export const momoYellowHover = "#f5c200";
export const momoBlue = "#003e85";
export const momoNavy = "#002b49";
export const momoSurface = "#f8f9fa";

const momoBrand: BrandVariants = {
    10: "#FFFBE6",
    20: "#FFF6BF",
    30: "#FFEE99",
    40: "#FFE566",
    50: "#FFDC33",
    60: "#FFD41A",
    70: "#FFCC00",
    80: "#F5C200",
    90: "#D4A800",
    100: "#B38C00",
    110: "#8C6E00",
    120: "#665000",
    130: "#4D3C00",
    140: "#332800",
    150: "#1F1800",
    160: "#140F00",
};

const momoOnBrand: Partial<Theme> = {
    colorNeutralForegroundOnBrand: momoNavy,
    colorNeutralForeground1: momoNavy,
    colorBrandForeground1: momoBlue,
    colorBrandForeground2: momoNavy,
    colorBrandBackground: momoYellow,
    colorBrandBackgroundHover: momoYellowHover,
    colorBrandBackgroundPressed: "#e0b400",
    colorBrandStroke1: momoYellow,
    colorCompoundBrandBackground: momoYellow,
    colorCompoundBrandBackgroundHover: momoYellowHover,
    colorCompoundBrandStroke: momoYellow,
    colorStrokeFocus2: momoBlue,
};

export const momoLightTheme: Theme = {
    ...createLightTheme(momoBrand),
    ...momoOnBrand,
    colorNeutralBackground1: "#ffffff",
    colorNeutralBackground2: momoSurface,
    colorNeutralBackground3: "#eef1f4",
};

export const momoDarkTheme: Theme = {
    ...createDarkTheme(momoBrand),
    ...momoOnBrand,
    colorNeutralForeground1: "#f3f6f8",
    colorNeutralForegroundOnBrand: momoNavy,
    colorNeutralBackground1: momoNavy,
    colorNeutralBackground2: "#013258",
    colorNeutralBackground3: "#001b2e",
    colorBrandForeground1: momoYellow,
};
