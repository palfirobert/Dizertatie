package com.dizertatie.scheduler;

import java.util.Comparator;
import java.util.List;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

/**
 * Stage 5 - Energy-Aware Scheduler.
 * Bin-packs cloudlets onto already-loaded VMs (consolidation) so that
 * under-utilised hosts stay idle and consume only static power.
 * Hard cap of PEs*3 per VM prevents any single VM from being overloaded.
 * CRITICAL tasks are scheduled first and prefer high-MIPS VMs.
 */
public class EnergyAwareScheduler extends BaseScheduler {

    public EnergyAwareScheduler() {
        super("EnergyAware");
    }

    @Override
    public void schedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (vms.isEmpty()) return;

        List<Cloudlet> ordered = cloudlets.stream()
                .sorted(Comparator
                        .comparingInt((Cloudlet c) -> isCritical(c) ? 0 : 1)
                        .thenComparingLong(Cloudlet::getLength))
                .toList();

        for (Cloudlet c : ordered) {
            assign(c, selectBestVm(c, vms));
        }
    }

    private Vm selectBestVm(Cloudlet c, List<Vm> vms) {
        boolean critical = isCritical(c);
        // Hard cap: PEs*3 per VM - stable limit that never drifts during scheduling
        return vms.stream()
                .filter(vm -> assignedLoad(vm) < vm.getNumberOfPes() * 3)
                .max(Comparator
                        .comparingInt(this::assignedLoad)
                        .thenComparingDouble((Vm vm) -> critical ? vm.getMips() : -vm.getMips()))
                .orElse(leastLoaded(vms));
    }
}
