package com.dizertatie.metrics;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.dizertatie.config.SimulationConfig;

/**
 * Stage 8 — Results Exporter.
 * Writes three CSV files into the results/ directory:
 *   summary_results.csv  – one row per scheduler × scenario
 *   energy_results.csv   – energy-focused columns
 *   sla_results.csv      – SLA-focused columns
 */
public class ResultsExporter {

    private final String dir;

    public ResultsExporter() {
        this(SimulationConfig.RESULTS_DIR);
    }

    public ResultsExporter(String dir) {
        this.dir = dir;
        try { Files.createDirectories(Paths.get(dir)); }
        catch (IOException e) { System.err.println("Cannot create results dir: " + e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public void exportAll(List<SimulationResult> results) {
        exportSummary(results);
        exportEnergy(results);
        exportSla(results);
    }

    // ── summary_results.csv ───────────────────────────────────────────────────

    public void exportSummary(List<SimulationResult> results) {
        String file = dir + "summary_results.csv";
        try (PrintWriter pw = writer(file)) {
            pw.println("Scheduler,Scenario,TotalTasks,CompletedTasks,FailedTasks," +
                       "Makespan_s,Throughput_tasks_per_s,SlaViolations,SlaViolationRate_pct," +
                       "TotalEnergy_kWh,EnergyPerTask_Wh,AvgCpuUtilisation_pct," +
                       "FailedHosts,RecoveredCloudlets,RecoveryTime_s");
            for (SimulationResult r : results) {
                pw.printf("%s,%s,%d,%d,%d,%.3f,%.6f,%d,%.2f,%.6f,%.4f,%.4f,%d,%d,%.3f%n",
                    r.getSchedulerName(), r.getScenarioName(),
                    r.getTotalTasks(), r.getCompletedTasks(), r.getFailedTasks(),
                    r.getMakespan(), r.getThroughput(),
                    r.getSlaViolations(), r.getSlaViolationRate() * 100.0,
                    r.getTotalEnergyKWh(), r.getEnergyPerTask(),
                    r.getAvgCpuUtilisation(),
                    r.getFailedHosts(), r.getRecoveredCloudlets(), r.getRecoveryTimeSec());
            }
            System.out.println("[ResultsExporter] Written: " + file);
        } catch (IOException e) {
            System.err.println("[ResultsExporter] ERROR writing " + file + ": " + e.getMessage());
        }
    }

    // ── energy_results.csv ────────────────────────────────────────────────────

    public void exportEnergy(List<SimulationResult> results) {
        String file = dir + "energy_results.csv";
        try (PrintWriter pw = writer(file)) {
            pw.println("Scheduler,Scenario,TotalEnergy_kWh,EnergyPerTask_Wh," +
                       "CompletedTasks,AvgCpuUtilisation_pct");
            for (SimulationResult r : results) {
                pw.printf("%s,%s,%.6f,%.4f,%d,%.4f%n",
                    r.getSchedulerName(), r.getScenarioName(),
                    r.getTotalEnergyKWh(), r.getEnergyPerTask(),
                    r.getCompletedTasks(), r.getAvgCpuUtilisation());
            }
            System.out.println("[ResultsExporter] Written: " + file);
        } catch (IOException e) {
            System.err.println("[ResultsExporter] ERROR writing " + file + ": " + e.getMessage());
        }
    }

    // ── sla_results.csv ───────────────────────────────────────────────────────

    public void exportSla(List<SimulationResult> results) {
        String file = dir + "sla_results.csv";
        try (PrintWriter pw = writer(file)) {
            pw.println("Scheduler,Scenario,TotalTasks,SlaViolations,SlaViolationRate_pct," +
                       "Makespan_s,Throughput_tasks_per_s");
            for (SimulationResult r : results) {
                pw.printf("%s,%s,%d,%d,%.2f,%.3f,%.6f%n",
                    r.getSchedulerName(), r.getScenarioName(),
                    r.getTotalTasks(), r.getSlaViolations(),
                    r.getSlaViolationRate() * 100.0,
                    r.getMakespan(), r.getThroughput());
            }
            System.out.println("[ResultsExporter] Written: " + file);
        } catch (IOException e) {
            System.err.println("[ResultsExporter] ERROR writing " + file + ": " + e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PrintWriter writer(String path) throws IOException {
        return new PrintWriter(new BufferedWriter(new FileWriter(path)));
    }
}
