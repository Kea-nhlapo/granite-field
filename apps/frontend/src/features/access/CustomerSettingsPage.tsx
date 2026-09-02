import {
    Button,
    Card,
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    Radio,
    RadioGroup,
    Text,
    Title2,
    Title3,
} from "@fluentui/react-components";
import { useState, type FormEvent } from "react";

import { useTheme } from "../../shared/theme/ThemeProvider";
import { useAccessStyles } from "./access.styles";
import {
    clearAccountProfile,
    isLikelyEmail,
    isLikelyPhone,
    readAccountProfile,
    saveAccountDetails,
    type AccountProfile,
} from "./account-profile";
import { useSession } from "./SessionProvider";

function emptyProfile(): AccountProfile {
    return {
        firstName: "",
        lastName: "",
        businessName: "",
        registrationNumber: "",
        email: "",
        phoneNumber: "",
    };
}

export default function CustomerSettingsPage() {
    const styles = useAccessStyles();
    const { logout } = useSession();
    const { theme, setTheme } = useTheme();
    const [profile, setProfile] = useState<AccountProfile>(
        () => readAccountProfile() ?? emptyProfile(),
    );
    const [message, setMessage] = useState<string | undefined>();
    const [failed, setFailed] = useState(false);

    function onSave(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!isLikelyEmail(profile.email)) {
            setFailed(true);
            setMessage("Enter a valid email.");
            return;
        }
        if (profile.phoneNumber.trim() && !isLikelyPhone(profile.phoneNumber)) {
            setFailed(true);
            setMessage("Enter a valid phone number.");
            return;
        }
        const result = saveAccountDetails(profile);
        setFailed(!result.ok);
        setMessage(result.ok ? "Saved" : "Could not save");
    }

    return (
        <div className={styles.pageStack}>
            <Title2 as="h1">Settings</Title2>
            <Card>
                <form className={styles.stack} noValidate onSubmit={onSave}>
                    <Title3>Your details</Title3>
                    <Text>
                        {profile.firstName} {profile.lastName}
                    </Text>
                    <Text>{profile.businessName}</Text>
                    <Text>{profile.registrationNumber}</Text>
                    <Field label="Email" required>
                        <Input
                            aria-label="Email"
                            className={styles.touchTarget}
                            name="email"
                            onChange={(_, data) =>
                                setProfile({ ...profile, email: data.value })
                            }
                            type="email"
                            value={profile.email}
                        />
                    </Field>
                    <Field label="Phone number">
                        <Input
                            aria-label="Phone number"
                            className={styles.touchTarget}
                            name="phone"
                            onChange={(_, data) =>
                                setProfile({
                                    ...profile,
                                    phoneNumber: data.value,
                                })
                            }
                            value={profile.phoneNumber}
                        />
                    </Field>
                    {message ? (
                        <MessageBar
                            intent={failed ? "error" : "success"}
                            role="status"
                        >
                            <MessageBarBody>{message}</MessageBarBody>
                        </MessageBar>
                    ) : null}
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        type="submit"
                    >
                        Save
                    </Button>
                </form>
            </Card>
            <Card>
                <div className={styles.stack}>
                    <Title3>Look</Title3>
                    <RadioGroup
                        layout="horizontal"
                        onChange={(_, data) => {
                            if (
                                data.value === "light" ||
                                data.value === "dark"
                            ) {
                                setTheme(data.value);
                            }
                        }}
                        value={theme}
                    >
                        <Radio label="Light" value="light" />
                        <Radio label="Dark" value="dark" />
                    </RadioGroup>
                </div>
            </Card>
            <Button
                className={styles.touchTarget}
                onClick={() => {
                    clearAccountProfile();
                    void logout();
                }}
            >
                Sign out
            </Button>
        </div>
    );
}
