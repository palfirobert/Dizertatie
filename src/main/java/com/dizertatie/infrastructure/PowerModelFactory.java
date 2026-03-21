package com.dizertatie.infrastructure;

import com.dizertatie.config.SimulationConfig;
import org.cloudbus.cloudsim.power.models.PowerModelHostSimple;

/**
 * Creates power models for each host type.
 * Uses CloudSim Plus PowerModelHostSimple (linear between idle and max watts).
 */
public final class PowerModelFactory {

    private PowerModelFactory() {}

    public static PowerModelHostSimple forFast() {
        return new PowerModelHostSimple(SimulationConfig.POWER_FAST_MAX, SimulationConfig.POWER_FAST_IDLE);
    }

    public static PowerModelHostSimple forBalanced() {
        return new PowerModelHostSimple(SimulationConfig.POWER_BAL_MAX, SimulationConfig.POWER_BAL_IDLE);
    }

    public static PowerModelHostSimple forEco() {
        return new PowerModelHostSimple(SimulationConfig.POWER_ECO_MAX, SimulationConfig.POWER_ECO_IDLE);
    }
}
