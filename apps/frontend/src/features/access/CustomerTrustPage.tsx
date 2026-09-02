import { Badge, Card, Text, Title1, Title2 } from "@fluentui/react-components";
import { useEffect, useState } from "react";

import { trustPublicSummary } from "../../shared/api/app-api";
import type { PublicSummaryResponse } from "../../shared/api/generated";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import { useAccessStyles } from "./access.styles";
import { readAccountProfile } from "./account-profile";

export default function CustomerTrustPage() {
    const styles = useAccessStyles();
    const profile = readAccountProfile();
    const businessId = profile?.businessId ?? mockBusinessId;
    const [summary, setSummary] = useState<PublicSummaryResponse | undefined>();
    const [empty, setEmpty] = useState(false);

    useEffect(() => {
        void trustPublicSummary({
            path: { businessId },
        }).then((result) => {
            if (result.error || !result.data) {
                setEmpty(true);
                return;
            }
            setSummary(result.data);
        });
    }, [businessId]);

    const rating = summary?.rating?.average;
    const score =
        typeof rating === "number" ? Math.round(rating * 20) : undefined;

    return (
        <div className={styles.pageStack}>
            <Title2 as="h1">Trust and Risk</Title2>
            <Card>
                <div className={styles.stack}>
                    <Text size={200} weight="semibold">
                        Trust score
                    </Text>
                    {empty && !summary ? (
                        <Text>
                            No trust score yet. Finish business registration
                            first.
                        </Text>
                    ) : (
                        <>
                            <Title1>{score ?? "—"}</Title1>
                            <Text>out of 100</Text>
                            {summary?.historyBand ? (
                                <Badge appearance="tint" color="success">
                                    {summary.historyBand.replaceAll("_", " ")}
                                </Badge>
                            ) : null}
                        </>
                    )}
                </div>
            </Card>
        </div>
    );
}
