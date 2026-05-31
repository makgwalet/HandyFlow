package za.co.handyflow.platform.admin.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
public record AdminDashboardResponse(
        long       totalTenants,
        long       pilotTenants,
        long       activeTenants,
        long       suspendedTenants,
        BigDecimal mrr,
        BigDecimal arrProjection,
        long       newSignupsThisWeek,
        long       churnThisMonth,
        long       conversionsThisMonth,
        long       overdueAccounts,
        long       pilotsExpiring7d,
        long       pilotsExpiring14d,
        long       pilotsExpiredNoConversion,
        List<Map<String, Object>> mrrByModule,
        List<Map<String, Object>> top10TenantsByMrr
) {}