package com.dizertatie.scheduler;

import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.model.TaskRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

/**
 * Base class for all schedulers.
 * Provides the CloudSim broker and a common VM-assignment entry point.
 */
public abstract class BaseScheduler {

    protected final String name;

    protected BaseScheduler(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    /**
     * Assign each cloudlet in {@code cloudlets} to a VM from {@code vms}.
     * Subclasses implement their specific placement logic here.
     *
     * @param cloudlets list of cloudlets to schedule
     * @param vms       available VMs (all running inside the broker)
     */
    public abstract void schedule(List<Cloudlet> cloudlets, List<Vm> vms);

    // ── Shared helpers ────────────────────────────────────────────────────────

    protected TaskRecord task(Cloudlet c) { return TaskMapper.getTask(c); }

    protected boolean isCritical(Cloudlet c) { return TaskMapper.isCritical(c); }

    /** Estimated execution time on a VM in seconds. */
    protected double estimatedExecTime(Cloudlet c, Vm vm) {
        if (vm.getMips() <= 0) return Double.MAX_VALUE;
        return (double) c.getLength() / (vm.getMips() * vm.getNumberOfPes());
    }

    /** Current CPU utilisation fraction of a VM (0.0 – 1.0). */
    protected double vmUtilisation(Vm vm) {
        return vm.getCpuPercentUtilization();
    }
}
