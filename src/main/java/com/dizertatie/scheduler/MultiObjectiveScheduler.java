package com.dizertatie.scheduler;

import com.dizertatie.config.SimulationConfig;
import com.dizertatie.model.TaskRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

/**
 * Stage 6 — Custom Multi-Objective Scheduler (core dissertation contribution).
 *
 * <p>Placement score per VM:
 * <pre>
 *   Score = w1 * EnergyCost
 *         + w2 * SlaPenalty
 *         + w3 * NetworkCost
 *         + w4 * IdlePenalty
 *         + w5 * RedundancyPenalty
 * </pre>
 * The VM with the <em>lowest</em> score is selected.
 *
 * <p>CRITICAL tasks are always scheduled first and optionally replicated
 * to a secondary VM in a different region.
 */
public class MultiObjectiveScheduler extends BaseScheduler {

    /** Tracks which region each VM belongs to (set by the simulation before scheduling). */
    private final Map<Vm, String> vmRegionMap;

    /** Replica assignments for CRITICAL tasks: original cloudlet → replica cloudlet. */
    private final List<Cloudlet> replicaCloudlets = new ArrayList<>();

    public MultiObjectiveScheduler(Map<Vm, String> vmRegionMap) {
        super("MultiObjective");
        this.vmRegionMap = vmRegionMap;
    }

    public List<Cloudlet> getReplicaCloudlets() {
        return Collections.unmodifiableList(replicaCloudlets);
    }

    // ── Main scheduling entry point ────────────────────────────────────────────

    @Override
    public void schedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (vms.isEmpty()) return;

        // CRITICAL first, then by priority desc, then by length asc
        List<Cloudlet> ordered = cloudlets.stream()
                .sorted(Comparator
                        .comparingInt((Cloudlet c) -> isCritical(c) ? 0 : 1)
                        .thenComparingInt((Cloudlet c) -> {
                            TaskRecord t = task(c);
                            return t != null ? -t.getPriority() : 0;
                        })
                        .thenComparingLong(Cloudlet::getLength))
                .toList();

