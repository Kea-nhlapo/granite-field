package za.co.trademesh.modules.supplier.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SupplierEmailTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(SupplierEmail.from("  Supplier@Example.COM ").value()).isEqualTo("supplier@example.com");
    }

    @Test
    void rejectsValuesThatAreNotEmailAddresses() {
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierEmail.from("not-an-email"));
        assertThatIllegalArgumentException().isThrownBy(() -> SupplierEmail.from(null));
    }
}
