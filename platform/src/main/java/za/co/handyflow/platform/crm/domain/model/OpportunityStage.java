package za.co.handyflow.platform.crm.domain.model;

/** Where an opportunity is. The chance is a rule of thumb used only to weight the open pipeline. */
public enum OpportunityStage {
    PROSPECTING(10), QUALIFIED(25), PROPOSAL(50), NEGOTIATION(75), WON(100), LOST(0);

    private final int chance;

    OpportunityStage(int chance) { this.chance = chance; }

    /** Percent chance used to weight an open opportunity; 100 for won, 0 for lost. */
    public int chance() { return chance; }

    public boolean isOpen() { return this != WON && this != LOST; }
}
