package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.CustomFieldRules.Def;
import za.co.handyflow.platform.crm.domain.model.CustomFieldType;

class CustomFieldTypesTest {
    private static Def def(CustomFieldType t, List<String> options) { return new Def("k", "Thing", t, false, options); }
    private static String clean(Def d, String v) { return CustomFieldRules.clean(List.of(d), Map.of("k", v), Map.of()).get("k"); }
    private static void refuses(Def d, String v, String msg) {
        assertThatThrownBy(() -> clean(d, v)).isInstanceOf(IllegalArgumentException.class).hasMessage(msg);
    }
    private static final String WEB = "Thing must be a web address starting with http:// or https://";

    @Test
    void onlyListFieldsHaveOptions() {
        assertThat(CustomFieldType.CHOICE.hasOptions()).isTrue();
        assertThat(CustomFieldType.MULTI_CHOICE.hasOptions()).isTrue();
        assertThat(CustomFieldType.EMAIL.hasOptions()).isFalse();
        assertThat(CustomFieldType.TEXT.hasOptions()).isFalse();
    }

    @Test
    void checksNewFieldDefinitions() {
        assertThat(CustomFieldRules.definitionProblem("Services", CustomFieldType.MULTI_CHOICE, List.of("A"))).isNotNull();
        assertThat(CustomFieldRules.definitionProblem("Services", CustomFieldType.MULTI_CHOICE, List.of("A", "B"))).isNull();
        assertThat(CustomFieldRules.definitionProblem("Services", CustomFieldType.MULTI_CHOICE, List.of("A|x", "B"))).isEqualTo("Options cannot contain the | character");
        assertThat(CustomFieldRules.definitionProblem("Site", CustomFieldType.URL, null)).isNull();
        assertThat(CustomFieldRules.definitionProblem("Mail", CustomFieldType.EMAIL, null)).isNull();
    }

    @Test
    void multipleChoiceKeepsTheOptionsOrder() {
        var d = def(CustomFieldType.MULTI_CHOICE, List.of("Tyres", "Fuel", "Wash"));
        assertThat(clean(d, "Wash | Tyres")).isEqualTo("Tyres|Wash");
        assertThat(clean(d, "Fuel|Fuel")).isEqualTo("Fuel");
        refuses(d, "Tyres|Oil", "Thing can only include: Tyres, Fuel, Wash");
        refuses(d, "||", "Thing needs at least one choice");
    }

    @Test
    void emailIsLowercasedAndChecked() {
        var d = def(CustomFieldType.EMAIL, List.of());
        assertThat(clean(d, " Bob@Example.CO.za ")).isEqualTo("bob@example.co.za");
        refuses(d, "bob", "Thing must be an email address");
        refuses(d, "bob@x", "Thing must be an email address");
        refuses(d, "a b@x.co", "Thing must be an email address");
        refuses(d, "a@" + "x".repeat(260) + ".co", "Thing must be an email address");
    }

    @Test
    void webAddressMustBeHttp() {
        var d = def(CustomFieldType.URL, List.of());
        assertThat(clean(d, "https://acme.example/a?b=1")).isEqualTo("https://acme.example/a?b=1");
        assertThat(clean(d, "HTTP://x.co")).isEqualTo("HTTP://x.co");
        refuses(d, "acme.example", WEB);
        refuses(d, "https://", WEB);
        refuses(d, "https://a b.co", WEB);
        refuses(d, "javascript:alert(1)", WEB);
        refuses(d, "https://a.co/" + "x".repeat(500), "Thing can be at most 500 characters");
    }

    @Test
    void olderKindsStillWork() {
        assertThat(clean(def(CustomFieldType.CHOICE, List.of("A", "B")), "A")).isEqualTo("A");
        assertThat(clean(def(CustomFieldType.YES_NO, List.of()), "True")).isEqualTo("yes");
    }
}
