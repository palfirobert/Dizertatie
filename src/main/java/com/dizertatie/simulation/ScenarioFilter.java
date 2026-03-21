package com.dizertatie.simulation;

import com.dizertatie.model.TaskRecord;
import com.dizertatie.dataset.TaskMapper;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;

import java.util.List;

/**
 * Scenario filter helpers.
 * Each static method returns a filtered + time-rescaled copy of the
 * full cloudlet list appropriate for that scenario.
 *
 * Time windows (seconds from midnight = 0):
 *   Morning Peak   → 07:00–10:00  (25200 – 36000 s)
 *   Evening Idle   → 20:00–24:00  (72000 – 86400 s)
 *   Mixed Daily    → full 24 h    (0 – 86400 s)
 *   Fault          → Morning Peak subset with fault injection enabled
 *   Energy Flex    → full day but only ECO/BALANCED tasks prioritised
 */
public final class ScenarioFilter {

    // Seconds from midnight
    public static final double MORNING_START =  7 * 3600;   // 07:00
    public static final double MORNING_END   = 10 * 3600;   // 10:00
    public static final double EVENING_START = 20 * 3600;   // 20:00
    public static final double EVENING_END   = 24 * 3600;   // 24:00

    private ScenarioFilter() {}

    /** Keep only tasks that arrive between [windowStart, windowEnd]. */
    public static List<Cloudlet> filter(List<Cloudlet> all,
                                        double windowStart, double windowEnd) {
        return all.stream()
                .filter(c -> {
                    double t = c.getSubmissionDelay();
                    return t >= windowStart && t <= windowEnd;
                })
                .toList();
    }

    /** Morning Peak: high-load window 07:00–10:00. */
    public static List<Cloudlet> morningPeak(List<Cloudlet> all) {
        return filter(all, MORNING_START, MORNING_END);
    }

    /** Evening Idle: sparse traffic after 20:00. */
    public static List<Cloudlet> eveningIdle(List<Cloudlet> all) {
        return filter(all, EVENING_START, EVENING_END);
    }

    /** Mixed Daily: all tasks, full 24-hour window. */
    public static List<Cloudlet> mixedDaily(List<Cloudlet> all) {
        return all; // no filtering — use everything
    }

    /**
     * Fault scenario: Morning Peak window.
     * Fault injection is handled in SimulationEngine, not here.
     */
    public static List<Cloudlet> faultScenario(List<Cloudlet> all) {
        return morningPeak(all);
    }

    /**
     * Energy Flexibility: full day but CRITICAL tasks keep their delay;
     * ROUTINE tasks with high data_size (>= 500 MB) are pushed to off-peak
     * evening hours to reduce peak-hour energy draw.
     */
    public static List<Cloudlet> energyFlexibility(List<Cloudlet> all) {
        return all.stream().map(c -> {
            TaskRecord t = TaskMapper.getTask(c);
            if (t != null && !t.isCritical() && t.getDataSizeMb() >= 500.0) {
                // Defer to evening window
                double newDelay = Math.max(c.getSubmissionDelay(), EVENING_START);
                c.setSubmissionDelay(newDelay);
            }
            return c;
        }).toList();
    }
}
