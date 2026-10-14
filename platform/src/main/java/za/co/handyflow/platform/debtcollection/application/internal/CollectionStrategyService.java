package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.application.internal.StrategyRules.Step;
import za.co.handyflow.platform.debtcollection.application.internal.StrategyRules.Strategy;
import za.co.handyflow.platform.debtcollection.dto.StrategyDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/** Strategy definitions: create, change, switch on and off (finance F23). */
@Service
@RequiredArgsConstructor
public class CollectionStrategyService {

    private final JdbcTemplate jdbc;

    static HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "STRATEGY_REFUSED"); }

    @Transactional(readOnly = true)
    public List<StrategyResponse> list(TenantId tenantId) {
        List<StrategyResponse> out = new ArrayList<>();
        for (StrategyResponse s : headers(tenantId, null)) out.add(withSteps(s));
        return out;
    }

    private List<StrategyResponse> headers(TenantId tenantId, UUID id) {
        String sql = "SELECT id, name, description, priority, min_balance, max_balance, min_days, max_days, quiet_days, active FROM dc_strategies WHERE tenant_id = ?"
                + (id == null ? "" : " AND id = ?") + " ORDER BY priority, name";
        org.springframework.jdbc.core.RowMapper<StrategyResponse> m = (rs, n) -> new StrategyResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getInt(4), rs.getBigDecimal(5), rs.getBigDecimal(6), (Integer) rs.getObject(7), (Integer) rs.getObject(8), rs.getInt(9), rs.getBoolean(10), List.of());
        return id == null ? jdbc.query(sql, m, tenantId.getValue()) : jdbc.query(sql, m, tenantId.getValue(), id);
    }

    private StrategyResponse withSteps(StrategyResponse s) {
        List<StepOut> steps = jdbc.query("SELECT id, after_days, action, instruction FROM dc_strategy_steps WHERE strategy_id = ? ORDER BY after_days",
                (rs, n) -> new StepOut(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3), rs.getString(4)), s.id());
        return new StrategyResponse(s.id(), s.name(), s.description(), s.priority(), s.minBalance(), s.maxBalance(), s.minDays(), s.maxDays(), s.quietDays(), s.active(), steps);
    }

    /** The active strategies as rules, for the engine. */
    @Transactional(readOnly = true)
    public List<Strategy> activeRules(TenantId tenantId) {
        List<Strategy> out = new ArrayList<>();
        for (StrategyResponse s : list(tenantId))
            if (s.active()) out.add(new Strategy(s.id(), s.name(), s.priority(), s.minBalance(), s.maxBalance(), s.minDays(), s.maxDays(), s.quietDays(),
                    s.steps().stream().map(x -> new Step(x.id(), x.afterDays(), x.action(), x.instruction())).toList()));
        return out;
    }

    private static Strategy rule(StrategyRequest r) {
        return new Strategy(null, r.name(), r.priority(), r.minBalance(), r.maxBalance(), r.minDays(), r.maxDays(), r.quietDays(),
                r.steps() == null ? null : r.steps().stream().map(x -> new Step(null, x.afterDays(), x.action(), x.instruction())).toList());
    }

    @Transactional
    public StrategyResponse create(TenantId tenantId, StrategyRequest req, UUID by) {
        String problem = StrategyRules.problem(rule(req));
        if (problem != null) throw bad(problem);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dc_strategies (id, tenant_id, name, description, priority, min_balance, max_balance, min_days, max_days, quiet_days, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, tenantId.getValue(), req.name().trim(), blank(req.description()), req.priority(), req.minBalance(), req.maxBalance(), req.minDays(), req.maxDays(), req.quietDays(), by);
        for (StepIn s : req.steps()) insertStep(tenantId, id, s);
        return one(tenantId, id);
    }

    /** Steps keep their identity by day, so what was already raised for a step is not raised again after an edit. */
    @Transactional
    public StrategyResponse update(TenantId tenantId, UUID id, StrategyRequest req) {
        one(tenantId, id);
        String problem = StrategyRules.problem(rule(req));
        if (problem != null) throw bad(problem);
        jdbc.update("""
                UPDATE dc_strategies SET name = ?, description = ?, priority = ?, min_balance = ?, max_balance = ?, min_days = ?, max_days = ?, quiet_days = ?, updated_at = now()
                 WHERE id = ? AND tenant_id = ?""",
                req.name().trim(), blank(req.description()), req.priority(), req.minBalance(), req.maxBalance(), req.minDays(), req.maxDays(), req.quietDays(), id, tenantId.getValue());
        Set<Integer> keep = new HashSet<>();
        for (StepIn s : req.steps()) {
            keep.add(s.afterDays());
            int n = jdbc.update("UPDATE dc_strategy_steps SET action = ?, instruction = ? WHERE strategy_id = ? AND after_days = ?",
                    s.action(), blank(s.instruction()), id, s.afterDays());
            if (n == 0) insertStep(tenantId, id, s);
        }
        jdbc.query("SELECT after_days FROM dc_strategy_steps WHERE strategy_id = ?", rs -> {
            int d = rs.getInt(1);
            if (!keep.contains(d)) jdbc.update("DELETE FROM dc_strategy_steps WHERE strategy_id = ? AND after_days = ?", id, d);
        }, id);
        return one(tenantId, id);
    }

    @Transactional
    public StrategyResponse setActive(TenantId tenantId, UUID id, boolean active) {
        one(tenantId, id);
        jdbc.update("UPDATE dc_strategies SET active = ?, updated_at = now() WHERE id = ? AND tenant_id = ?", active, id, tenantId.getValue());
        return one(tenantId, id);
    }

    private StrategyResponse one(TenantId tenantId, UUID id) {
        List<StrategyResponse> r = headers(tenantId, id);
        if (r.isEmpty()) throw new HandyFlowException("Strategy not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        return withSteps(r.get(0));
    }

    private void insertStep(TenantId tenantId, UUID strategyId, StepIn s) {
        jdbc.update("INSERT INTO dc_strategy_steps (id, strategy_id, tenant_id, after_days, action, instruction) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), strategyId, tenantId.getValue(), s.afterDays(), s.action(), blank(s.instruction()));
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
