package com.dizertatie.dataset;

import com.dizertatie.config.SimulationConfig;
import com.dizertatie.model.TaskRecord;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.*;
import java.util.*;

/**
 * Loads and parses synthetic_cloud_workload_10000.csv into TaskRecord objects.
 * Tries classpath first, then the filesystem working directory.
 */
public class DatasetLoader {

    /**
     * Load all tasks from the configured dataset path.
     * Returns tasks sorted by arrival time ascending.
     */
    public List<TaskRecord> load() throws IOException {
        return load(SimulationConfig.DATASET_PATH);
    }

    public List<TaskRecord> load(String path) throws IOException {
        List<TaskRecord> records = new ArrayList<>();

        try (Reader reader = openReader(path);
             CSVReader csv   = new CSVReader(reader)) {

            List<String[]> rows;
            try {
                rows = csv.readAll();
            } catch (CsvException e) {
                throw new IOException("CSV parse error: " + e.getMessage(), e);
            }

            if (rows.isEmpty()) throw new IOException("Dataset file is empty: " + path);

            // Detect header row
            String[] header = rows.get(0);
            Map<String, Integer> idx = buildIndex(header);

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < header.length) continue; // skip malformed

                try {
                    TaskRecord rec = parse(row, idx, i);
                    records.add(rec);
                } catch (NumberFormatException e) {
                    System.err.println("Skipping malformed row " + i + ": " + e.getMessage());
                }
            }
        }

        // Sort by arrival time so the injector can process them in order
        records.sort(Comparator.comparingDouble(TaskRecord::getArrivalTimeSec));
        System.out.printf("[DatasetLoader] Loaded %d tasks from '%s'%n", records.size(), path);
        return records;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TaskRecord parse(String[] row, Map<String, Integer> idx, int lineNo) {
        int    id             = get(row, idx, "id", lineNo) != null
                                    ? Integer.parseInt(clean(get(row, idx, "id", lineNo))) : lineNo;
        double arrivalTime    = Double.parseDouble(clean(require(row, idx, "arrival_time_sec", lineNo)));
        long   lengthMi       = (long) Double.parseDouble(clean(require(row, idx, "length_mi", lineNo)));
        int    cpuCores       = (int)  Double.parseDouble(clean(require(row, idx, "cpu_cores", lineNo)));
        long   ramMb          = (long) Double.parseDouble(clean(require(row, idx, "ram_mb", lineNo)));
        double deadlineSec    = Double.parseDouble(clean(require(row, idx, "deadline_sec", lineNo)));
        // deadline_rel_sec: relative slack from arrival; fall back to (deadlineSec - arrivalTime)
        String relRaw         = get(row, idx, "deadline_rel_sec", lineNo);
        double deadlineRelSec = (relRaw != null && !relRaw.isBlank())
                                    ? Double.parseDouble(clean(relRaw))
                                    : Math.max(0.0, deadlineSec - arrivalTime);
        int    priority       = (int)  Double.parseDouble(clean(require(row, idx, "priority", lineNo)));
        String type           = clean(require(row, idx, "type", lineNo)).toUpperCase();
        String dataRegion     = clean(require(row, idx, "data_region", lineNo)).toUpperCase();
        double dataSizeMb     = Double.parseDouble(clean(require(row, idx, "data_size_mb", lineNo)));

        // Guard: length must be > 0 for CloudSim
        if (lengthMi <= 0) lengthMi = 1;
        if (cpuCores <= 0) cpuCores = 1;

        return new TaskRecord(id, arrivalTime, lengthMi, cpuCores,
                              ramMb, deadlineSec, deadlineRelSec, priority, type, dataRegion, dataSizeMb);
    }

    private Map<String, Integer> buildIndex(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            map.put(clean(header[i]).toLowerCase(), i);
        }
        return map;
    }

    private String require(String[] row, Map<String, Integer> idx, String col, int lineNo) {
        Integer i = idx.get(col);
        if (i == null) throw new NumberFormatException("Missing column '" + col + "' at line " + lineNo);
        return row[i];
    }

    private String get(String[] row, Map<String, Integer> idx, String col, int lineNo) {
        Integer i = idx.get(col);
        if (i == null || i >= row.length) return null;
        return row[i];
    }

    /** Remove BOM, quotes, and surrounding whitespace. */
    private String clean(String s) {
        if (s == null) return "";
        return s.replace("\uFEFF", "").replace("\"", "").trim();
    }

    private Reader openReader(String path) throws IOException {
        // 1. Try as classpath resource
        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
        if (is != null) return new InputStreamReader(is);

        // 2. Try filesystem (working directory)
        File f = new File(path);
        if (f.exists()) return new FileReader(f);

        throw new FileNotFoundException("Dataset not found at classpath or filesystem: " + path);
    }
}
