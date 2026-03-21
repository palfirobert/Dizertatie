package com.dizertatie.scheduler;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.Comparator;
import java.util.List;

/**
 * Stage 4 — Shortest Job First (SJF) Scheduler.
 * Sorts cloudlets by length ascending, then assigns each to the
 * VM with the lowest current CPU utilisation (best-effort load balance).
 */
public class ShortestJobFirstScheduler extends BaseScheduler {

    public ShortestJobFirstScheduler() {
        super("SJF");
    }

    @Override
    public void schedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (vms.isEmpty()) return;

        // Sort by length ascending (shortest first)
        List<Cloudlet> sorted = cloudlets.stream()
                .sorted(Comparator.comparingLong(Cloudlet::getLength))
                .toList();

        for (Cloudlet c : sorted) {
            Vm target = leastLoaded(vms);
            c.setVm(target);
        }
    }

    private Vm leastLoaded(List<Vm> vms) {
        return vms.stream()
                .min(Comparator.comparingDouble(this::vmUtilisation))
                .orElse(vms.get(0));
    }
}
