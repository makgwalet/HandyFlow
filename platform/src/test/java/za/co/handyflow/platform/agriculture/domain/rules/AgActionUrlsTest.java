package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgActionUrlsTest {

    private static final UUID FARM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID X = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test void animalAndGroupOpenTheFarmWithThatRecord() {
        assertEquals("/agriculture/farms?farm=" + FARM + "&animal=" + X, AgActionUrls.animal(FARM, X));
        assertEquals("/agriculture/farms?farm=" + FARM + "&group=" + X, AgActionUrls.group(FARM, X));
    }

    @Test void aReminderForAnimalOrGroupPicksWhicheverItIsFor() {
        assertEquals(AgActionUrls.animal(FARM, X), AgActionUrls.animalOrGroup(FARM, X, null));
        assertEquals(AgActionUrls.group(FARM, X), AgActionUrls.animalOrGroup(FARM, null, X));
        assertEquals("/agriculture/farms?farm=" + FARM, AgActionUrls.animalOrGroup(FARM, null, null));
        assertEquals(AgActionUrls.animal(FARM, X), AgActionUrls.animalOrGroup(FARM, X, UUID.randomUUID()), "an animal wins");
    }

    @Test void stockAndCropsOpenTheirOwnPlace() {
        assertEquals("/agriculture/farms?farm=" + FARM + "&tab=inventory", AgActionUrls.inventory(FARM));
        assertEquals("/agriculture/crop-cycles?farm=" + FARM, AgActionUrls.cropCycles(FARM));
    }
}
