package com.dizertatie.metrics;

/**
 * Immutable value object that carries every metric produced by one
 * simulation run (one scheduler × one scenario combination).
 */
public class SimulationResult {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final String schedulerName;
    private final String scenarioName;

    // ── Throughput / completion ───────────────────────────────────────────────
    private final int    totalTasks;
    private final int    completedTasks;
    private final int    failedTasks;
    private final double makespan;          // seconds
    private final double throughput;        // tasks / second

    // ── SLA ───────────────────────────────────────────────────────────────────
    private final int    slaViolations;
    private final double slaViolationRate;  // 0.0 – 1.0

    // ── Energy ────────────────────────────────────────────────────────────────
    private final double totalEnergyKWh;    // kWh
    private final double energyPerTask;     // Wh / task

    // ── Utilisation ───────────────────────────────────────────────────────────
    private final double avgCpuUtilisation; // 0.0 – 1.0

    // ── Fault / Failover ──────────────────────────────────────────────────────
    private final int    failedHosts;
    private final int    recoveredCloudlets;
    private final double recoveryTimeSec;

    public SimulationResult(
            String schedulerName, String scenarioName,
            int totalTasks, int completedTasks, int failedTasks,
            double makespan, double throughput,
            int slaViolations, double slaViolationRate,
            double totalEnergyKWh, double energyPerTask,
            double avgCpuUtilisation,
            int failedHosts, int recoveredCloudlets, double recoveryTimeSec) {

        this.schedulerName      = schedulerName;
        this.scenarioName       = scenarioName;
        this.totalTasks         = totalTasks;
        this.completedTasks     = completedTasks;
        this.failedTasks        = failedTasks;
        this.makespan           = makespan;
        this.throughput         = throughput;
        this.slaViolations      = slaViolations;
        this.slaViolationRate   = slaViolationRate;
        this.totalEnergyKWh     = totalEnergyKWh;
        this.energyPerTask      = energyPerTask;
        this.avgCpuUtilisation  = avgCpuUtilisation;
        this.failedHosts        = failedHosts;
        this.recoveredCloudlets = recoveredCloudlets;
        this.recoveryTimeSec    = recoveryTimeSec;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getSchedulerName()      { return schedulerName; }
    public String getScenarioName()       { return scenarioName; }
    public int    getTotalTasks()         { return totalTasks; }
    public int    getCompletedTasks()     { return completedTasks; }
    public int    getFailedTasks()        { return failedTasks; }
    public double getMakespan()           { return makespan; }
    public double getThroughput()         { return throughput; }
    public int    getSlaViolations()      { return slaViolations; }
    public double getSlaViolationRate()   { return slaViolationRate; }
    public double getTotalEnergyKWh()     { return totalEnergyKWh; }
    public double getEnergyPerTask()      { return energyPerTask; }
    public double getAvgCpuUtilisation()  { return avgCpuUtilisation; }
    public int    getFailedHosts()        { return failedHosts; }
    public int    getRecoveredCloudlets() { return recoveredCloudlets; }
    public double getRecoveryTimeSec()    { return recoveryTimeSec; }

    @Override
    public String toString() {
        return String.format(
            "[%s / %s] tasks=%d done=%d slaVio=%d energy=%.3f kWh makespan=%.1f s",
            schedulerName, scenarioName, totalTasks, completedTasks,
            slaViolations, totalEnergyKWh, makespan);
    }
}
