package com.dizertatie.metrics;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostStateHistoryEntry;
import org.cloudbus.cloudsim.power.models.PowerModelHostSimple;
import org.cloudbus.cloudsim.vms.Vm;

import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.fault.FailoverHandler;
import com.dizertatie.fault.FaultInjector;
import com.dizertatie.model.TaskRecord;

public class MetricsCollector {

    public SimulationResult collect(
            String schedulerName,
            String scenarioName,
            List<Cloudlet> submittedCls,
            List<Cloudlet> finishedCls,
            List<Vm> allVms,
            Collection<Datacenter> datacenters,
            FaultInjector faultInjector,
            FailoverHandler failoverHandler) {

        Map<Integer, Cloudlet> submittedByTask = submittedCls.stream()
            .collect(Collectors.toMap(this::logicalTaskId, c -> c, (left, right) -> left));

        Map<Integer, Double> earliestSuccessByTask = finishedCls.stream()
            .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS)
            .map(c -> Map.entry(logicalTaskId(c), c.getFinishTime()))
            // Keep only logical tasks that were in the submitted scenario workload.
            .filter(e -> submittedByTask.containsKey(e.getKey()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                Math::min));

        int total     = submittedByTask.size();
        int completed = earliestSuccessByTask.size();
        int failed    = Math.max(0, total - completed);

        double makespan = earliestSuccessByTask.values().stream()
            .max(Comparator.naturalOrder()).orElse(0.0);
        double throughput = makespan > 0 ? (double) completed / makespan : 0.0;

        int slaVio = 0;
        for (Map.Entry<Integer, Cloudlet> entry : submittedByTask.entrySet()) {
            TaskRecord t = TaskMapper.getTask(entry.getValue());
            if (t == null) continue;
            Double finish = earliestSuccessByTask.get(entry.getKey());
            if (finish == null || finish > t.getEffectiveDeadlineSec()) slaVio++;
        }
        double slaRate = total > 0 ? (double) slaVio / total : 0.0;

        double totalWh = 0.0;
        for (Datacenter dc : datacenters) {
            for (Host host : dc.getHostList()) {
                totalWh += accumulatedWh(host);
            }
        }
        double totalKWh      = totalWh / 1000.0;
        double energyPerTask = completed > 0 ? totalWh / completed : 0.0;

        CpuUtilisation cpu = computeCpuUtilisation(finishedCls, allVms, makespan);

        int    failedHosts  = faultInjector  != null ? faultInjector.getFailedHosts().size() : 0;
        int    recovered    = failoverHandler != null ? failoverHandler.getRecoveredCount()   : 0;
        double recoveryTime = failoverHandler != null ? failoverHandler.getRecoveryTime()      : 0.0;

        return new SimulationResult(
                schedulerName, scenarioName,
                total, completed, failed,
                makespan, throughput,
                slaVio, slaRate,
                totalKWh, energyPerTask,
                cpu.fleetPercent,
                cpu.activePercent,
            cpu.activeVmCount,
            cpu.activeVmRatio,
                failedHosts, recovered, recoveryTime);
    }

    /**
     * Average VM busy-time utilization (0-100%) across all VMs.
     *
     * For each successful logical task, we keep the earliest finishing cloudlet.
     * Then per VM we compute busy span as lastFinish - firstStart and normalize by
     * scenario makespan. Averaging across all VMs penalizes idle capacity and is
     * sensitive to how each scheduler packs work.
     */
    private CpuUtilisation computeCpuUtilisation(
            List<Cloudlet> finishedCls,
            List<Vm> allVms,
            double makespan) {

        if (allVms == null || allVms.isEmpty() || makespan <= 0.0) {
            return new CpuUtilisation(0.0, 0.0, 0, 0.0);
        }

        Map<Long, List<double[]>> vmIntervals = new HashMap<>();
        for (Cloudlet c : finishedCls) {
            Vm vm = c.getVm();
            if (vm == null || vm == Vm.NULL) continue;
            double start = Math.max(0.0, c.getExecStartTime());
            double finish = c.getFinishTime();
            if (finish <= start) continue;

            vmIntervals.computeIfAbsent(vm.getId(), id -> new java.util.ArrayList<>())
                    .add(new double[]{start, finish});
        }

        double utilSum = 0.0;
        int activeVmCount = 0;
        double activeUtilSum = 0.0;
        for (Vm vm : allVms) {
            List<double[]> intervals = vmIntervals.get(vm.getId());
            if (intervals == null || intervals.isEmpty()) continue;
            intervals.sort(Comparator.comparingDouble(interval -> interval[0]));

            double firstStart = intervals.get(0)[0];
            double lastEnd = intervals.get(0)[1];
            double mergedBusyTime = 0.0;
            double currentStart = intervals.get(0)[0];
            double currentEnd = intervals.get(0)[1];
            for (int i = 1; i < intervals.size(); i++) {
                double[] interval = intervals.get(i);
                lastEnd = Math.max(lastEnd, interval[1]);
                if (interval[0] <= currentEnd) {
                    currentEnd = Math.max(currentEnd, interval[1]);
                } else {
                    mergedBusyTime += Math.max(0.0, currentEnd - currentStart);
                    currentStart = interval[0];
                    currentEnd = interval[1];
                }
            }
            mergedBusyTime += Math.max(0.0, currentEnd - currentStart);
            utilSum += Math.min(1.0, mergedBusyTime / makespan);

            double activeWindow = Math.max(0.0, lastEnd - firstStart);
            if (activeWindow > 0.0) {
                activeVmCount++;
                activeUtilSum += Math.min(1.0, mergedBusyTime / activeWindow);
            }
        }

        double fleetPercent = (utilSum / allVms.size()) * 100.0;
        double activePercent = activeVmCount > 0 ? (activeUtilSum / activeVmCount) * 100.0 : 0.0;
        double activeVmRatio = allVms.isEmpty() ? 0.0 : (double) activeVmCount / allVms.size();
        return new CpuUtilisation(fleetPercent, activePercent, activeVmCount, activeVmRatio);
    }

    private static final class CpuUtilisation {
        private final double fleetPercent;
        private final double activePercent;
        private final int activeVmCount;
        private final double activeVmRatio;

        private CpuUtilisation(double fleetPercent, double activePercent, int activeVmCount, double activeVmRatio) {
            this.fleetPercent = fleetPercent;
            this.activePercent = activePercent;
            this.activeVmCount = activeVmCount;
            this.activeVmRatio = activeVmRatio;
        }
    }

    private int logicalTaskId(Cloudlet cloudlet) {
        TaskRecord task = TaskMapper.getTask(cloudlet);
        return task != null ? task.getId() : (int) cloudlet.getId();
    }

    private double accumulatedWh(Host host) {
        List<HostStateHistoryEntry> history = host.getStateHistory();
        if (history == null || history.isEmpty()) return 0.0;
        double totalJoules = 0.0;
        PowerModelHostSimple pm = (PowerModelHostSimple) host.getPowerModel();
        for (int i = 1; i < history.size(); i++) {
            HostStateHistoryEntry prev = history.get(i - 1);
            HostStateHistoryEntry curr = history.get(i);
            double dt = curr.time() - prev.time();
            if (dt <= 0) continue;
            double totalMips = host.getTotalMipsCapacity();
            double util = totalMips > 0
                    ? Math.min(1.0, (prev.requestedMips() + curr.requestedMips()) / 2.0 / totalMips)
                    : 0.0;
            totalJoules += pm.getPower(util) * dt;
        }
        return totalJoules / 3_600.0;
    }
}
