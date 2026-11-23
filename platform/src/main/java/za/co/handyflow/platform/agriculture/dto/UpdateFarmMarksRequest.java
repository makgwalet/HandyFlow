package za.co.handyflow.platform.agriculture.dto;

/**
 * The farm's registered marks and the owner details printed on sale documents. Blank clears one. Marks: at most 60 printable
 * characters; owner name 200; address 300. {@code ownerName} and {@code physicalAddress} are optional and left as they are when
 * the request does not send them at all.
 */
public record UpdateFarmMarksRequest(String brandMark, String earMark, String markRegistrationNumber, String ownerName, String physicalAddress) {
    public UpdateFarmMarksRequest(String brandMark, String earMark, String markRegistrationNumber) { this(brandMark, earMark, markRegistrationNumber, null, null); }
}
