package com.dizertatie.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.vms.Vm;

import com.dizertatie.config.SimulationConfig;
import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.model.TaskRecord;

/**
 * Hybrid ML-assisted scheduler that keeps the multi-objective heuristic core
 * and adds a task-profile model to adapt decisions per workload cluster.
 */
public class HybridMlMultiObjectiveScheduler extends BaseScheduler {

    private static final double BASE_TIME = 0.28;
    private static final double BASE_ENERGY = 0.20;
    private static final double BASE_LOAD = 0.15;
    private static final double BASE_REGION = 0.10;
    private static final double BASE_DEADLINE = 0.17;
    private static final double BASE_PRIORITY = 0.05;
    private static final double BASE_FIT = 0.05;
    private static final double ML_BLEND = 0.30;

    private final Map<Vm, String> regionMap;
    private final List<Cloudlet> replicaCloudlets = new ArrayList<>();
    private final TaskProfileClusteringModel profileModel =
            new TaskProfileClusteringModel(3, 20, SimulationConfig.RANDOM_SEED);

    private double normLoad = 1.0;
    private double maxExecTime = 3600.0;
    private int minPriority = 0;
    private int maxPriority = 1;

    public HybridMlMultiObjectiveScheduler(Map<Vm, String> regionMap) {
        super("HybridMlMultiObjective");
        this.regionMap = regionMap;
    }

    @Override
    public void schedule(List<Cloudlet> cloudlets, List<Vm> vms) {
        if (vms.isEmpty()) return;

        replicaCloudlets.clear();
        profileModel.fit(cloudlets, this::task);

        normLoad = Math.max(1.0, (double) cloudlets.size() / vms.size());
        maxExecTime = cloudlets.stream()
                .mapToDouble(c -> estimatedExecTime(c, vms.get(0)))
                .max().orElse(3600.0);

        IntSummaryStatistics prStats = cloudlets.stream()
                .map(this::task)
                .filter(Objects::nonNull)
                .mapToInt(TaskRecord::getPriority)
                .summaryStatistics();
        if (prStats.getCount() > 0) {
            minPriority = prStats.getMin();
            maxPriority = Math.max(minPriority + 1, prStats.getMax());
        }

        List<Cloudlet> critical = new ArrayList<>();
        List<Cloudlet> routine = new ArrayList<>();
        for (Cloudlet c : cloudlets) {
            if (isCritical(c)) {
                critical.add(c);
            } else {
                routine.add(c);
            }
        }
        critical.sort(Comparator.comparingLong(Cloudlet::getLength));
        routine.sort(Comparator.comparingLong(Cloudlet::getLength));

        for (Cloudlet c : critical) {
            Vm best = selectVm(c, vms, true);
            assign(c, best);
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

    private Vm selectVm(Cloudlet c, List<Vm> vms, boolean critical) {
        Vm best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vm vm : vms) {
            double score = score(c, vm, critical);
            if (score < bestScore) {
                bestScore = score;
                best = vm;
            }
        }
        return best != null ? best : leastLoaded(vms);
    }

    private Vm selectBackupVm(Cloudlet c, List<Vm> vms, Vm primary) {
        return vms.stream()
                .filter(vm -> vm != primary)
                .min(Comparator.comparingDouble(vm -> score(c, vm, true)))
                .orElse(null);
    }

    private double score(Cloudlet c, Vm vm, boolean critical) {
        double execTime = estimatedExecTime(c, vm);
        double timeCost = Math.min(1.0, execTime / maxExecTime);

        double mips = vm.getMips();
        double energyCost = mips > 0 ? Math.min(1.0, 4000.0 / mips) : 1.0;
        double loadCost = Math.min(1.0, assignedLoad(vm) / normLoad);

        TaskRecord tr = task(c);
        String preferred = (tr != null && tr.getDataRegion() != null)
                ? tr.getDataRegion() : SimulationConfig.REGION_EU_WEST;
        String vmRegion = regionMap.getOrDefault(vm, SimulationConfig.REGION_EU_WEST);
        double regionCost = preferred.equalsIgnoreCase(vmRegion) ? 0.0 : 1.0;

        double deadlineCost = deadlineRisk(c, vm, tr);
        double priorityCost = priorityUrgency(tr) * timeCost;
        double fitCost = resourceFitCost(c, vm, tr);

        double heuristicScore;
        if (critical) {
            heuristicScore = (BASE_TIME + BASE_ENERGY) * timeCost
                    + BASE_DEADLINE * deadlineCost
                    + BASE_LOAD * loadCost
                    + BASE_REGION * regionCost
                    + BASE_PRIORITY * priorityCost
                    + BASE_FIT * fitCost;
        } else {
            heuristicScore = BASE_TIME * timeCost
                    + BASE_ENERGY * energyCost
                    + BASE_LOAD * loadCost
                    + BASE_REGION * regionCost
                    + BASE_DEADLINE * deadlineCost
                    + BASE_PRIORITY * priorityCost
                    + BASE_FIT * fitCost;
        }

        TaskProfileClusteringModel.Profile profile = profileModel.predictProfile(tr);
        double mlScore = mlAdjustment(profile, timeCost, energyCost, loadCost, regionCost,
                deadlineCost, priorityCost, fitCost, critical);

        return (1.0 - ML_BLEND) * heuristicScore + ML_BLEND * mlScore;
    }

    private double mlAdjustment(TaskProfileClusteringModel.Profile profile,
                                double timeCost,
                                double energyCost,
                                double loadCost,
                                double regionCost,
                                double deadlineCost,
                                double priorityCost,
                                double fitCost,
                                boolean critical) {
        switch (profile) {
            case LATENCY_SENSITIVE:
                return 0.45 * timeCost
                        + 0.35 * deadlineCost
                        + 0.10 * priorityCost
                        + 0.10 * loadCost;
            case RESOURCE_HEAVY:
                return 0.40 * fitCost
                        + 0.25 * loadCost
                        + 0.20 * timeCost
                        + 0.15 * energyCost;
            case BALANCED:
            default:
                if (critical) {
                    return 0.35 * timeCost
                            + 0.25 * deadlineCost
                            + 0.20 * energyCost
                            + 0.10 * loadCost
                            + 0.10 * regionCost;
                }
                return 0.30 * energyCost
                        + 0.25 * timeCost
                        + 0.20 * loadCost
                        + 0.15 * regionCost
                        + 0.10 * fitCost;
        }
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
        if (tr == null || maxPriority <= minPriority) return 0.0;
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

    private Cloudlet replicate(Cloudlet c) {
        CloudletSimple r = new CloudletSimple(c.getLength(), c.getNumberOfPes());
        r.setUtilizationModelCpu(c.getUtilizationModelCpu())
                .setUtilizationModelRam(c.getUtilizationModelRam())
                .setUtilizationModelBw(c.getUtilizationModelBw())
                .setSubmissionDelay(c.getSubmissionDelay());
        TaskMapper.registerClone((int) r.getId(), (int) c.getId());
        return r;
    }
}