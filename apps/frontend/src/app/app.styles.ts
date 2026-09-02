import { makeStyles, tokens } from "@fluentui/react-components";

export const useAppStyles = makeStyles({
    page: {
        minHeight: "100dvh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: tokens.spacingHorizontalXXL,
        backgroundColor: tokens.colorNeutralBackground3,
        boxSizing: "border-box",
    },
    shell: {
        width: "100%",
        maxWidth: "40rem",
        overflow: "hidden",
        borderRadius: tokens.borderRadiusXLarge,
        boxShadow: tokens.shadow8,
        backgroundColor: tokens.colorNeutralBackground1,
    },
    brandBar: {
        minHeight: "3.5rem",
        backgroundColor: tokens.colorBrandBackground,
        color: tokens.colorNeutralForegroundOnBrand,
        padding: `${tokens.spacingVerticalL} ${tokens.spacingHorizontalXL}`,
        boxSizing: "border-box",
    },
    brandBarLabel: {
        color: tokens.colorNeutralForegroundOnBrand,
    },
    card: {
        padding: tokens.spacingHorizontalXL,
        paddingBottom: tokens.spacingVerticalXXL,
    },
    stack: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalM,
    },
    title: {
        marginTop: 0,
        marginBottom: 0,
    },
});
