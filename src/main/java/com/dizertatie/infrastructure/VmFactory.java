package com.dizertatie.infrastructure;

import com.dizertatie.config.SimulationConfig;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds heterogeneous VMs: FAST_VM, BALANCED_VM, ECO_VM.
 * Each VM uses a time-shared cloudlet scheduler so multiple cloudlets
 * can run concurrently on a single VM.
 */
public final class VmFactory {

    private VmFactory() {}

    public static Vm createFast() {
        return new VmSimple(SimulationConfig.VM_FAST_MIPS, SimulationConfig.VM_FAST_PES)
                .setRam(SimulationConfig.VM_FAST_RAM)
                .setBw(SimulationConfig.VM_BW)
                .setSize(SimulationConfig.VM_SIZE)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    public static Vm createBalanced() {
        return new VmSimple(SimulationConfig.VM_BALANCED_MIPS, SimulationConfig.VM_BALANCED_PES)
                .setRam(SimulationConfig.VM_BALANCED_RAM)
                .setBw(SimulationConfig.VM_BW)
                .setSize(SimulationConfig.VM_SIZE)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    public static Vm createEco() {
        return new VmSimple(SimulationConfig.VM_ECO_MIPS, SimulationConfig.VM_ECO_PES)
                .setRam(SimulationConfig.VM_ECO_RAM)
                .setBw(SimulationConfig.VM_BW)
                .setSize(SimulationConfig.VM_SIZE)
                .setCloudletScheduler(new CloudletSchedulerTimeShared());
    }

    /**
     * Build a flat list of all VM types (VMS_PER_TYPE of each).
     * Used when creating a broker's initial VM pool.
     */
    public static List<Vm> createMixedVmList() {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < SimulationConfig.VMS_PER_TYPE; i++) {
            vms.add(createFast());
            vms.add(createBalanced());
            vms.add(createEco());
        }
        return vms;
    }

    /**
     * Build a list of additional FAST VMs for peak scale-up.
     */
    public static List<Vm> createFastVmPool(int count) {
        List<Vm> vms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) vms.add(createFast());
        return vms;
    }

    /**
     * Build a list of additional ECO VMs for low-load consolidation.
     */
    public static List<Vm> createEcoVmPool(int count) {
        List<Vm> vms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) vms.add(createEco());
        return vms;
    }
}
