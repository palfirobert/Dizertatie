package com.dizertatie.simulation;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

import com.dizertatie.dataset.TaskMapper;
import com.dizertatie.model.TaskRecord;

public class ScenarioFilterTest {

    private final TaskMapper mapper = new TaskMapper();

    @Before
    public void setUp() {
        TaskMapper.clearRegistry();
    }

    @Test
    public void eveningIdleDoesNotMutateInputCloudlets() {
        List<Cloudlet> all = new ArrayList<>();

        Cloudlet c1 = mapper.map(new TaskRecord(
                1, 72_100.0, 10_000, 1,
                512, 90_000.0, 1_000.0, 1,
                "ROUTINE", "EU_WEST", 100.0));
        Cloudlet c2 = mapper.map(new TaskRecord(
                2, 72_400.0, 10_000, 1,
                512, 90_000.0, 1_000.0, 1,
                "ROUTINE", "EU_WEST", 100.0));
        Cloudlet c3 = mapper.map(new TaskRecord(
                3, 25_000.0, 10_000, 1,
                512, 90_000.0, 1_000.0, 1,
                "ROUTINE", "EU_WEST", 100.0));

        all.add(c1);
        all.add(c2);
        all.add(c3);

        double originalDelay1 = c1.getSubmissionDelay();
        double originalDelay2 = c2.getSubmissionDelay();
        double originalDelay3 = c3.getSubmissionDelay();

        List<Cloudlet> evening = ScenarioFilter.eveningIdle(all);

        assertEquals("Expected two evening tasks", 2, evening.size());
        assertEquals("Input cloudlet delay must stay unchanged", originalDelay1, c1.getSubmissionDelay(), 1e-9);
        assertEquals("Input cloudlet delay must stay unchanged", originalDelay2, c2.getSubmissionDelay(), 1e-9);
        assertEquals("Input cloudlet delay must stay unchanged", originalDelay3, c3.getSubmissionDelay(), 1e-9);

        double minEveningDelay = evening.stream().mapToDouble(Cloudlet::getSubmissionDelay).min().orElse(-1.0);
        assertEquals("Evening scenario should be re-based to zero", 0.0, minEveningDelay, 1e-9);

        assertTrue("Returned list must contain copied cloudlets", evening.stream().noneMatch(all::contains));
    }
}
