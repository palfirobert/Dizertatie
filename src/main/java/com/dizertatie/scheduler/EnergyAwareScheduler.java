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
            Vm best = selectBestVm(c, vms);
            c.setVm(best);
        }
    }

    /**
     * Pick the VM whose host is most utilised (consolidation) but still has
     * enough free PEs for the cloudlet.  Falls back to least-loaded if no
     * host has free capacity.
     */
    private Vm selectBestVm(Cloudlet c, List<Vm> vms) {
        long requiredPes = c.getNumberOfPes();

        // Prefer VMs on hosts that are already active and heavily loaded
        return vms.stream()
                .filter(vm -> vm.getHost() != null
                           && vm.getHost() != Host.NULL
                           && vm.getExpectedFreePesNumber() >= requiredPes)
                .max(Comparator.comparingDouble(vm -> hostUtilisation(vm.getHost())))
                .orElseGet(() ->
                    // Fallback: any VM with enough free PEs
                    vms.stream()
                       .filter(vm -> vm.getExpectedFreePesNumber() >= requiredPes)
                       .findFirst()
                       .orElse(vms.get(0))
                );
    }

    private double hostUtilisation(Host host) {
        return host.getCpuPercentUtilization();
    }
}
