package za.co.handyflow.platform.complianceservices.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PortfolioDtos {

    private PortfolioDtos() {}

    public record SignalView(String code, String severity, String label, String detail, String tab) {}

    public record ClientView(UUID clientId, String clientName, String riskRating, int high, int medium, int low, List<SignalView> signals) {}

    public record Summary(LocalDate asOf, int activeClients, int flaggedClients, int withHigh, List<ClientView> clients) {}
}
