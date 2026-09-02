import { Avatar, Card, Text, Title2, Title3 } from "@fluentui/react-components";
import { Link } from "react-router-dom";

import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import { useAccessStyles } from "./access.styles";
import { initialsFor, readAccountProfile } from "./account-profile";

export default function CustomerHomePage() {
    const styles = useAccessStyles();
    const profile = readAccountProfile();
    const businessId = profile?.businessId ?? mockBusinessId;
    const name = profile?.businessName.trim() || "Your business";

    return (
        <div className={styles.pageStack}>
            <div className={styles.pageHeader}>
                <Title2 as="h1">Home</Title2>
                <Link aria-label="Your account" to="/app/settings">
                    <Avatar
                        aria-hidden
                        initials={initialsFor(profile)}
                        name={
                            `${profile?.firstName ?? ""} ${profile?.lastName ?? ""}`.trim() ||
                            "Account"
                        }
                    />
                </Link>
            </div>
            <Card>
                <Text size={200} weight="semibold">
                    Your business
                </Text>
                <Title3>{name}</Title3>
                {profile?.registrationNumber ? (
                    <Text size={200}>
                        Registration {profile.registrationNumber}
                    </Text>
                ) : (
                    <Link className={styles.tileLink} to="/app/onboarding">
                        Finish business registration
                    </Link>
                )}
            </Card>
            <div className={styles.pageStack}>
                <Text size={200} weight="semibold">
                    What do you want to do?
                </Text>
                <div className={styles.tileGrid}>
                    <Link
                        aria-label="Source stock"
                        className={styles.tileLink}
                        to={`/app/procurement/${businessId}`}
                    >
                        <Card className={styles.tile}>
                            <Title3>Source stock</Title3>
                            <Text size={200}>Ask suppliers for prices</Text>
                        </Card>
                    </Link>
                    <Link
                        aria-label="Upload invoice"
                        className={styles.tileLink}
                        to={`/app/documents/${businessId}`}
                    >
                        <Card className={styles.tile}>
                            <Title3>Upload invoice</Title3>
                            <Text size={200}>Check a supplier bill</Text>
                        </Card>
                    </Link>
                    <Link
                        aria-label="Trust and Risk"
                        className={styles.tileLink}
                        to="/app/trust"
                    >
                        <Card className={styles.tile}>
                            <Title3>Trust and Risk</Title3>
                            <Text size={200}>See how your business looks</Text>
                        </Card>
                    </Link>
                </div>
            </div>
        </div>
    );
}
