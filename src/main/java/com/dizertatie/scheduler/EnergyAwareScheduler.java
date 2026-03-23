package com.dizertatie.scheduler;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.Comparator;
import java.util.List;

/**
 * Stage 5 — Energy-Aware Scheduler.
 *
 * <p>Strategy: for each cloudlet pick the VM whose host already has the
 * highest CPU utilisation (bin-packing).  This consolidates workloads onto
 * fewer hosts so under-utilised hosts can remain idle and consume only
 * static (idle) power instead of dynamic power.
 *
 * <p>CRITICAL tasks are prioritised by sorting them to the front before
 * assignment.
 */
public class EnergyAwareScheduler extends BaseScheduler {

    public EnergyAwareScheduler() {
        super("EnergyAware");
    }

    @Override
    public void schedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (vms.isEmpty()) return;

        // CRITICAL tasks first, then by length ascending
        List<Cloudlet> ordered = cloudlets.stream()
                .sorted(Comparator
                        .comparingInt((Cloudlet c) -> isCritical(c) ? 0 : 1)
                        .thenComparingLong(Cloudlet::getLength))
                .toList();

        for (Cloudlet c : ordered) {
            assign(c, selectBestVm(c, vms));
        }
    }

    /**
     * Pick the VM whose assigned load is highest (bin-packing consolidation).
     * Among equally loaded VMs prefer those with higher MIPS for CRITICAL tasks.
     */
    private Vm selectBestVm(Cloudlet c, List<Vm> vms) {
        boolean critical = isCritical(c);
        int avgLoad = loadCounter.values().stream().mapToInt(i -> i).sum() / Math.max(1, vms.size());
        int cap = Math.max(avgLoad * 2, 50); // don't pile more than 2x average onto one VM

        // Prefer consolidation onto already-loaded VMs, but only up to cap
        return vms.stream()
                .filter(vm -> assignedLoad(vm) <= cap)
                .max(Comparator
                        .comparingInt(this::assignedLoad)
                        .thenComparingDouble((Vm vm) -> critical ? vm.getMips() : -vm.getMips()))
                .orElse(leastLoaded(vms)); // fallback: least loaded if all over cap
    }

    private double hostUtilisation(Host host) {
        return host.getCpuPercentUtilization();
    }
}
