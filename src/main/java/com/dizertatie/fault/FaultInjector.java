package com.dizertatie.fault;

import com.dizertatie.config.SimulationConfig;
import org.cloudbus.cloudsim.hosts.Host;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stage 7 — Fault Injector.
 * Randomly selects hosts to mark as failed, simulating hardware failures.
 * Uses FAULT_PROBABILITY from SimulationConfig.
 */
public class FaultInjector {

    private final Random rng = new Random(SimulationConfig.RANDOM_SEED);
    private final List<Host> failedHosts = new ArrayList<>();

    /**
     * Inject faults into the provided host list.
     * Each host has a FAULT_PROBABILITY chance of being marked as failed.
     *
     * @param hosts all hosts across all datacenters
     * @return list of hosts that were failed
     */
    public List<Host> injectFaults(List<Host> hosts) {
        failedHosts.clear();
        for (Host host : hosts) {
            if (rng.nextDouble() < SimulationConfig.FAULT_PROBABILITY) {
                host.setActive(false);
                failedHosts.add(host);
                System.out.printf("[FaultInjector] Host %d in DC '%s' marked as FAILED%n",
                        host.getId(), host.getDatacenter().getName());
            }
        }
        System.out.printf("[FaultInjector] %d / %d hosts failed%n", failedHosts.size(), hosts.size());
        return failedHosts;
    }

    /**
     * Fail a specific percentage of hosts (for controlled scenario testing).
     *
     * @param hosts      host pool
     * @param percentage fraction 0.0–1.0 of hosts to fail
     * @return list of failed hosts
     */
    public List<Host> injectFaultsControlled(List<Host> hosts, double percentage) {
        failedHosts.clear();
        int count = (int) Math.ceil(hosts.size() * percentage);
        List<Host> shuffled = new ArrayList<>(hosts);
        java.util.Collections.shuffle(shuffled, rng);
        for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
            Host host = shuffled.get(i);
            host.setActive(false);
            failedHosts.add(host);
            System.out.printf("[FaultInjector] Host %d in DC '%s' FORCED FAIL%n",
                    host.getId(), host.getDatacenter().getName());
        }
        System.out.printf("[FaultInjector] Controlled fault: %d hosts failed%n", failedHosts.size());
        return failedHosts;
    }

    /** Restore all previously failed hosts (for recovery measurement). */
    public void restoreAll() {
        for (Host h : failedHosts) {
            h.setActive(true);
            System.out.printf("[FaultInjector] Host %d RESTORED%n", h.getId());
        }
        failedHosts.clear();
    }

    public List<Host> getFailedHosts() { return List.copyOf(failedHosts); }
}
