package com.dizertatie.simulation;

import com.dizertatie.config.SimulationConfig;
import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.fault.FailoverHandler;
import com.dizertatie.fault.FaultInjector;
import com.dizertatie.infrastructure.DatacenterFactory;
import com.dizertatie.infrastructure.VmFactory;
import com.dizertatie.metrics.MetricsCollector;
import com.dizertatie.metrics.SimulationResult;
import com.dizertatie.scheduler.BaseScheduler;
import com.dizertatie.scheduler.MultiObjectiveScheduler;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core simulation engine.
 * One instance = one run (scheduler × scenario).
 *
 * Lifecycle:
 *  1. Create a fresh CloudSim instance
 *  2. Build datacenters + hosts
 *  3. Create broker + VMs
 *  4. Apply scenario filter to cloudlets
 *  5. Run scheduler to bind cloudlets → VMs
 *  6. Optionally inject faults
 *  7. Start simulation
 *  8. Collect metrics
 */
public class SimulationEngine {

    private final String        scenarioName;
    private final BaseScheduler scheduler;
    private final List<Cloudlet> cloudlets;
    private final boolean       injectFaults;
    private final double        faultPercentage;

    private FaultInjector   faultInjector;
    private FailoverHandler failoverHandler;

    public SimulationEngine(String scenarioName, BaseScheduler scheduler,
                            List<Cloudlet> cloudlets,
                            boolean injectFaults, double faultPercentage) {
        this.scenarioName    = scenarioName;
        this.scheduler       = scheduler;
        this.cloudlets       = cloudlets;
        this.injectFaults    = injectFaults;
        this.faultPercentage = faultPercentage;
    }

    public SimulationEngine(String scenarioName, BaseScheduler scheduler,
                            List<Cloudlet> cloudlets) {
        this(scenarioName, scheduler, cloudlets, false, 0.0);
    }

    public SimulationResult run() {
        System.out.printf("%n════════════════════════════════════════════%n");
        System.out.printf(" Scenario: %-18s Scheduler: %s%n", scenarioName, scheduler.getName());
        System.out.printf("════════════════════════════════════════════%n");

        CloudSim sim = new CloudSim();

        Map<String, Datacenter> dcMap = DatacenterFactory.createAll(sim);
        List<Datacenter> dcList = new ArrayList<>(dcMap.values());

        DatacenterBrokerSimple broker = new DatacenterBrokerSimple(sim);
        List<Vm> vms = buildVms(dcMap);
        broker.submitVmList(vms);

        List<Cloudlet> runCloudlets = shallowCopy(cloudlets);

        if (scheduler instanceof MultiObjectiveScheduler) {
            Map<Vm, String> regionMap = buildRegionMap(vms);
            MultiObjectiveScheduler freshMos = new MultiObjectiveScheduler(regionMap);
            freshMos.schedule(runCloudlets, vms);
            runCloudlets.addAll(freshMos.getReplicaCloudlets());
        } else {
            scheduler.schedule(runCloudlets, vms);
        }

        broker.submitCloudletList(runCloudlets);

        List<Host> allHosts = dcList.stream()
                .flatMap(dc -> dc.getHostList().stream())
                .collect(Collectors.toList());

        if (injectFaults) {
            faultInjector   = new FaultInjector();
            failoverHandler = new FailoverHandler();
            List<Host> failed = faultInjector.injectFaultsControlled(allHosts, faultPercentage);
            if (!failed.isEmpty()) {
                failoverHandler.handleFailover(runCloudlets, vms, 0.0);
            }
        }

        sim.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        System.out.printf("[Engine] Finished cloudlets: %d / %d%n",
                finished.size(), runCloudlets.size());

        SimulationResult result = new MetricsCollector().collect(
                scheduler.getName(), scenarioName,
                finished, vms, dcList,
                faultInjector, failoverHandler);

        System.out.println("[Engine] " + result);
        return result;
    }

    private List<Vm> buildVms(Map<String, Datacenter> dcMap) {
        List<Vm> allVms = new ArrayList<>();
        for (String region : dcMap.keySet()) {
            List<Vm> regionVms = VmFactory.createMixedVmList();
            regionVms.forEach(vm -> vm.setDescription(region));
            allVms.addAll(regionVms);
        }
        return allVms;
    }

    private Map<Vm, String> buildRegionMap(List<Vm> vms) {
        Map<Vm, String> map = new HashMap<>();
        for (Vm vm : vms) {
            String region = vm.getDescription();
            if (region == null || region.isBlank()) region = SimulationConfig.REGION_EU_WEST;
            map.put(vm, region);
        }
        return map;
    }

    private List<Cloudlet> shallowCopy(List<Cloudlet> source) {
        List<Cloudlet> copy = new ArrayList<>(source.size());
        for (Cloudlet c : source) {
            CloudletSimple nc = new CloudletSimple(c.getLength(), c.getNumberOfPes());
            nc.setUtilizationModelCpu(c.getUtilizationModelCpu())
              .setUtilizationModelRam(c.getUtilizationModelRam())
              .setUtilizationModelBw(c.getUtilizationModelBw())
              .setSubmissionDelay(c.getSubmissionDelay());
            TaskMapper.registerClone((int) nc.getId(), (int) c.getId());
            copy.add(nc);
        }
        return copy;
    }
}
