package com.dizertatie.scheduler;

import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.model.TaskRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all schedulers.
 * Provides the CloudSim broker and a common VM-assignment entry point.
 */
public abstract class BaseScheduler {

    protected final String name;

    /** Tracks how many cloudlets have been assigned to each VM during scheduling. */
    protected final Map<Vm, Integer> loadCounter = new HashMap<>();

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
        if (vm.getMips() <= 0 || vm.getNumberOfPes() <= 0) return Double.MAX_VALUE;
        return (double) c.getLength() / (vm.getMips() * vm.getNumberOfPes());
    }

    /** Number of cloudlets assigned to this VM so far during scheduling. */
    protected int assignedLoad(Vm vm) {
        return loadCounter.getOrDefault(vm, 0);
    }

    /** Assign cloudlet to VM and increment the load counter. */
    protected void assign(Cloudlet c, Vm vm) {
        c.setVm(vm);
        loadCounter.merge(vm, 1, Integer::sum);
    }

    /** VM with fewest assigned cloudlets so far — use instead of live utilisation. */
    protected Vm leastLoaded(List<Vm> vms) {
        return vms.stream()
                .min(java.util.Comparator.comparingInt(this::assignedLoad))
                .orElse(vms.get(0));
    }

    /** @deprecated live utilisation is always 0 at schedule time — use assignedLoad() */
    @Deprecated
    protected double vmUtilisation(Vm vm) {
        return vm.getCpuPercentUtilization();
    }
}
