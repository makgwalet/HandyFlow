package za.co.handyflow.platform.contracting.domain.model;

import java.util.*;

/**
 * The pure rules of a negotiation: where a proposal lands in the current wording, which proposals clash, and what the text becomes
 * when accepted proposals are applied. Proposals are located by the wording they were written against, never by position, so an
 * unrelated edit made since a proposal was submitted is kept and does not move or lose the proposal.
 */
public final class NegotiationEngine {
    private NegotiationEngine() {}

    private static String key(String block) { return block == null ? "" : block.replaceAll("\\s+", " ").trim(); }

    /** Index of the block with this wording, nearest to {@code hint} when the same wording appears more than once; -1 when it is gone. */
    static int locate(List<String> blocks, String original, Integer hint) {
        String want = key(original);
        int best = -1, bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < blocks.size(); i++) {
            if (!key(blocks.get(i)).equals(want)) continue;
            int dist = hint == null ? i : Math.abs(i - hint);
            if (dist < bestDist) { best = i; bestDist = dist; }
        }
        return best;
    }

    /** True while the wording this proposal is attached to still exists in the body. An addition at the very start has no anchor. */
    public static boolean targetExists(String currentBody, ContractNegotiationProposal p) {
        if (ContractNegotiationProposal.ADD.equals(p.getKind()) && (p.getOriginalHtml() == null || p.getOriginalHtml().isBlank())) return true;
        return locate(BodyDiff.blocks(currentBody), p.getOriginalHtml(), p.getBlockIndex()) >= 0;
    }

    /**
     * Applies the proposals, in the order given, to the current body. All or nothing: if any proposal's wording has gone, nothing is applied
     * and the exception names the proposal so staff can reject it.
     */
    public static String apply(String currentBody, List<ContractNegotiationProposal> proposals) {
        List<String> blocks = new ArrayList<>(BodyDiff.blocks(currentBody));
        for (ContractNegotiationProposal p : proposals) {
            boolean add = ContractNegotiationProposal.ADD.equals(p.getKind());
            boolean startAnchor = add && (p.getOriginalHtml() == null || p.getOriginalHtml().isBlank());
            int at = startAnchor ? -1 : locate(blocks, p.getOriginalHtml(), p.getBlockIndex());
            if (!startAnchor && at < 0)
                throw new IllegalStateException("The wording that proposal " + p.getId() + " changes is no longer in the contract. Reject it and ask the counterparty to propose again.");
            List<String> replacement = p.getProposedHtml() == null ? List.of() : BodyDiff.blocks(p.getProposedHtml());
            switch (p.getKind()) {
                case ContractNegotiationProposal.ADD -> blocks.addAll(at + 1, replacement);
                case ContractNegotiationProposal.DELETE -> blocks.remove(at);
                case ContractNegotiationProposal.REPLACE -> { blocks.remove(at); blocks.addAll(at, replacement); }
                default -> throw new IllegalStateException("Unknown proposal kind " + p.getKind());
            }
        }
        return String.join("", blocks);
    }

    /**
     * Which open proposals clash with another. Result maps the clashing proposal to the proposal it clashes with. An accepted proposal wins:
     * anything else on the same wording is held in conflict until staff reject it. Proposals that clash with no one are absent from the map.
     */
    public static Map<UUID, UUID> conflicts(List<ContractNegotiationProposal> open) {
        Map<String, List<ContractNegotiationProposal>> byTarget = new LinkedHashMap<>();
        for (ContractNegotiationProposal p : open)
            if (p.isOpen()) byTarget.computeIfAbsent(p.targetKey(), k -> new ArrayList<>()).add(p);
        Map<UUID, UUID> out = new LinkedHashMap<>();
        for (List<ContractNegotiationProposal> group : byTarget.values()) {
            if (group.size() < 2) continue;
            ContractNegotiationProposal winner = group.stream().filter(p -> ContractNegotiationProposal.ACCEPTED.equals(p.getStatus())).findFirst().orElse(null);
            for (ContractNegotiationProposal p : group) {
                if (p == winner) continue;
                ContractNegotiationProposal other = winner != null ? winner : group.stream().filter(o -> o != p).findFirst().orElseThrow();
                out.put(p.getId(), other.getId());
            }
        }
        return out;
    }
}
