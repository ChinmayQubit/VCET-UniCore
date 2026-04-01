package com.college.studentportal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import weka.classifiers.trees.RandomForest;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.List;

/**
 * AI Prediction Service — now uses pre-trained models from ModelTrainingService
 * instead of building classifiers during each request.
 * 
 * If a pre-trained model is not available (e.g., first startup before training completes),
 * falls back to a simple weighted-average estimation.
 */
@Service
public class AIPredictionService {

    private static final Logger logger = LoggerFactory.getLogger(AIPredictionService.class);

    private final ModelTrainingService modelTrainingService;

    public AIPredictionService(ModelTrainingService modelTrainingService) {
        this.modelTrainingService = modelTrainingService;
    }

    /**
     * Predict final CGPA using the pre-trained CGPA model.
     * Falls back to a weighted average if the model isn't ready yet.
     */
    public double predictCGPA(double sgpa1, double sgpa2, double sgpa3, double sgpa4,
                              int backlogCount, double internalConsistency, double attendance) throws Exception {

        if (!modelTrainingService.isCgpaModelReady()) {
            logger.warn("CGPA model not ready yet, using fallback estimation.");
            return fallbackCGPAEstimate(sgpa1, sgpa2, sgpa3, sgpa4, backlogCount);
        }

        RandomForest model = modelTrainingService.getCgpaModel();
        Instances header = modelTrainingService.getCgpaDatasetHeader();

        DenseInstance newStudent = new DenseInstance(8);
        newStudent.setDataset(header);
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

    /**
     * Predict next semester SGPA using the pre-trained SGPA model.
     * Falls back to a trend-based estimate if the model isn't ready.
     */
    public double predictNextSGPA(List<Double> sgpas, int backlogCount,
                                   double internalConsistency, double attendance) throws Exception {

        int numFeatures = sgpas.size();
        if (numFeatures == 0) return 7.0;

        if (!modelTrainingService.isSgpaModelReady()) {
            logger.warn("SGPA model not ready yet, using fallback estimation.");
            return fallbackSGPAEstimate(sgpas, backlogCount);
        }

        RandomForest model = modelTrainingService.getSgpaModel();
        Instances header = modelTrainingService.getSgpaDatasetHeader();

        // If the pre-trained model has a different number of features than we need,
        // build a temporary model on the fly (rare edge case)
        int modelFeatures = header.numAttributes() - 4; // subtract backlog, internal, attendance, class
        if (modelFeatures != numFeatures) {
            logger.info("SGPA model feature mismatch (model={}, need={}). Using fallback.", modelFeatures, numFeatures);
            return fallbackSGPAEstimate(sgpas, backlogCount);
        }

        DenseInstance instance = new DenseInstance(numFeatures + 4);
        instance.setDataset(header);
        for (int i = 0; i < numFeatures; i++) {
            instance.setValue(i, sgpas.get(i));
        }
        instance.setValue(numFeatures, backlogCount);
        instance.setValue(numFeatures + 1, internalConsistency);
        instance.setValue(numFeatures + 2, attendance);

        double prediction = model.classifyInstance(instance);
        return Math.round(prediction * 100.0) / 100.0;
    }

    // ======================== FALLBACK ESTIMATIONS ========================

    /**
     * Simple weighted-average fallback when the AI model isn't trained yet.
     * Uses a trend line approach with backlog penalty.
     */
    private double fallbackCGPAEstimate(double sgpa1, double sgpa2, double sgpa3, double sgpa4, int backlogCount) {
        // More recent semesters get higher weight
        double weightedAvg = (sgpa1 * 0.15 + sgpa2 * 0.20 + sgpa3 * 0.30 + sgpa4 * 0.35);
        // Penalty for backlogs
        double penalty = backlogCount * 0.15;
        double estimate = Math.max(0, Math.min(10, weightedAvg - penalty));
        return Math.round(estimate * 100.0) / 100.0;
    }

    /**
     * Simple trend-based fallback for next-semester SGPA prediction.
     */
    private double fallbackSGPAEstimate(List<Double> sgpas, int backlogCount) {
        if (sgpas.isEmpty()) return 7.0;
        if (sgpas.size() == 1) return sgpas.get(0);

        // Calculate trend: difference between last two semesters
        double last = sgpas.get(sgpas.size() - 1);
        double secondLast = sgpas.get(sgpas.size() - 2);
        double trend = last - secondLast;

        // Dampen trend (don't extrapolate too aggressively)
        double predicted = last + (trend * 0.5);
        double penalty = backlogCount * 0.1;
        predicted = Math.max(0, Math.min(10, predicted - penalty));
        return Math.round(predicted * 100.0) / 100.0;
    }
}