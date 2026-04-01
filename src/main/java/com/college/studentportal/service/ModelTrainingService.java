package com.college.studentportal.service;

import com.college.studentportal.model.Result;
import com.college.studentportal.model.Student;
import com.college.studentportal.repository.ResultRepository;
import com.college.studentportal.repository.StudentRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Background service that trains AI models on a schedule and provides
 * pre-trained model access to the prediction service.
 * 
 * Models are trained:
 * - On application startup (@PostConstruct)
 * - Every 6 hours via @Scheduled
 * 
 * Trained models are kept in memory (volatile references for thread safety)
 * and also serialized to disk for persistence across restarts.
 */
@Service
public class ModelTrainingService {

    private static final Logger logger = LoggerFactory.getLogger(ModelTrainingService.class);

    private final ResultRepository resultRepository;
    private final StudentRepository studentRepository;
    private final AttendanceService attendanceService;

    @Value("${ai.model.directory:./models}")
    private String modelDirectory;

    // Thread-safe model references
    private volatile RandomForest cgpaModel;
    private volatile Instances cgpaDatasetHeader;
    private volatile RandomForest sgpaModel;
    private volatile Instances sgpaDatasetHeader;

    // Track last training time for monitoring
    private volatile long lastTrainingTimestamp = 0;

    public ModelTrainingService(ResultRepository resultRepository,
                                StudentRepository studentRepository,
                                AttendanceService attendanceService) {
        this.resultRepository = resultRepository;
        this.studentRepository = studentRepository;
        this.attendanceService = attendanceService;
    }

