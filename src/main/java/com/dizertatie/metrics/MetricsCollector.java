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
import java.util.List;

/**
 * Stage 8 — Metrics Collector.
 * Aggregates all simulation output into a {@link SimulationResult}.
 */
public class MetricsCollector {

    /**
     * Compute all metrics after the simulation has finished.
     *
     * @param schedulerName  name of the scheduler used
     * @param scenarioName   name of the scenario
     * @param finishedCls    cloudlets returned by the broker (finished + failed)
     * @param allVms         all VMs in the simulation
     * @param datacenters    all datacenters (for energy)
     * @param faultInjector  may be null if no faults were injected
     * @param failoverHandler may be null if no failover occurred
     */
    public SimulationResult collect(
            String schedulerName,
            String scenarioName,
            List<Cloudlet> finishedCls,
            List<Vm> allVms,
            Collection<Datacenter> datacenters,
            FaultInjector faultInjector,
            FailoverHandler failoverHandler) {

        // ── Task counts ───────────────────────────────────────────────────────
        int total     = finishedCls.size();
        int completed = (int) finishedCls.stream()
                .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS).count();
        int failed    = total - completed;

        // ── Makespan / throughput ─────────────────────────────────────────────
        double makespan = finishedCls.stream()
                .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS)
                .mapToDouble(Cloudlet::getFinishTime)
                .max().orElse(0.0);
        double throughput = makespan > 0 ? (double) completed / makespan : 0.0;

        // ── SLA violations ────────────────────────────────────────────────────
        int slaVio = 0;
        for (Cloudlet c : finishedCls) {
            if (c.getStatus() != Cloudlet.Status.SUCCESS) continue;
            TaskRecord t = TaskMapper.getTask(c);
            if (t == null) continue;
            if (c.getFinishTime() > t.getEffectiveDeadlineSec()) slaVio++;
        }
        double slaRate = total > 0 ? (double) slaVio / total : 0.0;

        // ── Energy ────────────────────────────────────────────────────────────
        double totalWh = 0.0;
        for (Datacenter dc : datacenters) {
            for (Host host : dc.getHostList()) {
                totalWh += accumulatedWh(host);
            }
        }
        double totalKWh     = totalWh / 1000.0;
        double energyPerTask = completed > 0 ? totalWh / completed : 0.0;

        // ── CPU utilisation ───────────────────────────────────────────────────
        // getCpuPercentUtilization() is instantaneous — always 0 after sim ends.
        // Instead compute a time-weighted average from host state history.
        double avgCpu = datacenters.stream()
                .flatMap(dc -> dc.getHostList().stream())
                .mapToDouble(this::timeWeightedCpuUtil)
                .average().orElse(0.0);

        // ── Fault / failover ──────────────────────────────────────────────────
        int    failedHosts     = faultInjector  != null ? faultInjector.getFailedHosts().size() : 0;
        int    recovered       = failoverHandler != null ? failoverHandler.getRecoveredCount()   : 0;
        double recoveryTime    = failoverHandler != null ? failoverHandler.getRecoveryTime()      : 0.0;

        return new SimulationResult(
                schedulerName, scenarioName,
                total, completed, failed,
                makespan, throughput,
                slaVio, slaRate,
                totalKWh, energyPerTask,
                avgCpu,
                failedHosts, recovered, recoveryTime);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Accumulate host energy (Wh) from the host's state history.
     * Each history entry stores CPU utilisation over a time interval;
     * we integrate using the trapezoidal rule and the host's power model.
     */
    private double accumulatedWh(Host host) {
        List<HostStateHistoryEntry> history = host.getStateHistory();
        if (history == null || history.isEmpty()) return 0.0;

        double totalJoules = 0.0;
        var pm = host.getPowerModel();

        for (int i = 1; i < history.size(); i++) {
            HostStateHistoryEntry prev = history.get(i - 1);
            HostStateHistoryEntry curr = history.get(i);
            double dt     = curr.time() - prev.time();   // seconds
            if (dt <= 0) continue;
            double totalMips = host.getTotalMipsCapacity();
            double util = totalMips > 0
                    ? Math.min(1.0, (prev.requestedMips() + curr.requestedMips()) / 2.0 / totalMips)
                    : 0.0;
            try {
                totalJoules += pm.getPower(util) * dt;
            } catch (Exception ignored) {}
        }
        return totalJoules / 3_600.0; // J → Wh
    }

    /**
     * Compute time-weighted CPU utilisation from host state history.
     */
    private double timeWeightedCpuUtil(Host host) {
        List<HostStateHistoryEntry> history = host.getStateHistory();
        if (history == null || history.isEmpty()) return 0.0;

        double totalUtil = 0.0;
        double totalTime = 0.0;

        for (int i = 1; i < history.size(); i++) {
            HostStateHistoryEntry prev = history.get(i - 1);
            HostStateHistoryEntry curr = history.get(i);
            double dt = curr.time() - prev.time(); // seconds
            if (dt <= 0) continue;
            double totalMips = host.getTotalMipsCapacity();
            double util = totalMips > 0
                    ? Math.min(1.0, (prev.requestedMips() + curr.requestedMips()) / 2.0 / totalMips)
                    : 0.0;
            totalUtil += util * dt;
            totalTime += dt;
        }
        return totalTime > 0 ? totalUtil / totalTime : 0.0;
    }
}
