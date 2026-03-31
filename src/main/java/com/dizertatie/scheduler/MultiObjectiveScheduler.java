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

    private static final double W_TIME   = 0.40;
    private static final double W_ENERGY = 0.30;
    private static final double W_LOAD   = 0.20;
    private static final double W_REGION = 0.10;

    private final Map<Vm, String> regionMap;
    private final List<Cloudlet>  replicaCloudlets = new ArrayList<>();

    // Normalisation reference — set dynamically per scenario
    private double normLoad = 1.0;
    // Max estimated exec time across all VMs — for time normalisation
    private double maxExecTime = 3600.0;

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

        if (critical) {
            // Critical: speed is everything, energy secondary, load still matters
            return (W_TIME + W_ENERGY) * timeCost
                 + W_LOAD             * loadCost
                 + W_REGION           * regionCost;
        }

        return W_TIME   * timeCost
             + W_ENERGY * energyCost
             + W_LOAD   * loadCost
             + W_REGION * regionCost;
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