    @PostConstruct
    public void init() {
        // Ensure model directory exists
        try {
            Files.createDirectories(Paths.get(modelDirectory));
        } catch (IOException e) {
            logger.warn("Could not create model directory: {}", modelDirectory, e);
        }

        // Try to load existing models from disk first (fast startup)
        boolean loaded = loadModelsFromDisk();
        if (!loaded) {
            logger.info("No pre-trained models found on disk. Training will begin shortly.");
        }

        // Schedule initial training in a background thread to not block startup
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait 5 seconds for app to fully initialize
                trainAllModels();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "AI-Model-Init-Trainer").start();
    }

    /**
     * Retrain all models every 6 hours.
     */
    @Scheduled(fixedRate = 6 * 60 * 60 * 1000, initialDelay = 6 * 60 * 60 * 1000)
    public void scheduledTraining() {
        logger.info("Scheduled AI model retraining triggered.");
        trainAllModels();
    }

    /**
     * Train both CGPA and SGPA models.
     */
    public synchronized void trainAllModels() {
        try {
            logger.info("Starting AI model training...");
            long start = System.currentTimeMillis();

            trainCGPAModel();
            trainSGPAModel();

            lastTrainingTimestamp = System.currentTimeMillis();
            long elapsed = lastTrainingTimestamp - start;
            logger.info("AI model training completed in {}ms.", elapsed);

        } catch (Exception e) {
            logger.error("Error during AI model training", e);
        }
    }

    // ======================== CGPA MODEL ========================

    private void trainCGPAModel() throws Exception {
        int requiredSemesters = 4;
        Instances dataset = buildDataset(requiredSemesters, "finalCgpa");
        populateTrainingData(dataset, requiredSemesters, true);

        if (dataset.numInstances() < 5) {
            addHardcodedCGPATrainingData(dataset);
        }

        if (dataset.numInstances() == 0) {
            logger.warn("No training data for CGPA model. Skipping.");
            return;
        }

        RandomForest model = new RandomForest();
        model.setNumIterations(100);
        model.buildClassifier(dataset);

        // Atomic swap
        this.cgpaModel = model;
        this.cgpaDatasetHeader = new Instances(dataset, 0); // Header only

        // Persist to disk
        saveModelToDisk(model, dataset, "cgpa_model.ser");
        logger.info("CGPA model trained with {} instances.", dataset.numInstances());
    }

    // ======================== SGPA MODEL ========================

    private void trainSGPAModel() throws Exception {
        // Train models for varying numbers of input semesters (1-7)
        // We'll train the most common case: 4 semesters
        int numFeatures = 4;
        Instances dataset = buildDataset(numFeatures, "nextSgpa");
        populateTrainingData(dataset, numFeatures, false);

        if (dataset.numInstances() < 5) {
            addSyntheticSGPAData(dataset, numFeatures);
        }

        if (dataset.numInstances() == 0) {
            logger.warn("No training data for SGPA model. Skipping.");
            return;
        }

        RandomForest model = new RandomForest();
        model.setNumIterations(50);
        model.buildClassifier(dataset);

        // Atomic swap
        this.sgpaModel = model;
        this.sgpaDatasetHeader = new Instances(dataset, 0);

        saveModelToDisk(model, dataset, "sgpa_model.ser");
        logger.info("SGPA model trained with {} instances.", dataset.numInstances());
    }

    // ======================== DATASET BUILDING ========================

    private Instances buildDataset(int numSemesters, String className) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        for (int i = 1; i <= numSemesters; i++) {
            attributes.add(new Attribute("sgpa" + i));
        }
        attributes.add(new Attribute("backlogCount"));
        attributes.add(new Attribute("internalConsistency"));
        attributes.add(new Attribute("attendancePercentage"));
        attributes.add(new Attribute(className));
        Instances dataset = new Instances("StudentData", attributes, 0);
        dataset.setClassIndex(numSemesters + 3);
        return dataset;
    }

    private void populateTrainingData(Instances dataset, int requiredSemesters, boolean predictingCGPA) {
        List<Student> students = studentRepository.findAll();

        for (Student student : students) {
            List<Result> results = resultRepository.findByStudentId(student.getId());
            if (results.isEmpty()) continue;

            Map<Integer, List<Result>> semesterResults = new HashMap<>();
            double totalWeightedScore = 0;
            int totalCredits = 0;
            int backlogs = 0;
            double internalTotal = 0;
            double externalTotal = 0;

            for (Result r : results) {
                int sem = r.getSemester();
                semesterResults.computeIfAbsent(sem, k -> new ArrayList<>()).add(r);
                totalWeightedScore += r.getGradePoint() * r.getCredits();
                totalCredits += r.getCredits();
                if (r.getGradePoint() < 5) backlogs++;
                internalTotal += r.getInternalMarks();
                externalTotal += r.getExternalMarks();
            }

            if (semesterResults.size() >= requiredSemesters) {
                double[] features = new double[requiredSemesters + 4];
                for (int i = 1; i <= requiredSemesters; i++) {
                    List<Result> semRes = semesterResults.get(i);
                    if (semRes == null) {
                        features[i - 1] = 7.0;
                    } else {
                        double semScore = 0;
                        int semCreds = 0;
                        for (Result r : semRes) {
                            semScore += r.getGradePoint() * r.getCredits();
                            semCreds += r.getCredits();
                        }
                        features[i - 1] = semCreds == 0 ? 0 : semScore / semCreds;
                    }
                }

                features[requiredSemesters] = backlogs;
                features[requiredSemesters + 1] = (internalTotal + externalTotal == 0) ? 0 : internalTotal / (internalTotal + externalTotal);
                features[requiredSemesters + 2] = attendanceService.getOverallAttendancePercentage(student.getId());

                if (predictingCGPA) {
                    features[requiredSemesters + 3] = totalCredits == 0 ? 0 : totalWeightedScore / totalCredits;
                } else {
                    List<Result> nextSemRes = semesterResults.get(requiredSemesters + 1);
                    if (nextSemRes != null) {
                        double nextScore = 0;
                        int nextCreds = 0;
                        for (Result r : nextSemRes) {
                            nextScore += r.getGradePoint() * r.getCredits();
                            nextCreds += r.getCredits();
                        }
                        features[requiredSemesters + 3] = nextCreds == 0 ? 0 : nextScore / nextCreds;
                    } else {
                        features[requiredSemesters + 3] = features[requiredSemesters - 1];
                    }
                }

                dataset.add(new DenseInstance(1.0, features));
            }
        }
    }

    private void addHardcodedCGPATrainingData(Instances dataset) {
        dataset.add(new DenseInstance(1.0, new double[]{6.5, 6.8, 7.0, 7.2, 2, 0.35, 75, 7.0}));
        dataset.add(new DenseInstance(1.0, new double[]{7.0, 7.2, 7.5, 7.7, 0, 0.40, 85, 7.4}));
        dataset.add(new DenseInstance(1.0, new double[]{7.5, 7.8, 8.0, 8.2, 0, 0.42, 90, 7.9}));
        dataset.add(new DenseInstance(1.0, new double[]{8.0, 8.2, 8.4, 8.5, 0, 0.45, 95, 8.3}));
        dataset.add(new DenseInstance(1.0, new double[]{6.8, 7.0, 7.1, 7.3, 1, 0.38, 80, 7.05}));
        dataset.add(new DenseInstance(1.0, new double[]{7.2, 7.4, 7.6, 7.8, 0, 0.39, 88, 7.5}));
        dataset.add(new DenseInstance(1.0, new double[]{7.8, 8.0, 8.2, 8.4, 0, 0.41, 92, 8.1}));
        dataset.add(new DenseInstance(1.0, new double[]{8.5, 8.8, 9.0, 9.2, 0, 0.44, 98, 8.9}));
        dataset.add(new DenseInstance(1.0, new double[]{9.0, 9.2, 9.3, 9.5, 0, 0.46, 99, 9.25}));
        dataset.add(new DenseInstance(1.0, new double[]{6.0, 6.2, 6.5, 6.8, 3, 0.30, 65, 6.4}));
    }

    private void addSyntheticSGPAData(Instances dataset, int numFeatures) {
        for (int i = 0; i < 10; i++) {
            double[] synthetic = new double[numFeatures + 4];
            double base = 6.0 + (i * 0.3);
            for (int j = 0; j < numFeatures; j++) {
                synthetic[j] = base + (j * 0.1);
            }
            synthetic[numFeatures] = 0;
            synthetic[numFeatures + 1] = 0.40;
            synthetic[numFeatures + 2] = 85.0;
            synthetic[numFeatures + 3] = base + (numFeatures * 0.1);
            dataset.add(new DenseInstance(1.0, synthetic));
        }
    }

    // ======================== DISK PERSISTENCE ========================

    private void saveModelToDisk(RandomForest model, Instances dataset, String filename) {
        Path path = Paths.get(modelDirectory, filename);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path.toFile()))) {
            oos.writeObject(model);
            oos.writeObject(new Instances(dataset, 0)); // Save header only
            logger.info("Model saved to disk: {}", path);
        } catch (IOException e) {
            logger.warn("Could not save model to disk: {}", path, e);
        }
    }

    private boolean loadModelsFromDisk() {
        boolean loaded = false;

        Path cgpaPath = Paths.get(modelDirectory, "cgpa_model.ser");
        if (Files.exists(cgpaPath)) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cgpaPath.toFile()))) {
                this.cgpaModel = (RandomForest) ois.readObject();
                this.cgpaDatasetHeader = (Instances) ois.readObject();
                logger.info("Loaded CGPA model from disk.");
                loaded = true;
            } catch (Exception e) {
                logger.warn("Could not load CGPA model from disk", e);
            }
        }

        Path sgpaPath = Paths.get(modelDirectory, "sgpa_model.ser");
        if (Files.exists(sgpaPath)) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(sgpaPath.toFile()))) {
                this.sgpaModel = (RandomForest) ois.readObject();
                this.sgpaDatasetHeader = (Instances) ois.readObject();
                logger.info("Loaded SGPA model from disk.");
                loaded = true;
            } catch (Exception e) {
                logger.warn("Could not load SGPA model from disk", e);
            }
        }

        return loaded;
    }

    // ======================== PUBLIC ACCESSORS ========================

    public RandomForest getCgpaModel() {
        return cgpaModel;
    }

    public Instances getCgpaDatasetHeader() {
        return cgpaDatasetHeader;
    }

    public RandomForest getSgpaModel() {
        return sgpaModel;
    }

    public Instances getSgpaDatasetHeader() {
        return sgpaDatasetHeader;
    }

    public boolean isCgpaModelReady() {
        return cgpaModel != null && cgpaDatasetHeader != null;
    }

    public boolean isSgpaModelReady() {
        return sgpaModel != null && sgpaDatasetHeader != null;
    }

    public long getLastTrainingTimestamp() {
        return lastTrainingTimestamp;
    }
}
