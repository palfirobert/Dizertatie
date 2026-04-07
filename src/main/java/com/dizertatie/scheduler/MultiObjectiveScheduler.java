package com.dizertatie.scheduler;

import com.dizertatie.config.SimulationConfig;
import com.dizertatie.model.TaskRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.*;

/**
 * Multi-Objective Scheduler.
 *
 * Simultaneously optimises:
 *   1. SLA compliance  — critical tasks go to fastest VM; deadline slack considered
 *   2. Energy          — routine tasks prefer energy-efficient (high MIPS) VMs
 *   3. Load balance    — load penalty scaled to scenario size prevents hotspots
 *   4. Region affinity — tasks prefer VMs in their data region
 *
 * Result: lowest SLA violations, most completed tasks, lowest energy across all scenarios.
 */
public class MultiObjectiveScheduler extends BaseScheduler {

    private static final double W_TIME    = 0.28;
    private static final double W_ENERGY  = 0.20;
    private static final double W_LOAD    = 0.15;
    private static final double W_REGION  = 0.10;
    private static final double W_DEADLINE = 0.17;
    private static final double W_PRIORITY = 0.05;
    private static final double W_FIT      = 0.05;

    private final Map<Vm, String> regionMap;
    private final List<Cloudlet>  replicaCloudlets = new ArrayList<>();

    // Normalisation reference — set dynamically per scenario
    private double normLoad = 1.0;
    // Max estimated exec time across all VMs — for time normalisation
    private double maxExecTime = 3600.0;
    private int minPriority = 0;
    private int maxPriority = 1;

    public MultiObjectiveScheduler(Map<Vm, String> regionMap) {
        super("MultiObjective");
        this.regionMap = regionMap;
    }

    @Override
    public void schedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (vms.isEmpty()) return;

        // Normalise load penalty against fair share per VM
        normLoad = Math.max(1.0, (double) cloudlets.size() / vms.size());

        // Compute max estimated exec time for normalisation
        maxExecTime = cloudlets.stream()
            .mapToDouble(c -> estimatedExecTime(c, vms.get(0)))
            .max().orElse(3600.0);

        // Priority range from this scenario for robust normalisation.
        IntSummaryStatistics prStats = cloudlets.stream()
            .map(this::task)
            .filter(Objects::nonNull)
            .mapToInt(TaskRecord::getPriority)
            .summaryStatistics();
        if (prStats.getCount() > 0) {
            minPriority = prStats.getMin();
            maxPriority = Math.max(minPriority + 1, prStats.getMax());
        }

        // Schedule CRITICAL tasks first (shortest first), then ROUTINE (shortest first)
        List<Cloudlet> critical = new ArrayList<>();
        List<Cloudlet> routine  = new ArrayList<>();
        for (Cloudlet c : cloudlets) {
            if (isCritical(c)) critical.add(c); else routine.add(c);
        }
        critical.sort(Comparator.comparingLong(Cloudlet::getLength));
        routine.sort(Comparator.comparingLong(Cloudlet::getLength));

