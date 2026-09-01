package za.co.trademesh.modules.business.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RegistrationNumberTest {

    @Test
    void normalizesCommonRegistrationNumberFormats() {
        assertThat(RegistrationNumber.from(" 2024 / 123456 / 07 ").value()).isEqualTo("2024/123456/07");
        assertThat(RegistrationNumber.from("2024-123456-07").value()).isEqualTo("2024/123456/07");
        assertThat(RegistrationNumber.from("202412345607").value()).isEqualTo("2024/123456/07");
    }

    @Test
    void rejectsWrongLengthsAndUnexpectedCharacters() {
        assertThatThrownBy(() -> RegistrationNumber.from("2024/12345/07")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegistrationNumber.from("CIPC-2024/123456/07"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
