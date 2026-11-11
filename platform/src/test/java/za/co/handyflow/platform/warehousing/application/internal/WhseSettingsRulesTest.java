package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhseSettingsRules.IndustryProfile;
import za.co.handyflow.platform.warehousing.application.internal.WhseSettingsRules.OperatingModel;

import static org.junit.jupiter.api.Assertions.*;

class WhseSettingsRulesTest {

    @Test @DisplayName("model and profile names are read in any case; anything else is refused with the choices")
    void parsing() {
        assertEquals(OperatingModel.HYBRID, WhseSettingsRules.parseModel(" hybrid "));
        assertEquals(IndustryProfile.AGRI, WhseSettingsRules.parseIndustry("agri"));
        assertThrows(IllegalArgumentException.class, () -> WhseSettingsRules.parseModel("RETAIL"));
        assertThrows(IllegalArgumentException.class, () -> WhseSettingsRules.parseModel(null));
        assertThrows(IllegalArgumentException.class, () -> WhseSettingsRules.parseIndustry("MINING"));
    }

    @Test @DisplayName("pharma, food and agriculture start items with lot and expiry tracking; general starts with none")
    void presets() {
        for (IndustryProfile p : new IndustryProfile[]{IndustryProfile.PHARMA, IndustryProfile.FOOD, IndustryProfile.AGRI}) {
            assertTrue(WhseSettingsRules.itemDefaults(p).lotTracked(), p.name());
            assertTrue(WhseSettingsRules.itemDefaults(p).expiryTracked(), p.name());
        }
        assertFalse(WhseSettingsRules.itemDefaults(IndustryProfile.GENERAL).lotTracked());
        assertFalse(WhseSettingsRules.itemDefaults(IndustryProfile.GENERAL).expiryTracked());
    }

    @Test @DisplayName("an internal warehouse takes one owner; 3PL and hybrid take more")
    void newClient() {
        assertDoesNotThrow(() -> WhseSettingsRules.checkNewClient(OperatingModel.INTERNAL, 0));
        assertThrows(IllegalStateException.class, () -> WhseSettingsRules.checkNewClient(OperatingModel.INTERNAL, 1));
        assertDoesNotThrow(() -> WhseSettingsRules.checkNewClient(OperatingModel.THREE_PL, 40));
        assertDoesNotThrow(() -> WhseSettingsRules.checkNewClient(OperatingModel.HYBRID, 5));
    }

    @Test @DisplayName("moving to internal is refused while more than one account is open; other moves are free")
    void modelChange() {
        assertDoesNotThrow(() -> WhseSettingsRules.checkModelChange(OperatingModel.INTERNAL, 1));
        assertDoesNotThrow(() -> WhseSettingsRules.checkModelChange(OperatingModel.INTERNAL, 0));
        assertThrows(IllegalStateException.class, () -> WhseSettingsRules.checkModelChange(OperatingModel.INTERNAL, 2));
        assertDoesNotThrow(() -> WhseSettingsRules.checkModelChange(OperatingModel.THREE_PL, 12));
        assertDoesNotThrow(() -> WhseSettingsRules.checkModelChange(OperatingModel.HYBRID, 12));
    }
}
