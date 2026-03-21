package com.dizertatie.dataset;

import com.dizertatie.model.TaskRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelDynamic;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts TaskRecord objects into CloudSim Cloudlets.
 * Because CloudSim 7.3.2 Cloudlet has no setUserData/getUserData,
 * we maintain a static registry: cloudlet-ID → TaskRecord.
 */
public class TaskMapper {

    /** Global registry: cloudlet ID → TaskRecord. Populated during mapAll(). */
    private static final Map<Integer, TaskRecord> REGISTRY = new ConcurrentHashMap<>();

    public List<Cloudlet> mapAll(List<TaskRecord> records) {
        List<Cloudlet> cloudlets = new ArrayList<>(records.size());
        for (TaskRecord r : records) cloudlets.add(map(r));
        return cloudlets;
    }

    public Cloudlet map(TaskRecord r) {
        var cpuModel = r.isCritical()
                ? new UtilizationModelFull()
                : new UtilizationModelDynamic(0.8);
        var ramModel = new UtilizationModelDynamic(Math.min(1.0, r.getRamMb() / 16_384.0));
        var bwModel  = new UtilizationModelDynamic(Math.min(1.0, r.getDataSizeMb() / 1_000.0));

        CloudletSimple cloudlet = new CloudletSimple(
                r.getId(),
                Math.max(1, r.getLengthMi()),
                Math.max(1, r.getCpuCores()));

        cloudlet.setUtilizationModelCpu(cpuModel)
                .setUtilizationModelRam(ramModel)
                .setUtilizationModelBw(bwModel)
                .setSubmissionDelay(r.getArrivalTimeSec());

        // Register in static map so schedulers/metrics can look up by ID
        REGISTRY.put(r.getId(), r);
        return cloudlet;
    }

    /** Register a clone cloudlet (replica) under the same TaskRecord. */
    public static void registerClone(int newId, int originalId) {
        TaskRecord original = REGISTRY.get(originalId);
        if (original != null) REGISTRY.put(newId, original);
    }

    public static TaskRecord getTask(Cloudlet c) {
        return REGISTRY.get((int) c.getId());
    }

    public static TaskRecord getTask(int cloudletId) {
        return REGISTRY.get(cloudletId);
    }

    public static boolean isCritical(Cloudlet c) {
        TaskRecord t = getTask(c);
        return t != null && t.isCritical();
    }

    public static double deadline(Cloudlet c) {
        TaskRecord t = getTask(c);
        return t != null ? t.getDeadlineSec() : Double.MAX_VALUE;
    }

    public static void clearRegistry() {
        REGISTRY.clear();
    }
}
