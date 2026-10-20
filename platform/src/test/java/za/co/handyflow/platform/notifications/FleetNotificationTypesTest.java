package za.co.handyflow.platform.notifications;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FleetNotificationTypesTest {

    @Test void everyNotificationTypeTheFleetCodeUsesExists() throws IOException {
        Path root = Path.of("src/main/java/za/co/handyflow/platform/fleet");
        if (!Files.isDirectory(root)) return;
        Pattern p = Pattern.compile("NotificationType\\.([A-Z][A-Z0-9_]+)");
        Set<String> used = new TreeSet<>();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path f : s.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = p.matcher(Files.readString(f));
                while (m.find()) used.add(m.group(1));
            }
        }
        Set<String> have = new HashSet<>();
        for (NotificationType t : NotificationType.values()) have.add(t.name());
        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(have);
        assertTrue(missing.isEmpty(), "Used by the fleet code but missing from NotificationType: " + missing);
    }

    @Test void theWorkshopTyreBatteryFuelAndTelematicsAlertsWarnByAppAndEmail() {
        for (NotificationType t : List.of(NotificationType.INSPECTION_FAILED, NotificationType.WORK_ORDER_APPROVAL_REQUESTED, NotificationType.TYRE_TREAD_LOW,
                NotificationType.BATTERY_WARRANTY_EXPIRING, NotificationType.FUEL_ANOMALY_DETECTED, NotificationType.TELEMATICS_ALERT,
                NotificationType.ACCIDENT_REPORTED)) {
            assertEquals(NotificationSeverity.WARNING, t.defaultSeverity(), t.name());
            assertEquals(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL), t.defaultChannels(), t.name());
        }
    }

    @Test void aTheftReportIsCriticalAndReachesPeopleBySms() {
        assertEquals(NotificationSeverity.CRITICAL, NotificationType.VEHICLE_THEFT_REPORTED.defaultSeverity());
        assertEquals(Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS),
                NotificationType.VEHICLE_THEFT_REPORTED.defaultChannels());
    }
}
