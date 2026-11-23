package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What goes on the "document of identification" for livestock that are sold or leave the farm (Stock Theft Act 57 of 1959,
 * section 6): who sells, the registered marks, who receives, where to, in what vehicle, and each animal or batch described.
 * This builds the content only; the PDF class draws it. Anything the document needs but the records do not hold is returned as a
 * warning so it is printed on the page and the seller fills it in by hand before signing.
 */
public final class AgSaleDocumentRules {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private AgSaleDocumentRules() {}

    public record Party(String name, String idNumber, String address) {}

    /** One animal, or one batch of a group. */
    public record Line(String tag, String description, int head) {}

    public record Document(String number, String title, LocalDate date, String movementType,
                           String farmName, String farmRegistration, String brandMark, String earMark, String markRegistrationNumber,
                           Party seller, Party buyer, String destination, String vehicleRegistration,
                           List<Line> lines, int totalHead, List<String> warnings) {}

    /** Only a sale or a transfer out takes livestock off the farm, so only those get a document. */
    public static void requireDocumentable(String movementType) {
        if (!"SALE".equals(movementType) && !"TRANSFER_OUT".equals(movementType))
            throw new IllegalStateException("A document of identification is only made for a sale or a transfer out");
    }

    /** S6-1A2B3C4D-20261123: the start of the movement's id and its date, so it can be found again from the record. */
    public static String number(UUID movementId, LocalDate date) {
        String id = movementId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "S6-" + id + "-" + date.toString().replace("-", "");
    }

    /** "Cattle, female, Bonsmara, born 12 Mar 2022" - whatever is known, in that order. */
    public static String animalDescription(String species, String sex, String breed, LocalDate born, boolean bornEstimated) {
        List<String> parts = new ArrayList<>();
        if (notBlank(species)) parts.add(species.trim());
        if (notBlank(sex)) parts.add(sex.trim().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (notBlank(breed)) parts.add(breed.trim());
        if (born != null) parts.add((bornEstimated ? "born about " : "born ") + DAY.format(born));
        return String.join(", ", parts);
    }

    public static String groupDescription(String species, String breed) {
        List<String> parts = new ArrayList<>();
        if (notBlank(species)) parts.add(species.trim());
        if (notBlank(breed)) parts.add(breed.trim());
        return String.join(", ", parts);
    }

    public static Document build(UUID movementId, LocalDate date, String movementType, String farmName, String farmRegistration,
                                 String brandMark, String earMark, String markRegistrationNumber, Party seller, Party buyer,
                                 String destination, String vehicleRegistration, List<Line> lines) {
        requireDocumentable(movementType);
        List<String> warnings = new ArrayList<>();
        if (!notBlank(brandMark) && !notBlank(earMark)) warnings.add("No registered brand mark or ear mark is recorded for this farm. Write it in before signing.");
        if (!notBlank(seller.name())) warnings.add("The owner's name is not recorded for this farm. Write it in before signing.");
        if (!notBlank(seller.address())) warnings.add("The farm's physical address is not recorded. Write it in before signing.");
        if (!notBlank(buyer.name())) warnings.add("The buyer's name was not recorded on the sale. Write it in before signing.");
        if (!notBlank(buyer.idNumber())) warnings.add("The buyer's ID number was not recorded on the sale. Write it in before signing.");
        if (!notBlank(destination)) warnings.add("The destination was not recorded on the sale. Write it in before signing.");
        if (!notBlank(vehicleRegistration)) warnings.add("The vehicle registration was not recorded on the sale. Write it in before signing.");
        int total = lines.stream().mapToInt(Line::head).sum();
        String title = "SALE".equals(movementType) ? "Document of identification: sale of livestock" : "Document of identification: removal of livestock";
        return new Document(number(movementId, date), title, date, movementType, farmName, farmRegistration, brandMark, earMark,
                markRegistrationNumber, seller, buyer, destination, vehicleRegistration, List.copyOf(lines), total, List.copyOf(warnings));
    }

    public static String formatDay(LocalDate d) { return d == null ? "" : DAY.format(d); }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
