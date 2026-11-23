package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static za.co.handyflow.platform.agriculture.domain.rules.AgCsv.day;
import static za.co.handyflow.platform.agriculture.domain.rules.AgCsv.num;
import static za.co.handyflow.platform.agriculture.domain.rules.AgCsv.text;

/**
 * The three registers an auditor, bank, insurer, abattoir or GLOBALG.A.P. customer asks for, as CSV: the herd register,
 * the veterinary treatment register (with withdrawal end dates) and the spray register (with pre-harvest intervals).
 * The rows come from the records; this only lays them out.
 */
public final class AgRegisterRules {

    private AgRegisterRules() {}

    public record Herd(String type, String tagOrBatch, String name, String species, String breed, String sex, Integer head, LocalDate born,
                       boolean bornEstimated, String status, boolean breedingStock, String acquisitionType, LocalDate acquisitionDate,
                       BigDecimal acquisitionCost, String sire, String dam) {}

    public record Treatment(LocalDate date, String subject, String eventType, String description, String product, String dosage,
                            String administeredBy, String veterinarian, Integer withdrawalDays, String status, String notes) {}

    public record Spray(LocalDate date, String cropCycle, String inputType, String product, BigDecimal quantity, String unit, String method,
                        String appliedBy, String weather, Integer preHarvestDays, String notes) {}

    public static String herdCsv(String farmName, String brandMark, String earMark, List<Herd> rows) {
        StringBuilder sb = new StringBuilder(AgCsv.BOM);
        sb.append(AgCsv.line(List.of(text("Herd register"), text(farmName), text("Brand mark"), text(brandMark), text("Ear mark"), text(earMark))));
        sb.append(AgCsv.line(List.of(text("Type"), text("Tag or batch"), text("Name"), text("Species"), text("Breed"), text("Sex"), text("Head"),
                text("Born or origin date"), text("Date estimated"), text("Status"), text("Breeding stock"), text("Acquisition"),
                text("Acquisition date"), text("Acquisition cost (R)"), text("Sire"), text("Dam"))));
        for (Herd r : rows) {
            sb.append(AgCsv.line(List.of(text(r.type()), text(r.tagOrBatch()), text(r.name()), text(r.species()), text(r.breed()), text(r.sex()),
                    num(r.head()), day(r.born()), r.born() == null ? "" : yesNo(r.bornEstimated()), text(r.status()), yesNo(r.breedingStock()),
                    text(r.acquisitionType()), day(r.acquisitionDate()), num(r.acquisitionCost()), text(r.sire()), text(r.dam()))));
        }
        return sb.toString();
    }

    public static String treatmentCsv(String farmName, List<Treatment> rows) {
        StringBuilder sb = new StringBuilder(AgCsv.BOM);
        sb.append(AgCsv.line(List.of(text("Veterinary treatment register"), text(farmName))));
        sb.append(AgCsv.line(List.of(text("Date"), text("Animal or batch"), text("Type"), text("Description"), text("Product"), text("Dosage"),
                text("Administered by"), text("Veterinarian"), text("Withdrawal (days)"), text("Withdrawal ends"), text("Status"), text("Notes"))));
        for (Treatment r : rows) {
            sb.append(AgCsv.line(List.of(day(r.date()), text(r.subject()), text(r.eventType()), text(r.description()), text(r.product()), text(r.dosage()),
                    text(r.administeredBy()), text(r.veterinarian()), num(r.withdrawalDays()), day(AgWithdrawalRules.clearOn(r.date(), r.withdrawalDays())),
                    text(r.status()), text(r.notes()))));
        }
        return sb.toString();
    }

    public static String sprayCsv(String farmName, List<Spray> rows) {
        StringBuilder sb = new StringBuilder(AgCsv.BOM);
        sb.append(AgCsv.line(List.of(text("Spray and input register"), text(farmName))));
        sb.append(AgCsv.line(List.of(text("Date"), text("Crop cycle"), text("Type"), text("Product"), text("Quantity"), text("Unit"), text("Method"),
                text("Applied by"), text("Weather"), text("Pre-harvest interval (days)"), text("Harvest clear on"), text("Notes"))));
        for (Spray r : rows) {
            sb.append(AgCsv.line(List.of(day(r.date()), text(r.cropCycle()), text(r.inputType()), text(r.product()), num(r.quantity()), text(r.unit()),
                    text(r.method()), text(r.appliedBy()), text(r.weather()), num(r.preHarvestDays()),
                    day(AgWithdrawalRules.clearOn(r.date(), r.preHarvestDays())), text(r.notes()))));
        }
        return sb.toString();
    }

    private static String yesNo(boolean b) { return b ? "Yes" : "No"; }
}
