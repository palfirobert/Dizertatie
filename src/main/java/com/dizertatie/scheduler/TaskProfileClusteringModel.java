package com.dizertatie.scheduler;

import com.dizertatie.model.TaskRecord;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Lightweight unsupervised model that clusters tasks into scheduling profiles.
 *
 * Profiles are learned per scenario from task features, then used by the hybrid
 * scheduler to bias placement toward urgency-sensitive or resource-heavy VMs.
 */
public class TaskProfileClusteringModel {

    public enum Profile {
        LATENCY_SENSITIVE,
        RESOURCE_HEAVY,
        BALANCED
    }

    private static final int FEATURE_COUNT = 5;

    private final int clusterCount;
    private final int iterations;
    private final Random rng;

    private final double[] minValues = new double[FEATURE_COUNT];
    private final double[] maxValues = new double[FEATURE_COUNT];
    private double[][] centroids = new double[0][0];
    private Profile[] profiles = new Profile[0];

    public TaskProfileClusteringModel(int clusterCount, int iterations, long seed) {
        this.clusterCount = Math.max(1, clusterCount);
        this.iterations = Math.max(1, iterations);
        this.rng = new Random(seed);
    }

    public void fit(List<Cloudlet> cloudlets, java.util.function.Function<Cloudlet, TaskRecord> resolver) {
        List<double[]> data = new ArrayList<>();
        for (Cloudlet cloudlet : cloudlets) {
            TaskRecord task = resolver.apply(cloudlet);
            if (task != null) {
                data.add(rawFeatures(task));
            }
        }

        if (data.isEmpty()) {
            centroids = new double[][]{new double[FEATURE_COUNT]};
            profiles = new Profile[]{Profile.BALANCED};
            return;
        }

        initScaling(data);
        List<double[]> normalized = data.stream().map(this::normalize).toList();

        int k = Math.min(clusterCount, normalized.size());
        centroids = seedCentroids(normalized, k);

        int[] assignments = new int[normalized.size()];
        Arrays.fill(assignments, -1);

        for (int iteration = 0; iteration < iterations; iteration++) {
            boolean changed = assignClusters(normalized, assignments);
            recomputeCentroids(normalized, assignments, k);
            if (!changed) break;
        }

        profiles = classifyProfiles(centroids);
    }

    public Profile predictProfile(TaskRecord task) {
        if (task == null || centroids.length == 0) return Profile.BALANCED;
        double[] vector = normalize(rawFeatures(task));
        int best = nearestCentroid(vector);
        if (best < 0 || best >= profiles.length) return Profile.BALANCED;
        return profiles[best];
    }

    private double[] rawFeatures(TaskRecord task) {
        return new double[]{
                Math.log1p(task.getLengthMi()),
                task.getCpuCores(),
                Math.log1p(task.getRamMb()),
                Math.log1p(Math.max(1.0, task.getEffectiveDeadlineSec() - task.getArrivalTimeSec())),
                task.getPriority()
        };
    }

    private void initScaling(List<double[]> data) {
        Arrays.fill(minValues, Double.POSITIVE_INFINITY);
        Arrays.fill(maxValues, Double.NEGATIVE_INFINITY);
        for (double[] row : data) {
            for (int i = 0; i < FEATURE_COUNT; i++) {
                minValues[i] = Math.min(minValues[i], row[i]);
                maxValues[i] = Math.max(maxValues[i], row[i]);
            }
        }
    }

    private double[] normalize(double[] raw) {
        double[] scaled = new double[FEATURE_COUNT];
        for (int i = 0; i < FEATURE_COUNT; i++) {
            double range = maxValues[i] - minValues[i];
            scaled[i] = range <= 1e-9 ? 0.0 : (raw[i] - minValues[i]) / range;
        }
        return scaled;
    }

    private double[][] seedCentroids(List<double[]> data, int k) {
        double[][] seeds = new double[k][FEATURE_COUNT];
        List<double[]> shuffled = new ArrayList<>(data);
        java.util.Collections.shuffle(shuffled, rng);
        for (int i = 0; i < k; i++) {
            seeds[i] = Arrays.copyOf(shuffled.get(i), FEATURE_COUNT);
        }
        return seeds;
    }

    private boolean assignClusters(List<double[]> data, int[] assignments) {
        boolean changed = false;
        for (int i = 0; i < data.size(); i++) {
            int cluster = nearestCentroid(data.get(i));
            if (assignments[i] != cluster) {
                assignments[i] = cluster;
                changed = true;
            }
        }
        return changed;
    }

    private void recomputeCentroids(List<double[]> data, int[] assignments, int k) {
        double[][] sums = new double[k][FEATURE_COUNT];
        int[] counts = new int[k];

        for (int i = 0; i < data.size(); i++) {
            int cluster = assignments[i];
            if (cluster < 0) continue;
            counts[cluster]++;
            for (int j = 0; j < FEATURE_COUNT; j++) {
                sums[cluster][j] += data.get(i)[j];
            }
        }

        for (int cluster = 0; cluster < k; cluster++) {
            if (counts[cluster] == 0) continue;
            for (int j = 0; j < FEATURE_COUNT; j++) {
                centroids[cluster][j] = sums[cluster][j] / counts[cluster];
            }
        }
    }

    private int nearestCentroid(double[] vector) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < centroids.length; i++) {
            double distance = squaredDistance(vector, centroids[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private double squaredDistance(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < FEATURE_COUNT; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return sum;
    }

    private Profile[] classifyProfiles(double[][] centers) {
        Profile[] result = new Profile[centers.length];
        for (int i = 0; i < centers.length; i++) {
            double urgencyScore = 0.55 * (1.0 - centers[i][3]) + 0.45 * centers[i][4];
            double heavyScore = 0.40 * centers[i][0] + 0.30 * centers[i][1] + 0.30 * centers[i][2];

            if (urgencyScore >= heavyScore && urgencyScore > 0.45) {
                result[i] = Profile.LATENCY_SENSITIVE;
            } else if (heavyScore > urgencyScore && heavyScore > 0.45) {
                result[i] = Profile.RESOURCE_HEAVY;
            } else {
                result[i] = Profile.BALANCED;
            }
        }
        return result;
    }
}