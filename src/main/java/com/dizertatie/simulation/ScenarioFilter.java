package com.dizertatie.simulation;

import com.dizertatie.model.TaskRecord;
import com.dizertatie.dataset.TaskMapper;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;

import java.util.List;

public final class ScenarioFilter {

    public static final double MORNING_START =  7 * 3600;
    public static final double MORNING_END   = 10 * 3600;
    public static final double EVENING_START = 20 * 3600;
    public static final double EVENING_END   = 24 * 3600;

    private ScenarioFilter() {}

    public static List<Cloudlet> filter(List<Cloudlet> all, double windowStart, double windowEnd) {
        return all.stream()
                .filter(c -> c.getSubmissionDelay() >= windowStart && c.getSubmissionDelay() <= windowEnd)
                .toList();
    }

    public static List<Cloudlet> morningPeak(List<Cloudlet> all) {
        return filter(all, MORNING_START, MORNING_END);
    }

    /**
     * Evening Idle: sparse traffic 20:00-24:00.
     * Delays are re-based to start from 0 so VMs are fresh and tasks
     * can meet their relative deadlines instead of queuing behind a full day backlog.
     */
    public static List<Cloudlet> eveningIdle(List<Cloudlet> all) {
        List<Cloudlet> evening = filter(all, EVENING_START, EVENING_END);
        if (evening.isEmpty()) return evening;

        double minDelay = evening.stream()
                .mapToDouble(Cloudlet::getSubmissionDelay)
                .min().orElse(EVENING_START);

        evening.forEach(c -> c.setSubmissionDelay(c.getSubmissionDelay() - minDelay));
        return evening;
    }

    public static List<Cloudlet> mixedDaily(List<Cloudlet> all) {
        return all;
    }

    public static List<Cloudlet> faultScenario(List<Cloudlet> all) {
        return morningPeak(all);
    }

    public static List<Cloudlet> energyFlexibility(List<Cloudlet> all) {
        return all.stream().map(c -> {
            TaskRecord t = TaskMapper.getTask(c);
            if (t != null && !t.isCritical() && t.getDataSizeMb() >= 500.0) {
                double newDelay = Math.max(c.getSubmissionDelay(), EVENING_START);
                c.setSubmissionDelay(newDelay);
            }
            return c;
        }).toList();
    }
}
