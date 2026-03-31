package com.college.studentportal.service;

import com.college.studentportal.model.Result;
import com.college.studentportal.model.Student;
import com.college.studentportal.repository.ResultRepository;
import com.college.studentportal.repository.StudentRepository;
import org.springframework.stereotype.Service;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIPredictionService {

    private final ResultRepository resultRepository;
    private final StudentRepository studentRepository;
    private final AttendanceService attendanceService;

    public AIPredictionService(ResultRepository resultRepository, 
                               StudentRepository studentRepository,
                               AttendanceService attendanceService) {
        this.resultRepository = resultRepository;
        this.studentRepository = studentRepository;
        this.attendanceService = attendanceService;
    }

    private Instances buildStudentDataset(int numSemesters, String className) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        for (int i = 1; i <= numSemesters; i++) {
            attributes.add(new Attribute("sgpa" + i));
        }
        attributes.add(new Attribute("backlogCount"));
        attributes.add(new Attribute("internalConsistency"));
        attributes.add(new Attribute("attendancePercentage"));
        attributes.add(new Attribute(className));
        Instances dataset = new Instances("StudentData", attributes, 0);
        dataset.setClassIndex(numSemesters + 3); // Num semesters + 3 features
        return dataset;
    }

    private void addHardcodedTrainingData(Instances dataset) {
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

    private void populateDynamicTrainingData(Instances dataset, int requiredSemesters, boolean predictingCGPA) {
        List<Student> students = studentRepository.findAll();
        int addedCount = 0;

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
                double[] features = new double[requiredSemesters + 4]; // +3 for new features, +1 for target class
                for (int i = 1; i <= requiredSemesters; i++) {
                    List<Result> semRes = semesterResults.get(i);
                    if (semRes == null) {
                        features[i - 1] = 7.0; // fallback avg
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
                        features[requiredSemesters + 3] = features[requiredSemesters - 1]; // fallback to last
                    }
                }

                dataset.add(new DenseInstance(1.0, features));
                addedCount++;
            }
        }

        if (addedCount < 5 && requiredSemesters == 4 && predictingCGPA) {
            addHardcodedTrainingData(dataset);
        }
    }

    public double predictCGPA(double sgpa1, double sgpa2, double sgpa3, double sgpa4, int backlogCount, double internalConsistency, double attendance) throws Exception {
        Instances dataset = buildStudentDataset(4, "finalCgpa");
        populateDynamicTrainingData(dataset, 4, true);

        RandomForest model = new RandomForest();
        model.setNumIterations(100);
        model.buildClassifier(dataset);

        DenseInstance newStudent = new DenseInstance(8);
        newStudent.setDataset(dataset);
        newStudent.setValue(0, sgpa1);
        newStudent.setValue(1, sgpa2);
        newStudent.setValue(2, sgpa3);
        newStudent.setValue(3, sgpa4);
        newStudent.setValue(4, backlogCount);
        newStudent.setValue(5, internalConsistency);
        newStudent.setValue(6, attendance);

        double predicted = model.classifyInstance(newStudent);
        return Math.round(predicted * 100.0) / 100.0;
    }

    public double predictNextSGPA(List<Double> sgpas, int backlogCount, double internalConsistency, double attendance) throws Exception {
        int numFeatures = sgpas.size();
        if (numFeatures == 0) return 7.0;

        Instances dataset = buildStudentDataset(numFeatures, "nextSgpa");
        populateDynamicTrainingData(dataset, numFeatures, false);

        if (dataset.numInstances() < 5) {
            for (int i = 0; i < 10; i++) {
                double[] synthetic = new double[numFeatures + 4];
                double base = 6.0 + (i * 0.3);
                for (int j = 0; j < numFeatures; j++) {
                    synthetic[j] = base + (j * 0.1);
                }
                synthetic[numFeatures] = 0; // fake backlog
                synthetic[numFeatures + 1] = 0.40; // fake internal consistency
                synthetic[numFeatures + 2] = 85.0; // fake attendance
                synthetic[numFeatures + 3] = base + (numFeatures * 0.1); // target class
                dataset.add(new DenseInstance(1.0, synthetic));
            }
        }

        RandomForest model = new RandomForest();
        model.setNumIterations(50);
        model.buildClassifier(dataset);

        DenseInstance instance = new DenseInstance(numFeatures + 4);
        instance.setDataset(dataset);
        for (int i = 0; i < numFeatures; i++) {
            instance.setValue(i, sgpas.get(i));
        }
        instance.setValue(numFeatures, backlogCount);
        instance.setValue(numFeatures + 1, internalConsistency);
        instance.setValue(numFeatures + 2, attendance);

        double prediction = model.classifyInstance(instance);
        return Math.round(prediction * 100.0) / 100.0;
    }
}