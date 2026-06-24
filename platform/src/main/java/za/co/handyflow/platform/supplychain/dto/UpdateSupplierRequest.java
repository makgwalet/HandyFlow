package za.co.handyflow.platform.supplychain.dto;

import java.time.LocalDate;

public record UpdateSupplierRequest(
        String name,
        String contactName,
        String contactEmail,
        String contactPhone,
        Integer bbbeeLevel,
        LocalDate bbbeeExpiry,
        Integer paymentTermsDays,
        String status,
        String notes
) {}
