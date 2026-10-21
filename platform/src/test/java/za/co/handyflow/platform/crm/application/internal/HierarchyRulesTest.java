package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HierarchyRulesTest {

    private static UUID id(int i) { return new UUID(0, i); }

    /** 1 <- 2 <- 3 <- 4 (4 is the deepest branch). */
    private static Map<UUID, UUID> chain() {
        Map<UUID, UUID> p = new HashMap<>();
        p.put(id(2), id(1)); p.put(id(3), id(2)); p.put(id(4), id(3));
        return p;
    }

    @Test
    void walksUpAndDown() {
        var p = chain();
        assertThat(HierarchyRules.ancestors(id(4), p)).containsExactly(id(3), id(2), id(1));
        assertThat(HierarchyRules.rootFirst(HierarchyRules.ancestors(id(4), p)).get(0)).isEqualTo(id(1));
        assertThat(HierarchyRules.descendants(id(1), p)).containsExactly(id(2), id(3), id(4));
        assertThat(HierarchyRules.height(id(1), p)).isEqualTo(4);
        assertThat(HierarchyRules.height(id(4), p)).isEqualTo(1);
        assertThat(HierarchyRules.height(id(9), p)).isEqualTo(1);
    }

    @Test
    void refusesSelfAndLoops() {
        var p = chain();
        HierarchyRules.checkParent(id(9), null, p);
        assertThatThrownBy(() -> HierarchyRules.checkParent(id(2), id(2), p)).isInstanceOf(IllegalArgumentException.class).hasMessage("A company cannot be its own parent");
        assertThatThrownBy(() -> HierarchyRules.checkParent(id(1), id(4), p)).hasMessage("That company is one of this company's own branches, so it cannot be its parent");
        assertThatThrownBy(() -> HierarchyRules.checkParent(id(1), id(2), p)).hasMessage("That company is one of this company's own branches, so it cannot be its parent");
    }

    @Test
    void limitsTheDepthToFiveLevels() {
        var p = chain();
        HierarchyRules.checkParent(id(9), id(3), p);
        HierarchyRules.checkParent(id(9), id(4), p);
        p.put(id(5), id(4));
        assertThatThrownBy(() -> HierarchyRules.checkParent(id(9), id(5), p)).hasMessage("A hierarchy can be at most 5 levels deep");
        Map<UUID, UUID> r = new HashMap<>();
        r.put(id(11), id(10)); r.put(id(12), id(11)); r.put(id(13), id(12)); r.put(id(4), id(3));
        assertThatThrownBy(() -> HierarchyRules.checkParent(id(3), id(13), r)).hasMessage("A hierarchy can be at most 5 levels deep");
        HierarchyRules.checkParent(id(3), id(12), r);
    }

    @Test
    void survivesDataThatAlreadyLoops() {
        Map<UUID, UUID> loop = new HashMap<>();
        loop.put(id(1), id(2)); loop.put(id(2), id(1));
        assertThat(HierarchyRules.ancestors(id(1), loop)).containsExactly(id(2));
        assertThat(HierarchyRules.descendants(id(1), loop)).isEqualTo(Set.of(id(2)));
        assertThat(HierarchyRules.height(id(1), loop)).isEqualTo(2);
        assertThat(List.of(1)).hasSize(1);
    }
}
