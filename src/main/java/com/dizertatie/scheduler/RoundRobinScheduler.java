package com.dizertatie.scheduler;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

/**
 * Stage 4 — Round Robin Scheduler.
 * Assigns cloudlets to VMs in a cyclic order, ignoring load or energy.
 */
public class RoundRobinScheduler extends BaseScheduler {

    private int index = 0;

    public RoundRobinScheduler() {
        super("RoundRobin");
    }

    @Override
    public void schedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (vms.isEmpty()) return;
        for (Cloudlet c : cloudlets) {
            Vm target = vms.get(index % vms.size());
            c.setVm(target);
            index++;
        }
    }
}
