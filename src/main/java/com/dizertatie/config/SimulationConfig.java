package com.dizertatie.config;

/**
 * Central configuration for the entire simulation.
 * All tuneable parameters live here so experiments are reproducible.
 */
public final class SimulationConfig {

    private SimulationConfig() {}

    // ── Reproducibility ──────────────────────────────────────────────────────
    public static final long RANDOM_SEED = 42L;

    // ── Dataset ──────────────────────────────────────────────────────────────
    public static final String DATASET_PATH      = "synthetic_cloud_workload_10000.csv";
    public static final String PREDICTIONS_PATH  = "predictions.csv";
    public static final String RESULTS_DIR       = "results/";

    // ── Simulation time (seconds) ─────────────────────────────────────────────
    public static final double SIMULATION_LIMIT  = 86_400.0; // 24 h

    // ── Datacenter regions ────────────────────────────────────────────────────
    public static final String REGION_EU_WEST    = "EU_WEST";
    public static final String REGION_EU_CENTRAL = "EU_CENTRAL";
    public static final String REGION_US_EAST    = "US_EAST";

    // ── Host types (MIPS per PE) ──────────────────────────────────────────────
    public static final long   HOST_FAST_MIPS     = 10_000;
    public static final long   HOST_BALANCED_MIPS =  6_000;
    public static final long   HOST_ECO_MIPS      =  3_000;

    public static final int    HOST_FAST_PES      = 32;
    public static final int    HOST_BALANCED_PES  = 16;
    public static final int    HOST_ECO_PES       =  8;

    public static final long   HOST_FAST_RAM      = 131_072; // 128 GB
    public static final long   HOST_BALANCED_RAM  =  65_536; // 64 GB
    public static final long   HOST_ECO_RAM       =  32_768; // 32 GB

    public static final long   HOST_BW            = 100_000; // 100 Gbps (Mbps)
    public static final long   HOST_STORAGE       = 1_000_000; // 1 TB

    // Hosts per datacenter per type
    public static final int    HOSTS_PER_TYPE     = 3;

    // ── VM types ──────────────────────────────────────────────────────────────
    public static final long   VM_FAST_MIPS      = 4_000;
    public static final long   VM_BALANCED_MIPS  = 2_000;
    public static final long   VM_ECO_MIPS       = 1_000;

    public static final int    VM_FAST_PES       = 8;
    public static final int    VM_BALANCED_PES   = 4;
    public static final int    VM_ECO_PES        = 2;

    public static final long   VM_FAST_RAM       = 16_384; // 16 GB
    public static final long   VM_BALANCED_RAM   =  8_192; //  8 GB
    public static final long   VM_ECO_RAM        =  4_096; //  4 GB

    public static final long   VM_BW             = 10_000;
    public static final long   VM_SIZE           = 50_000;

    // VMs per type per datacenter
    public static final int    VMS_PER_TYPE      = 4;

    // ── Power models (Watts) ──────────────────────────────────────────────────
    public static final double POWER_FAST_MAX    = 300.0;
    public static final double POWER_FAST_IDLE   = 100.0;
    public static final double POWER_BAL_MAX     = 200.0;
    public static final double POWER_BAL_IDLE    =  70.0;
    public static final double POWER_ECO_MAX     = 120.0;
    public static final double POWER_ECO_IDLE    =  40.0;

    // ── Multi-objective scheduler weights ─────────────────────────────────────
    public static final double W1_ENERGY         = 0.30;
    public static final double W2_SLA            = 0.30;
    public static final double W3_NETWORK        = 0.15;
    public static final double W4_IDLE           = 0.15;
    public static final double W5_REDUNDANCY     = 0.10;

    // ── SLA ──────────────────────────────────────────────────────────────────
    public static final double SLA_PENALTY_HIGH  = 1.0;   // CRITICAL
    public static final double SLA_PENALTY_LOW   = 0.3;   // ROUTINE

    // ── Fault injection ───────────────────────────────────────────────────────
    public static final double FAULT_PROBABILITY = 0.05;   // 5 % chance per host

    // ── Network cost between regions (normalised 0-1) ─────────────────────────
    // Same region = 0, different continent = 1
    public static double networkCost(String taskRegion, String vmRegion) {
        if (taskRegion == null || vmRegion == null)   return 0.5;
        if (taskRegion.equals(vmRegion))              return 0.0;
        boolean sameContinent =
            (taskRegion.startsWith("EU") && vmRegion.startsWith("EU")) ||
            (taskRegion.startsWith("US") && vmRegion.startsWith("US"));
        return sameContinent ? 0.4 : 1.0;
    }
}
