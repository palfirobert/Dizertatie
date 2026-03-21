package com.dizertatie.infrastructure;

import com.dizertatie.config.SimulationConfig;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds heterogeneous hosts for datacenter creation.
 * Three types: FAST, BALANCED, ECO — each with dedicated power models.
 */
public final class HostFactory {

    private HostFactory() {}

    public static Host createFast() {
        List<Pe> pes = makePes(SimulationConfig.HOST_FAST_PES, SimulationConfig.HOST_FAST_MIPS);
        HostSimple host = new HostSimple(SimulationConfig.HOST_FAST_RAM,
                                         SimulationConfig.HOST_BW,
                                         SimulationConfig.HOST_STORAGE, pes);
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.setPowerModel(PowerModelFactory.forFast());
        host.enableStateHistory();
        return host;
    }

    public static Host createBalanced() {
        List<Pe> pes = makePes(SimulationConfig.HOST_BALANCED_PES, SimulationConfig.HOST_BALANCED_MIPS);
        HostSimple host = new HostSimple(SimulationConfig.HOST_BALANCED_RAM,
                                         SimulationConfig.HOST_BW,
                                         SimulationConfig.HOST_STORAGE, pes);
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.setPowerModel(PowerModelFactory.forBalanced());
        host.enableStateHistory();
        return host;
    }

    public static Host createEco() {
        List<Pe> pes = makePes(SimulationConfig.HOST_ECO_PES, SimulationConfig.HOST_ECO_MIPS);
        HostSimple host = new HostSimple(SimulationConfig.HOST_ECO_RAM,
                                         SimulationConfig.HOST_BW,
                                         SimulationConfig.HOST_STORAGE, pes);
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.setPowerModel(PowerModelFactory.forEco());
        host.enableStateHistory();
        return host;
    }

    /** Build a mixed list: HOSTS_PER_TYPE of each type. */
    public static List<Host> createMixedHostList() {
        List<Host> hosts = new ArrayList<>();
        for (int i = 0; i < SimulationConfig.HOSTS_PER_TYPE; i++) {
            hosts.add(createFast());
            hosts.add(createBalanced());
            hosts.add(createEco());
        }
        return hosts;
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private static List<Pe> makePes(int count, long mips) {
        List<Pe> pes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) pes.add(new PeSimple(mips));
        return pes;
    }
}
