package com.dizertatie.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

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

        for (Cloudlet c : runCloudlets) {
            Vm assigned = c.getVm();
            if (assigned != null && assigned != Vm.NULL) {
                broker.bindCloudletToVm(c, assigned);
            }
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

        // runCloudlets has correct VM assignments but broker finished list
        // may have VM=NULL (especially MultiObjective uses bindCloudletToVm which
        // does not backfill the VM ref on the cloudlet object after execution).
        // Fix: match by (length + submissionDelay) since IDs differ after shallowCopy.
        Map<String, Vm> vmBySignature = new HashMap<>();
        for (Cloudlet c : runCloudlets) {
            if (c.getVm() != null && c.getVm() != Vm.NULL) {
                String key = c.getLength() + "_" + c.getSubmissionDelay();
                vmBySignature.put(key, c.getVm());
            }
        }
        for (Cloudlet c : finished) {
            if (c.getVm() == null || c.getVm() == Vm.NULL) {
                String key = c.getLength() + "_" + c.getSubmissionDelay();
                Vm assignedVm = vmBySignature.get(key);
                if (assignedVm != null) c.setVm(assignedVm);
            }
        }

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
