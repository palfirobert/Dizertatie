package com.dizertatie.metrics;

import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.fault.FailoverHandler;
import com.dizertatie.fault.FaultInjector;
import com.dizertatie.model.TaskRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostStateHistoryEntry;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsCollector {

    public SimulationResult collect(
            String schedulerName,
            String scenarioName,
            List<Cloudlet> finishedCls,
            List<Vm> allVms,
            Collection<Datacenter> datacenters,
            FaultInjector faultInjector,
            FailoverHandler failoverHandler) {

        int total     = finishedCls.size();
        int completed = (int) finishedCls.stream()
                .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS).count();
        int failed    = total - completed;

        double makespan = finishedCls.stream()
                .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS)
                .mapToDouble(Cloudlet::getFinishTime)
                .max().orElse(0.0);
        double throughput = makespan > 0 ? (double) completed / makespan : 0.0;

        int slaVio = 0;
        for (Cloudlet c : finishedCls) {
            if (c.getStatus() != Cloudlet.Status.SUCCESS) continue;
            TaskRecord t = TaskMapper.getTask(c);
            if (t == null) continue;
            if (c.getFinishTime() > t.getEffectiveDeadlineSec()) slaVio++;
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

        double avgCpu = computeAvgCpuUtilisation(finishedCls, allVms, makespan);

        int    failedHosts  = faultInjector  != null ? faultInjector.getFailedHosts().size() : 0;
        int    recovered    = failoverHandler != null ? failoverHandler.getRecoveredCount()   : 0;
        double recoveryTime = failoverHandler != null ? failoverHandler.getRecoveryTime()      : 0.0;

        return new SimulationResult(
                schedulerName, scenarioName,
                total, completed, failed,
                makespan, throughput,
                slaVio, slaRate,
                totalKWh, energyPerTask,
                avgCpu,
                failedHosts, recovered, recoveryTime);
    }

    /**
     * Average CPU utilisation per VM (0-100%).
     *
     * For each VM we compute the wall-clock busy span:
     *   busySpan = lastCloudletFinish - firstCloudletStart
     * util = min(1.0, busySpan / makespan)
     *
     * Averaged across ALL VMs so idle VMs pull the average down.
     * This reflects how differently each scheduler packs work onto VMs.
     */
    private double computeAvgCpuUtilisation(List<Cloudlet> cloudlets, List<Vm> allVms, double makespan) {
        if (allVms.isEmpty() || makespan <= 0) return 0.0;

        // Track [firstStart, lastFinish] per VM
        Map<Long, double[]> vmSpan = new HashMap<>();
        for (Cloudlet c : cloudlets) {
            if (c.getStatus() != Cloudlet.Status.SUCCESS) continue;
            Vm vm = c.getVm();
            if (vm == null || vm == Vm.NULL) continue;
            double start  = c.getExecStartTime();
            double finish = c.getFinishTime();
            if (finish <= 0) continue;
            vmSpan.compute(vm.getId(), (k, v) -> {
                if (v == null) return new double[]{start, finish};
                return new double[]{Math.min(v[0], start), Math.max(v[1], finish)};
            });
        }

        double totalUtil = 0.0;
        for (double[] span : vmSpan.values()) {
            double busySpan = span[1] - span[0];
            totalUtil += Math.min(1.0, busySpan / makespan);
        }

        // Divide by ALL VMs — idle VMs contribute 0
        return (totalUtil / allVms.size()) * 100.0;
    }

    private double accumulatedWh(Host host) {
        List<HostStateHistoryEntry> history = host.getStateHistory();
        if (history == null || history.isEmpty()) return 0.0;
        double totalJoules = 0.0;
        var pm = host.getPowerModel();
        for (int i = 1; i < history.size(); i++) {
            HostStateHistoryEntry prev = history.get(i - 1);
            HostStateHistoryEntry curr = history.get(i);
            double dt = curr.time() - prev.time();
            if (dt <= 0) continue;
            double totalMips = host.getTotalMipsCapacity();
            double util = totalMips > 0
                    ? Math.min(1.0, (prev.requestedMips() + curr.requestedMips()) / 2.0 / totalMips)
                    : 0.0;
            try { totalJoules += pm.getPower(util) * dt; } catch (Exception ignored) {}
        }
        return totalJoules / 3_600.0;
    }
}