        for (Cloudlet c : ordered) {
            Vm best = selectVm(c, vms);
            assign(c, best);

            // Replicate CRITICAL tasks to a different-region VM
            if (isCritical(c)) {
                createReplica(c, vms, best);
            }
        }
    }

    // ── VM selection ───────────────────────────────────────────────────────────

    private Vm selectVm(Cloudlet c, List<Vm> vms) {
        Vm best = null;
        double bestScore = Double.MAX_VALUE;

        for (Vm vm : vms) {
            double score = score(c, vm);
            if (score < bestScore) {
                bestScore = score;
                best = vm;
            }
        }
        return best != null ? best : leastLoaded(vms);
    }

    // ── Scoring ────────────────────────────────────────────────────────────────

    double score(Cloudlet c, Vm vm) {
        return SimulationConfig.W1_ENERGY    * energyCost(c, vm)
             + SimulationConfig.W2_SLA       * slaPenalty(c, vm)
             + SimulationConfig.W3_NETWORK   * networkCost(c, vm)
             + SimulationConfig.W4_IDLE      * idlePenalty(c, vm)
             + SimulationConfig.W5_REDUNDANCY* redundancyPenalty(c, vm);
    }

    /**
     * EnergyCost — estimated energy (Wh) to run this cloudlet on this VM,
     * normalised to [0,1]. Uses static power model params and estimated exec time
     * so it works correctly at scheduling time (before simulation starts).
     */
    private double energyCost(Cloudlet c, Vm vm) {
        Host host = vm.getHost();
        PowerModelData pm = (host != null && host != Host.NULL)
                ? powerParams(host) : new PowerModelData(SimulationConfig.POWER_FAST_IDLE, SimulationConfig.POWER_FAST_MAX);
        double execSec = estimatedExecTime(c, vm);
        // Use average of idle+max as a proxy for execution power
        double avgWatts = (pm.idle + pm.max) / 2.0;
        double wh = avgWatts * execSec / 3600.0;
        // Normalise: worst case = max power * 1 hour
        return Math.min(1.0, wh / (SimulationConfig.POWER_FAST_MAX * 1.0));
    }

    /**
     * SlaPenalty — estimated finish time vs effective deadline (arrival + rel slack).
     * Partial penalty proportional to overrun fraction.
     */
    private double slaPenalty(Cloudlet c, Vm vm) {
        TaskRecord t = task(c);
        if (t == null) return 0.0;
        double eta      = c.getSubmissionDelay() + estimatedExecTime(c, vm);
        double deadline = t.getEffectiveDeadlineSec();   // ← fixed: arrival + rel slack
        if (deadline <= 0) return 0.0;
        if (eta <= deadline) return 0.0;
        double overrun  = (eta - deadline) / deadline;
        double base     = t.isCritical()
                          ? SimulationConfig.SLA_PENALTY_HIGH
                          : SimulationConfig.SLA_PENALTY_LOW;
        return Math.min(1.0, base * overrun);
    }

    /**
     * NetworkCost — based on data_region of the task vs. region of the VM.
     */
    private double networkCost(Cloudlet c, Vm vm) {
        TaskRecord t = task(c);
        if (t == null) return 0.5;
        String vmRegion = vmRegionMap.getOrDefault(vm, "UNKNOWN");
        return SimulationConfig.networkCost(t.getDataRegion(), vmRegion);
    }

    /**
     * IdlePenalty — penalise slow ECO VMs for CRITICAL tasks,
     * and penalise FAST VMs for tiny ROUTINE tasks (wasteful).
     * Uses static MIPS instead of live utilisation (which is 0 at schedule time).
     */
    private double idlePenalty(Cloudlet c, Vm vm) {
        long mips = (long) vm.getMips();
        TaskRecord t = task(c);
        boolean critical = t != null && t.isCritical();
        // CRITICAL task on ECO VM → high penalty
        if (critical && mips <= SimulationConfig.VM_ECO_MIPS) return 0.9;
        // Small ROUTINE task on FAST VM → mild waste penalty
        if (!critical && mips >= SimulationConfig.VM_FAST_MIPS && c.getLength() < 10_000) return 0.3;
        return 0.0;
    }

    /**
     * RedundancyPenalty — for CRITICAL tasks, penalise placing on a VM that
     * already hosts another replica of the same task (same host = unsafe).
     */
    private double redundancyPenalty(Cloudlet c, Vm vm) {
        if (!isCritical(c)) return 0.0;
        // Penalise if another VM on the same host is already running a replica
        // with the same cloudlet ID.
        Host host = vm.getHost();
        if (host == null || host == Host.NULL) return 0.0;
        return host.getVmList().stream()
                .anyMatch(other -> other != vm
                        && other.getCloudletScheduler().getCloudletList().stream()
                                 .anyMatch(cl -> cl.getId() == c.getId()))
               ? 1.0 : 0.0;
    }

    // ── Replication ────────────────────────────────────────────────────────────

    private void createReplica(Cloudlet original, List<Vm> vms, Vm primaryVm) {
        String primaryRegion = vmRegionMap.getOrDefault(primaryVm, "");
        vms.stream()
           .filter(vm -> !vmRegionMap.getOrDefault(vm, "").equals(primaryRegion))
           .filter(vm -> canFit(original, vm))
           .min(Comparator.comparingDouble(vm -> score(original, vm)))
           .ifPresent(vm -> {
               Cloudlet replica = cloneCloudlet(original);
               if (replica != null) {
                   replica.setVm(vm);
                   replicaCloudlets.add(replica);
               }
           });
    }

    private Cloudlet cloneCloudlet(Cloudlet src) {
        try {
            CloudletSimple replica = new CloudletSimple(src.getLength(), src.getNumberOfPes());
            replica.setUtilizationModelCpu(src.getUtilizationModelCpu())
                   .setUtilizationModelRam(src.getUtilizationModelRam())
                   .setUtilizationModelBw(src.getUtilizationModelBw())
                   .setSubmissionDelay(src.getSubmissionDelay());
            // Register replica in TaskMapper so metrics can resolve it
            com.dizertatie.dataset.TaskMapper.registerClone((int) replica.getId(), (int) src.getId());
            return replica;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean canFit(Cloudlet c, Vm vm) {
        return vm.getExpectedFreePesNumber() >= c.getNumberOfPes();
    }

    private static final class PowerModelData {
        final double idle, max;
        PowerModelData(double idle, double max) { this.idle = idle; this.max = max; }
    }

    private PowerModelData powerParams(Host host) {
        long mips = host.getTotalMipsCapacity() > 0
                    ? (long)(host.getTotalMipsCapacity() / host.getNumberOfPes())
                    : 0;
        if      (mips >= SimulationConfig.HOST_FAST_MIPS)
            return new PowerModelData(SimulationConfig.POWER_FAST_IDLE, SimulationConfig.POWER_FAST_MAX);
        else if (mips >= SimulationConfig.HOST_BALANCED_MIPS)
            return new PowerModelData(SimulationConfig.POWER_BAL_IDLE,  SimulationConfig.POWER_BAL_MAX);
        else
            return new PowerModelData(SimulationConfig.POWER_ECO_IDLE,  SimulationConfig.POWER_ECO_MAX);
    }
}
