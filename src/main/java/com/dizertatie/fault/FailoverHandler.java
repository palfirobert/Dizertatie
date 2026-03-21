package com.dizertatie.fault;

import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 7 — Failover Handler.
 * Detects cloudlets that were running on failed hosts and reassigns them
 * to healthy VMs. Also measures recovery time.
 */
public class FailoverHandler {

    private double recoveryStartTime = 0;
    private double recoveryEndTime   = 0;
    private final List<Cloudlet> recoveredCloudlets = new ArrayList<>();

    /**
     * Reassign cloudlets whose assigned VM is on a failed (inactive) host
     * to the first available healthy VM.
     *
     * @param cloudlets  all submitted cloudlets
     * @param allVms     all VMs in the simulation
     * @param currentTime current simulation time (for recovery measurement)
     * @return number of cloudlets reassigned
     */
    public int handleFailover(List<Cloudlet> cloudlets, List<Vm> allVms, double currentTime) {
        recoveryStartTime = currentTime;
        recoveredCloudlets.clear();

        List<Vm> healthyVms = allVms.stream()
                .filter(vm -> vm.getHost() != null && vm.getHost().isActive())
                .toList();

        if (healthyVms.isEmpty()) {
            System.err.println("[FailoverHandler] WARNING: No healthy VMs available for failover!");
            return 0;
        }

        int idx = 0;
        for (Cloudlet c : cloudlets) {
            Vm assigned = c.getVm();
            boolean onFailedHost = assigned == null
                    || assigned == Vm.NULL
                    || !assigned.getHost().isActive();

            if (onFailedHost && c.getStatus() != Cloudlet.Status.SUCCESS) {
                Vm replacement = healthyVms.get(idx % healthyVms.size());
                c.setVm(replacement);
                recoveredCloudlets.add(c);
                idx++;
                System.out.printf("[FailoverHandler] Cloudlet %d reassigned to VM %d (host %d)%n",
                        c.getId(), replacement.getId(), replacement.getHost().getId());
            }
        }

        recoveryEndTime = currentTime + estimateRecoveryDelay(recoveredCloudlets.size());
        System.out.printf("[FailoverHandler] Failover complete: %d cloudlets reassigned, " +
                          "estimated recovery in %.1f s%n",
                recoveredCloudlets.size(), getRecoveryTime());

        return recoveredCloudlets.size();
    }

    /** Simple heuristic: 5 s base + 0.1 s per reassigned cloudlet. */
    private double estimateRecoveryDelay(int count) {
        return 5.0 + count * 0.1;
    }

    public double getRecoveryTime()          { return recoveryEndTime - recoveryStartTime; }
    public double getRecoveryStartTime()     { return recoveryStartTime; }
    public double getRecoveryEndTime()       { return recoveryEndTime; }
    public int    getRecoveredCount()        { return recoveredCloudlets.size(); }
    public List<Cloudlet> getRecovered()     { return List.copyOf(recoveredCloudlets); }
}
