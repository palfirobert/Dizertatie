package com.dizertatie.simulation;

import com.dizertatie.config.SimulationConfig;
import com.dizertatie.dataset.DatasetLoader;
import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.metrics.ResultsExporter;
import com.dizertatie.metrics.SimulationResult;
import com.dizertatie.scheduler.*;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;

import java.io.IOException;
import java.util.*;

/**
 * ExperimentLauncher — dissertation main entry point.
 *
 * Runs every scheduler × every scenario combination and exports results to CSV.
 *
 * Scenarios:
 *   1. MorningPeak       (07:00 – 10:00)
 *   2. EveningIdle       (20:00 – 24:00)
 *   3. MixedDaily        (full 24 h)
 *   4. FaultScenario     (morning peak + fault injection)
 *   5. EnergyFlexibility (deferred large tasks)
 *
 * Schedulers:
 *   RoundRobin | SJF | EnergyAware | MultiObjective
 */
public class ExperimentLauncher {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  Cloud Workload Scheduler — Dissertation Experiment      ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // ── 1. Load dataset ───────────────────────────────────────────────────
        List<Cloudlet> allCloudlets;
        try {
            var loader  = new DatasetLoader();
            var mapper  = new TaskMapper();
            var records = loader.load();
            allCloudlets = mapper.mapAll(records);
            System.out.printf("[Launcher] Mapped %d cloudlets from dataset%n", allCloudlets.size());
        } catch (IOException e) {
            System.err.println("[Launcher] FATAL: Cannot load dataset — " + e.getMessage());
            System.err.println("           Place '" + SimulationConfig.DATASET_PATH +
                               "' next to the JAR or in src/main/resources/");
            return;
        }

        // ── 2. Define schedulers ──────────────────────────────────────────────
        // MultiObjective needs a VM→region map that is built fresh per run
        // inside SimulationEngine, so we pass a sentinel instance here.
        List<BaseScheduler> schedulers = List.of(
                new RoundRobinScheduler(),
                new ShortestJobFirstScheduler(),
                new EnergyAwareScheduler(),
                new MultiObjectiveScheduler(Collections.emptyMap())  // region map injected at runtime
        );

        // ── 3. Define scenarios ───────────────────────────────────────────────
        // Each scenario is: (name, cloudlet-filter, injectFaults, faultPct)
        record Scenario(String name,
                        List<Cloudlet> cloudlets,
                        boolean fault,
                        double faultPct) {}

        List<Scenario> scenarios = List.of(
            new Scenario("MorningPeak",
                ScenarioFilter.morningPeak(allCloudlets),       false, 0.0),
            new Scenario("EveningIdle",
                ScenarioFilter.eveningIdle(allCloudlets),       false, 0.0),
            new Scenario("MixedDaily",
                ScenarioFilter.mixedDaily(allCloudlets),        false, 0.0),
            new Scenario("FaultScenario",
                ScenarioFilter.faultScenario(allCloudlets),     true,  0.20),
            new Scenario("EnergyFlexibility",
                ScenarioFilter.energyFlexibility(allCloudlets), false, 0.0)
        );

        // ── 4. Print scenario summary ─────────────────────────────────────────
        System.out.println();
        System.out.printf("%-22s %8s%n", "Scenario", "Tasks");
        System.out.println("-".repeat(32));
        for (Scenario s : scenarios) {
            System.out.printf("%-22s %8d%n", s.name(), s.cloudlets().size());
        }
        System.out.println();

        // ── 5. Run all combinations ───────────────────────────────────────────
        List<SimulationResult> allResults = new ArrayList<>();
        int total  = schedulers.size() * scenarios.size();
        int current = 0;

        for (Scenario scenario : scenarios) {
            if (scenario.cloudlets().isEmpty()) {
                System.out.printf("[Launcher] SKIP '%s' — no tasks in window%n", scenario.name());
                continue;
            }
            for (BaseScheduler scheduler : schedulers) {
                current++;
                System.out.printf("%n[Launcher] Run %d/%d  %-22s × %s%n",
                        current, total, scenario.name(), scheduler.getName());

                try {
                    SimulationEngine engine = new SimulationEngine(
                            scenario.name(),
                            scheduler,
                            scenario.cloudlets(),
                            scenario.fault(),
                            scenario.faultPct());

                    SimulationResult result = engine.run();
                    allResults.add(result);

                } catch (Exception e) {
                    System.err.printf("[Launcher] ERROR in run %d: %s%n", current, e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // ── 6. Export results ─────────────────────────────────────────────────
        System.out.println("\n[Launcher] Exporting results...");
        new ResultsExporter().exportAll(allResults);

        // ── 7. Print console summary table ───────────────────────────────────
        printSummaryTable(allResults);

        System.out.println("\n[Launcher] All experiments complete.");
        System.out.println("[Launcher] Results written to: " + SimulationConfig.RESULTS_DIR);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void printSummaryTable(List<SimulationResult> results) {
        System.out.println();
        System.out.println("━".repeat(120));
        System.out.printf("%-18s %-20s %7s %7s %6s %10s %10s %8s %8s%n",
                "Scheduler", "Scenario",
                "Done", "SLA_Vio", "SLA%",
                "Energy_kWh", "E/Task_Wh",
                "Make_s", "Thrpt");
        System.out.println("━".repeat(120));

        for (SimulationResult r : results) {
            System.out.printf("%-18s %-20s %7d %7d %6.1f %10.4f %10.3f %8.1f %8.4f%n",
                    r.getSchedulerName(),
                    r.getScenarioName(),
                    r.getCompletedTasks(),
                    r.getSlaViolations(),
                    r.getSlaViolationRate() * 100.0,
                    r.getTotalEnergyKWh(),
                    r.getEnergyPerTask(),
                    r.getMakespan(),
                    r.getThroughput());
        }
        System.out.println("━".repeat(120));
    }
}
