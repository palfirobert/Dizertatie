package com.dizertatie.metrics;

import java.util.Collections;
import java.util.List;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.model.TaskRecord;

public class MetricsCollectorTest {

    private final TaskMapper mapper = new TaskMapper();

    @Before
    public void setUp() {
        TaskMapper.clearRegistry();
    }

    @Test
    public void collectDeduplicatesReplicaCloudletsByLogicalTask() {
        Cloudlet original = mapper.map(new TaskRecord(
                101, 0.0, 10_000, 1,
                1024, 200.0, 200.0, 1,
                "CRITICAL", "EU_WEST", 100.0));

        TestCloudlet replicaFast = new TestCloudlet(10_000, 1);
        TaskMapper.registerClone((int) replicaFast.getId(), (int) original.getId());
        replicaFast.setStatus(Cloudlet.Status.SUCCESS);
        replicaFast.setExecStartTime(0.0);
        replicaFast.forceFinishTime(10.0);

        TestCloudlet replicaSlow = new TestCloudlet(10_000, 1);
        TaskMapper.registerClone((int) replicaSlow.getId(), (int) original.getId());
        replicaSlow.setStatus(Cloudlet.Status.SUCCESS);
        replicaSlow.setExecStartTime(0.0);
        replicaSlow.forceFinishTime(20.0);

        MetricsCollector collector = new MetricsCollector();
        SimulationResult result = collector.collect(
                "MultiObjective",
                "ReplicaTest",
                List.of(original),
                List.of(replicaFast, replicaSlow),
            Collections.emptyList(),
                Collections.emptyList(),
                null,
                null);

        assertEquals("Submitted logical tasks must be reported as total", 1, result.getTotalTasks());
        assertEquals("Replicas must count as one completed logical task", 1, result.getCompletedTasks());
        assertEquals("Earliest successful replica should define logical task completion", 10.0, result.getMakespan(), 1e-9);
        assertEquals("No SLA violation expected", 0, result.getSlaViolations());
    }

    private static final class TestCloudlet extends CloudletSimple {
        private TestCloudlet(long length, int pes) {
            super(length, pes);
        }

        private void forceFinishTime(double finishTime) {
            setFinishTime(finishTime);
        }
    }
}
