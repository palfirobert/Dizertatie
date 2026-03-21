package com.dizertatie.infrastructure;

import com.dizertatie.config.SimulationConfig;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicyBestFit;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;

import java.util.List;
import java.util.Map;

/**
 * Creates the three geo-distributed datacenters: EU_WEST, EU_CENTRAL, US_EAST.
 * Each datacenter has a heterogeneous mix of FAST, BALANCED, and ECO hosts.
 */
public final class DatacenterFactory {

    private DatacenterFactory() {}

    /**
     * Create all three regional datacenters and return them keyed by region name.
     */
    public static Map<String, Datacenter> createAll(CloudSim simulation) {
        Datacenter euWest    = createRegion(simulation, SimulationConfig.REGION_EU_WEST);
        Datacenter euCentral = createRegion(simulation, SimulationConfig.REGION_EU_CENTRAL);
        Datacenter usEast    = createRegion(simulation, SimulationConfig.REGION_US_EAST);

        return Map.of(
            SimulationConfig.REGION_EU_WEST,    euWest,
            SimulationConfig.REGION_EU_CENTRAL, euCentral,
            SimulationConfig.REGION_US_EAST,    usEast
        );
    }

    /**
     * Create a single regional datacenter with mixed hosts.
     * BestFit VM allocation: fills existing hosts before activating new ones,
     * which naturally supports energy-aware bin-packing.
     */
    public static Datacenter createRegion(CloudSim simulation, String region) {
        List<Host> hosts = HostFactory.createMixedHostList();
        DatacenterSimple dc = new DatacenterSimple(simulation, hosts, new VmAllocationPolicyBestFit());
        dc.setName(region);
        // Scheduling interval drives periodic resource usage updates
        dc.setSchedulingInterval(60); // every 60 sim-seconds
        return dc;
    }
}