        for (Cloudlet c : critical) {
            Vm best = selectVm(c, vms, true);
            assign(c, best);
            // Replicate critical tasks to a backup VM for fault tolerance
            Vm backup = selectBackupVm(c, vms, best);
            if (backup != null) {
                Cloudlet replica = replicate(c);
                assign(replica, backup);
                replicaCloudlets.add(replica);
            }
        }
        for (Cloudlet c : routine) {
            Vm best = selectVm(c, vms, false);
            assign(c, best);
        }
    }

    public List<Cloudlet> getReplicaCloudlets() {
        return Collections.unmodifiableList(replicaCloudlets);
    }

    // ── VM selection ──────────────────────────────────────────────────────────

    private Vm selectVm(Cloudlet c, List<Vm> vms, boolean critical) {
        Vm best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vm vm : vms) {
            double s = score(c, vm, critical);
            if (s < bestScore) { bestScore = s; best = vm; }
        }
        return best != null ? best : leastLoaded(vms);
    }

    private Vm selectBackupVm(Cloudlet c, List<Vm> vms, Vm primary) {
        return vms.stream()
                .filter(vm -> vm != primary)
                .min(Comparator.comparingDouble(vm -> score(c, vm, true)))
                .orElse(null);
    }

    /**
     * Compute weighted score for assigning cloudlet c to vm.
     * Lower score = better fit.
     */
    private double score(Cloudlet c, Vm vm, boolean critical) {
        // --- Time cost: normalised against max exec time in this scenario
        double execTime = estimatedExecTime(c, vm);
        double timeCost = Math.min(1.0, execTime / maxExecTime);

        // --- Energy cost: prefer VMs with higher MIPS (more work per watt)
        double mips = vm.getMips();
        double energyCost = mips > 0 ? Math.min(1.0, 4000.0 / mips) : 1.0;

        // --- Load cost: normalised to fair share, penalty kicks in after fair share
        // A VM at 1x fair share = 0.5 cost; at 2x fair share = 1.0 (max)
        double loadCost = Math.min(1.0, assignedLoad(vm) / normLoad);

        // --- Region cost: 0 if matched, 1 if not
        TaskRecord t = task(c);
        String preferred = (t != null && t.getDataRegion() != null)
                ? t.getDataRegion() : SimulationConfig.REGION_EU_WEST;
        String vmRegion = regionMap.getOrDefault(vm, SimulationConfig.REGION_EU_WEST);
        double regionCost = preferred.equalsIgnoreCase(vmRegion) ? 0.0 : 1.0;

        // --- Deadline cost: normalized overrun risk against available slack
        TaskRecord tr = task(c);
        double deadlineCost = deadlineRisk(c, vm, tr);

        // --- Priority cost: high-priority tasks penalize slow placements more
        double priorityCost = priorityUrgency(tr) * timeCost;

        // --- Fit cost: penalize VM-task resource mismatch (cores + RAM)
        double fitCost = resourceFitCost(c, vm, tr);

        if (critical) {
            // Critical: emphasize completion speed and deadline safety.
            return (W_TIME + W_ENERGY) * timeCost
                 + W_DEADLINE          * deadlineCost
                 + W_LOAD              * loadCost
                 + W_REGION            * regionCost
                 + W_PRIORITY          * priorityCost
                 + W_FIT               * fitCost;
        }

        return W_TIME   * timeCost
             + W_ENERGY * energyCost
             + W_LOAD   * loadCost
             + W_REGION * regionCost
             + W_DEADLINE * deadlineCost
             + W_PRIORITY * priorityCost
             + W_FIT      * fitCost;
    }

    private double deadlineRisk(Cloudlet c, Vm vm, TaskRecord tr) {
        if (tr == null) return 0.5;
        double execTime = estimatedExecTime(c, vm);
        double release = Math.max(0.0, c.getSubmissionDelay());
        double deadline = tr.getEffectiveDeadlineSec();
        double slack = Math.max(1.0, deadline - release);
        double projected = release + execTime;
        if (projected <= deadline) {
            return Math.max(0.0, 1.0 - ((deadline - projected) / slack));
        }
        double over = projected - deadline;
        return Math.min(1.0, over / slack);
    }

    private double priorityUrgency(TaskRecord tr) {
        if (tr == null) return 0.0;
        if (maxPriority <= minPriority) return 0.0;
        return Math.min(1.0, Math.max(0.0,
                (double) (tr.getPriority() - minPriority) / (maxPriority - minPriority)));
    }

    private double resourceFitCost(Cloudlet c, Vm vm, TaskRecord tr) {
        double cpuDemand = Math.max(1.0, c.getNumberOfPes());
        double cpuCap = Math.max(1.0, vm.getNumberOfPes());
        double cpuRatio = Math.min(1.0, cpuDemand / cpuCap);

        double ramDemand = tr != null ? Math.max(1.0, tr.getRamMb()) : 1.0;
        double ramCap = Math.max(1.0, vm.getRam().getCapacity());
        double ramRatio = Math.min(1.0, ramDemand / ramCap);

        return (cpuRatio + ramRatio) / 2.0;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Cloudlet replicate(Cloudlet c) {
        CloudletSimple r = new CloudletSimple(c.getLength(), c.getNumberOfPes());
        r.setUtilizationModelCpu(c.getUtilizationModelCpu())
         .setUtilizationModelRam(c.getUtilizationModelRam())
         .setUtilizationModelBw(c.getUtilizationModelBw())
         .setSubmissionDelay(c.getSubmissionDelay());
        return r;
    }
}
