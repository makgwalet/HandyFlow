package za.co.handyflow.platform.supplychain.dto;

import java.time.LocalDate;

public record CreateSupplierRequest(
        String name,
        String registrationNumber,
        String vatNumber,
        Integer bbbeeLevel,
        String bbbeeCertificate,
        LocalDate bbbeeExpiry,
        String contactName,
        String contactEmail,
        String contactPhone,
        String website,
        String street,
        String suburb,
        String city,
        String province,
        String postalCode,
        String bankName,
        String bankAccount,
        String bankBranchCode,
        Integer paymentTermsDays,
        String currency,
        String notes
) {}