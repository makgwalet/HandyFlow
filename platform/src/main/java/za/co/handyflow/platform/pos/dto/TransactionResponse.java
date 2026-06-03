package za.co.handyflow.platform.pos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID       id,
        String     transactionNumber,

        // Customer
        UUID       customerId,
        String     customerName,

        // Amounts
        BigDecimal subtotal,
        BigDecimal vatAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,

        // Payment
        String     paymentMethod,
        BigDecimal amountTendered,
        BigDecimal changeGiven,
        String     paymentRef,
        List<SplitPaymentLine> splitPayments,    // populated for SPLIT transactions

        // Status
        String     status,
        String     voidedReason,

        // Refund linkage
        UUID       originalTransactionId,        // set on REFUND transactions
        String     refundReason,

        // Session
        UUID       cashSessionId,
        String     cashSessionNumber,

        // Staff
        UUID       servedBy,
        String     servedByName,

        List<TransactionItemResponse> items,

        Instant    createdAt
) {}
