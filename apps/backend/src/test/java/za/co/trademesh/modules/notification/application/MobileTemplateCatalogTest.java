package za.co.trademesh.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class MobileTemplateCatalogTest {

    private final MobileTemplateCatalog templates =
            new MobileTemplateCatalog(new PathMatchingResourcePatternResolver());

    @Test
    void rendersVersionedSmsTextAndOrderedWhatsAppParameters() {
        var match = templates.render(NotificationTemplates.CAPACITY_MATCH_FOUND, 1, Map.of());
        assertThat(match.text()).isEqualTo("Transport matches are ready. Sign in to TradeMesh to review them.");
        assertThat(match.whatsappParameters()).isEmpty();
        assertThat(match.whatsappLanguage()).isEqualTo("en");

        var confirmation = templates.render(
                NotificationTemplates.DELIVERY_CONFIRMATION,
                NotificationTemplates.DELIVERY_CONFIRMATION_VERSION,
                Map.of("confirmationUrl", "https://app.example.test/delivery/confirm/token"));
        assertThat(confirmation.text())
                .isEqualTo(
                        "A delivery is waiting for your confirmation: https://app.example.test/delivery/confirm/token");
        assertThat(confirmation.whatsappParameters())
                .containsExactly("https://app.example.test/delivery/confirm/token");
    }

    @Test
    void rejectsUnknownMissingAndUnexpectedTemplateData() {
        assertThatThrownBy(() -> templates.render("not-a-template", 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported mobile template");
        assertThatThrownBy(() -> templates.render(NotificationTemplates.DELIVERY_CONFIRMATION, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template data");
        assertThatThrownBy(() -> templates.render(
                        NotificationTemplates.CAPACITY_MATCH_FOUND, 1, Map.of("shipmentId", "private")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("template data");
        assertThatThrownBy(() -> templates.render(
                        NotificationTemplates.DELIVERY_CONFIRMATION,
                        1,
                        Map.of("confirmationUrl", "javascript:alert(1)")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void everyBusinessEventTemplateContainsNoSensitiveVariables() {
        for (String key : new String[] {
            NotificationTemplates.CAPACITY_MATCH_FOUND,
            NotificationTemplates.HANDOVER_CONFIRMATION_ACCEPTED,
            NotificationTemplates.HANDOVER_FINALIZED_CLEAN,
            NotificationTemplates.HANDOVER_FINALIZED_DISPUTED,
            NotificationTemplates.ESCROW_RELEASED
        }) {
            var rendered = templates.render(key, 1, Map.of());
            assertThat(rendered.text())
                    .doesNotContain("amount", "phone", "QR", "nonce", "latitude", "longitude")
                    .hasSizeLessThanOrEqualTo(918);
        }
    }
}
