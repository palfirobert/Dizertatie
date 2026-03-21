package com.dizertatie.model;

/**
 * Represents one row from synthetic_cloud_workload_10000.csv.
 * All fields are parsed exactly as they appear in the dataset.
 */
public class TaskRecord {

    private final int    id;
    private final double arrivalTimeSec;
    private final long   lengthMi;
    private final int    cpuCores;
    private final long   ramMb;
    private final double deadlineSec;
    private final double deadlineRelSec;   // deadline relative to arrival (from CSV)
    private final int    priority;
    private final String type;          // "CRITICAL" | "ROUTINE"
    private final String dataRegion;    // e.g. "EU_WEST"
    private final double dataSizeMb;

    public TaskRecord(int id, double arrivalTimeSec, long lengthMi, int cpuCores,
                      long ramMb, double deadlineSec, double deadlineRelSec, int priority,
                      String type, String dataRegion, double dataSizeMb) {
        this.id             = id;
        this.arrivalTimeSec = arrivalTimeSec;
        this.lengthMi       = lengthMi;
        this.cpuCores       = cpuCores;
        this.ramMb          = ramMb;
        this.deadlineSec    = deadlineSec;
        this.deadlineRelSec = deadlineRelSec;
        this.priority       = priority;
        this.type           = type;
        this.dataRegion     = dataRegion;
        this.dataSizeMb     = dataSizeMb;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int    getId()             { return id; }
    public double getArrivalTimeSec() { return arrivalTimeSec; }
    public long   getLengthMi()       { return lengthMi; }
    public int    getCpuCores()       { return cpuCores; }
    public long   getRamMb()          { return ramMb; }
    public double getDeadlineSec()    { return deadlineSec; }
    public double getDeadlineRelSec() { return deadlineRelSec; }
    public int    getPriority()       { return priority; }
    public String getType()           { return type; }
    public String getDataRegion()     { return dataRegion; }
    public double getDataSizeMb()     { return dataSizeMb; }

    /**
     * Effective absolute deadline: arrival + relative slack.
     * Use this for SLA checks so tasks arriving late in the day
     * are not penalised for an already-passed absolute deadline.
     */
    public double getEffectiveDeadlineSec() {
        return arrivalTimeSec + deadlineRelSec;
    }

    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(type);
    }

    @Override
    public String toString() {
        return String.format("Task[id=%d, arrival=%.1fs, length=%d MI, cores=%d, " +
                             "ram=%d MB, deadline=%.1fs, relDeadline=%.1fs, priority=%d, type=%s, region=%s]",
                id, arrivalTimeSec, lengthMi, cpuCores,
                ramMb, deadlineSec, deadlineRelSec, priority, type, dataRegion);
    }
}
