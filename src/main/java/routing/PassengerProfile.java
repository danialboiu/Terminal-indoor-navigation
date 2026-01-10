package routing;

import model.EdgeType;

import java.util.Map;
import java.util.Set;

/**
 * Passenger profiles define routing preferences based on mobility needs.
 * Each profile specifies which edge types are allowed and cost penalties.
 */
public enum PassengerProfile {

    /**
     * Default profile - all edge types allowed, no penalties.
     */
    STANDARD(
            Set.of(EdgeType.CORRIDOR, EdgeType.STAIRS, EdgeType.ESCALATOR, EdgeType.ELEVATOR),
            Map.of()
    ),

    /**
     * Parents with stroller - avoid stairs, prefer elevators over escalators.
     * Escalators allowed but penalized (difficult with stroller).
     */
    PARENT_WITH_STROLLER(
            Set.of(EdgeType.CORRIDOR, EdgeType.ELEVATOR, EdgeType.ESCALATOR),
            Map.of(EdgeType.ESCALATOR, 3)  // 3x cost penalty for escalators
    ),

    /**
     * Elderly passenger - avoid stairs, prefer elevator over escalator.
     */
    ELDERLY(
            Set.of(EdgeType.CORRIDOR, EdgeType.ELEVATOR, EdgeType.ESCALATOR),
            Map.of(EdgeType.ESCALATOR, 2)  // 2x penalty for escalators
    ),

    /**
     * Passenger with disability (wheelchair) - only elevator for floor changes.
     */
    WHEELCHAIR(
            Set.of(EdgeType.CORRIDOR, EdgeType.ELEVATOR),
            Map.of()
    );

    private final Set<EdgeType> allowedTypes;
    private final Map<EdgeType, Integer> costMultipliers;

    PassengerProfile(Set<EdgeType> allowedTypes, Map<EdgeType, Integer> costMultipliers) {
        this.allowedTypes = allowedTypes;
        this.costMultipliers = costMultipliers;
    }

    /**
     * Returns whether this edge type is allowed for this profile.
     */
    public boolean isAllowed(EdgeType type) {
        return allowedTypes.contains(type);
    }

    /**
     * Returns the effective cost for an edge, applying any penalty multiplier.
     */
    public int getEffectiveCost(EdgeType type, int baseCost) {
        return baseCost * costMultipliers.getOrDefault(type, 1);
    }
}
