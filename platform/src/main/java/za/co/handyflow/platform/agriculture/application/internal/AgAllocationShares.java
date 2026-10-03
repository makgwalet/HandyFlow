package za.co.handyflow.platform.agriculture.application.internal;

import za.co.handyflow.platform.agriculture.domain.rules.AgCostAllocation;
import za.co.handyflow.platform.agriculture.dto.CostEntryDtos.AllocationShare;

import java.util.ArrayList;
import java.util.List;

/** Turns a request's allocation lines into the shares the ledger splits a cost by. */
final class AgAllocationShares {

    private AgAllocationShares() {}

    static List<AgCostAllocation.Share> of(List<AllocationShare> allocations) {
        if (allocations == null || allocations.isEmpty()) throw new IllegalArgumentException("choose at least one target to allocate to");
        List<AgCostAllocation.Share> shares = new ArrayList<>();
        for (AllocationShare a : allocations) shares.add(new AgCostAllocation.Share(a.targetType(), a.targetId(), a.percentage()));
        return shares;
    }
}
