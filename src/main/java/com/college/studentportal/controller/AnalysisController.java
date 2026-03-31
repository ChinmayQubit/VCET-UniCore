package com.college.studentportal.controller;
import com.college.studentportal.service.AIPredictionService;
import com.college.studentportal.service.AcademicAnalyticsService;

import com.college.studentportal.model.Result;
import com.college.studentportal.repository.ResultRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final ResultRepository resultRepository;
    private final AIPredictionService aiPredictionService;
    private final AcademicAnalyticsService analyticsService;

    public AnalysisController(ResultRepository resultRepository,
                              AcademicAnalyticsService analyticsService,
                              AIPredictionService aiPredictionService) {

        this.resultRepository = resultRepository;
        this.analyticsService = analyticsService;
        this.aiPredictionService = aiPredictionService;
    }

    @GetMapping("/trend/{studentId}")
    public Map<String, Object> getPerformanceTrend(@PathVariable Long studentId) {

        List<Result> results = resultRepository.findByStudentId(studentId);

        Map<Integer, List<Result>> semesterResults = new HashMap<>();

        for (Result result : results) {
            int semester = result.getSemester();

            semesterResults
                    .computeIfAbsent(semester, k -> new ArrayList<>())
                    .add(result);
        }

        Map<Integer, Double> semesterSgpa = new TreeMap<>();

        for (Map.Entry<Integer, List<Result>> entry : semesterResults.entrySet()) {

            double totalWeightedScore = 0;
            int totalCredits = 0;

            for (Result r : entry.getValue()) {
                double grade = r.getGradePoint();
                int credits = r.getCredits();

                totalWeightedScore += grade * credits;
                totalCredits += credits;
            }

            double sgpa = totalCredits == 0 ? 0 : totalWeightedScore / totalCredits;

            semesterSgpa.put(entry.getKey(), Math.round(sgpa * 100.0) / 100.0);
        }

        String trend = "Stable";

        if (semesterSgpa.size() >= 2) {
            List<Double> values = new ArrayList<>(semesterSgpa.values());

            double first = values.get(0);
            double last = values.get(values.size() - 1);

            if (last > first) trend = "Improving";
            else if (last < first) trend = "Declining";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("semesterSgpa", semesterSgpa);
        response.put("trend", trend);

        return response;
    }

    @GetMapping("/placement/{studentId}")
    public Map<String, Object> analyzePlacementEligibility(@PathVariable Long studentId) {

        List<Result> results = resultRepository.findByStudentId(studentId);

        double totalWeightedScore = 0;
        int totalCredits = 0;

        for (Result result : results) {
            double grade = result.getGradePoint();
            int credits = result.getCredits();

            totalWeightedScore += grade * credits;
            totalCredits += credits;
        }

        double cgpa = totalCredits == 0 ? 0 : totalWeightedScore / totalCredits;
        cgpa = Math.round(cgpa * 100.0) / 100.0;

        String placementStatus;
        String guidance;

        if (cgpa >= 8) {
            placementStatus = "Eligible for most companies including product companies";
            guidance = "Maintain your CGPA above 8 to keep maximum placement opportunities.";
        }
        else if (cgpa >= 7) {
            placementStatus = "Eligible for many service-based companies";
            guidance = "Aim for SGPA 8+ next semester to reach CGPA 8 and unlock more opportunities.";
        }
        else if (cgpa >= 6) {
            placementStatus = "Limited placement opportunities";
            guidance = "Work towards SGPA 8+ next semester to improve your CGPA.";
        }
        else {
            placementStatus = "Not eligible for most campus placements";
            guidance = "Focus on improving fundamentals and target SGPA above 8 in upcoming semesters.";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("currentCGPA", cgpa);
        response.put("placementStatus", placementStatus);
        response.put("guidance", guidance);

        return response;
    }

    @GetMapping("/target/{studentId}")
    public Map<String,Object> calculateTarget(@PathVariable Long studentId) throws Exception {

        List<Result> results = resultRepository.findByStudentId(studentId);
        AcademicAnalyticsService.StudentMetrics metrics = analyticsService.calculateAdvancedMetrics(results);
        int backlogs = metrics.backlogCount();
        double internalConsistency = metrics.internalConsistency();
        // Get semester SGPA from analytics service
        Map<Integer,Double> semesterSgpa =
                analyticsService.calculateSemesterSGPA(studentId);

        List<Double> sgpas = new ArrayList<>(semesterSgpa.values());

        // If less semesters exist, fill with average
        double avg = sgpas.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(7.0);

        while(sgpas.size() < 7){
            sgpas.add(avg);
        }

        // AI prediction
        double predictedNextSGPA =
                aiPredictionService.predictNextSGPA(sgpas, backlogs, internalConsistency, metrics.attendancePercentage());

        Map<String,Object> response = new HashMap<>();

        response.put("aiTargetSGPA", predictedNextSGPA);
        response.put("message",
                "AI predicted achievable SGPA for next semester based on academic trend");

        return response;
    }
    @GetMapping("/advice/{studentId}")
    public Map<String, Object> getAcademicAdvice(@PathVariable Long studentId) {

        List<Result> results = resultRepository.findByStudentId(studentId);

        double totalWeightedScore = 0;
        int totalCredits = 0;

        Map<Integer, List<Result>> semesterResults = new HashMap<>();

        for (Result result : results) {

            double grade = result.getGradePoint();
            int credits = result.getCredits();

            totalWeightedScore += grade * credits;
            totalCredits += credits;

            int semester = result.getSemester();

            semesterResults
                    .computeIfAbsent(semester, k -> new ArrayList<>())
                    .add(result);
        }

        double cgpa = totalCredits == 0 ? 0 : totalWeightedScore / totalCredits;

        List<Double> sgpaList = new ArrayList<>();

        for (Map.Entry<Integer, List<Result>> entry : semesterResults.entrySet()) {

            double semScore = 0;
            int semCredits = 0;

            for (Result r : entry.getValue()) {
                semScore += r.getGradePoint() * r.getCredits();
                semCredits += r.getCredits();
            }

            sgpaList.add(semScore / semCredits);
        }

        String trend = "Stable";

        if (sgpaList.size() >= 2) {

            double first = sgpaList.get(0);
            double last = sgpaList.get(sgpaList.size() - 1);

            if (last > first) trend = "Improving";
            else if (last < first) trend = "Declining";
        }

        String advice;

        if (cgpa >= 8) {
            advice = "Great performance! Maintain your CGPA above 8 to stay eligible for most companies.";
        }
        else if (cgpa >= 7) {
            advice = "You are close to strong placement eligibility. Target SGPA above 8 next semester.";
        }
        else if (cgpa >= 6) {
            advice = "Your CGPA needs improvement. Focus on scoring SGPA above 8 in upcoming semesters.";
        }
        else {
            advice = "Your CGPA is below placement eligibility. Improve study strategy and target SGPA above 8.5.";
        }

        if (trend.equals("Declining")) {
            advice += " Your performance trend is declining. Review difficult subjects and improve consistency.";
        }

        Map<String, Object> response = new HashMap<>();

        response.put("cgpa", Math.round(cgpa * 100.0) / 100.0);
        response.put("trend", trend);
        response.put("advice", advice);

        return response;
    }

    @GetMapping("/companies/{studentId}")
    public Map<String, Object> getCompanyEligibility(@PathVariable Long studentId) {

        List<Result> results = resultRepository.findByStudentId(studentId);

        double totalWeightedScore = 0;
        int totalCredits = 0;

        for (Result r : results) {
            totalWeightedScore += r.getGradePoint() * r.getCredits();
            totalCredits += r.getCredits();
        }

        double cgpa = totalCredits == 0 ? 0 : totalWeightedScore / totalCredits;
        cgpa = Math.round(cgpa * 100.0) / 100.0;

        List<String> eligible = new ArrayList<>();
        List<String> unlock = new ArrayList<>();

        if (cgpa >= 8) {
            eligible = List.of("TCS", "Infosys", "Capgemini", "Accenture", "Product Companies");
        }
        else if (cgpa >= 7.8) {
            eligible = List.of("TCS", "Infosys", "Capgemini", "Accenture");
            unlock = List.of("Product Companies");
        }
        else if (cgpa >= 7) {
            eligible = List.of("TCS", "Infosys");
            unlock = List.of("Capgemini", "Accenture");
        }
        else {
            unlock = List.of("TCS", "Infosys", "Capgemini", "Accenture");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("cgpa", cgpa);
        response.put("eligibleCompanies", eligible);
        response.put("nextLevelCompanies", unlock);

        return response;
    }

    @GetMapping("/readiness/{studentId}")
    public Map<String, Object> getPlacementReadiness(@PathVariable Long studentId) {

        List<Result> results = resultRepository.findByStudentId(studentId);

        double totalWeightedScore = 0;
        int totalCredits = 0;

        for (Result r : results) {
            totalWeightedScore += r.getGradePoint() * r.getCredits();
            totalCredits += r.getCredits();
        }

        double cgpa = totalCredits == 0 ? 0 : totalWeightedScore / totalCredits;
        cgpa = Math.round(cgpa * 100.0) / 100.0;

        int score = 0;

        // CGPA contribution (60%)
        if (cgpa >= 8) score += 60;
        else if (cgpa >= 7) score += 45;
        else if (cgpa >= 6) score += 30;
        else score += 15;

        // Simple trend check
        List<Double> sgpas = new ArrayList<>();
        for (Result r : results) {
            sgpas.add(r.getGradePoint());
        }

        if (sgpas.size() >= 2) {
            double last = sgpas.get(sgpas.size()-1);
            double prev = sgpas.get(sgpas.size()-2);

            if (last > prev) score += 25;
            else if (last == prev) score += 15;
            else score += 10;
        }

        // Consistency bonus
        score += 15;

        // Subject level penalty
        Map<String, List<String>> insights = analyticsService.getSubjectInsights(studentId);
        int weakCount = insights.containsKey("weak") ? insights.get("weak").size() : 0;
        if (weakCount > 2) {
            score -= 10;
        } else if (weakCount > 0) {
            score -= 5;
        }

        String level;

        if (score >= 80) level = "Highly Competitive";
        else if (score >= 60) level = "Placement Ready";
        else if (score >= 40) level = "Needs Improvement";
        else level = "At Risk";

        Map<String,Object> response = new HashMap<>();
        response.put("readinessScore", score);
        response.put("cgpa", cgpa);
        response.put("level", level);

        return response;
    }

    @GetMapping("/ai-cgpa/{studentId}")
    public Map<String,Object> predictCgpa(@PathVariable Long studentId) throws Exception {

        List<Result> results = resultRepository.findByStudentId(studentId);

        // Group results by semester
        Map<Integer,List<Result>> semesterResults = new HashMap<>();

        for(Result r : results){
            semesterResults
                    .computeIfAbsent(r.getSemester(),k->new ArrayList<>())
                    .add(r);
        }
        
        AcademicAnalyticsService.StudentMetrics metrics = analyticsService.calculateAdvancedMetrics(results);
        int backlogs = metrics.backlogCount();
        double internalConsistency = metrics.internalConsistency();

        // Calculate SGPA for each semester
        List<Double> sgpas = new ArrayList<>();

        for(List<Result> semResults : semesterResults.values()){

            double weightedScore = 0;
            int credits = 0;

            for(Result r : semResults){
                weightedScore += r.getGradePoint()*r.getCredits();
                credits += r.getCredits();
            }

            double sgpa = credits==0?0:weightedScore/credits;

            sgpas.add(sgpa);
        }

        // ensure we have at least 4 values
        double avg = 0;

        for(double s : sgpas){
            avg += s;
        }

        avg = sgpas.size() == 0 ? 7.0 : avg / sgpas.size();

        while(sgpas.size()<4){
            sgpas.add(avg);
        }

        double predicted = aiPredictionService.predictCGPA(
                sgpas.get(0),
                sgpas.get(1),
                sgpas.get(2),
                sgpas.get(3),
                backlogs,
                internalConsistency,
                metrics.attendancePercentage()
        );

        Map<String,Object> response = new HashMap<>();

        response.put("semesterSGPA",sgpas);
        response.put("predictedFinalCGPA",predicted);
        response.put("message","AI prediction based on real SGPA data");

        return response;
    }

    @GetMapping("/ai-guidance/{studentId}")
    public Map<String,Object> getAIGuidance(@PathVariable Long studentId) throws Exception {

        List<Result> results = resultRepository.findByStudentId(studentId);

        double totalWeightedScore = 0;
        int totalCredits = 0;
        Map<Integer,List<Result>> semesterResults = new HashMap<>();

        for(Result r : results){
            totalWeightedScore += r.getGradePoint()*r.getCredits();
            totalCredits += r.getCredits();
            semesterResults
                    .computeIfAbsent(r.getSemester(),k->new ArrayList<>())
                    .add(r);
        }

        AcademicAnalyticsService.StudentMetrics metrics = analyticsService.calculateAdvancedMetrics(results);
        int backlogs = metrics.backlogCount();
        double internalConsistency = metrics.internalConsistency();

        // Current CGPA
        double cgpa = totalCredits==0?0:totalWeightedScore/totalCredits;
        cgpa = Math.round(cgpa*100.0)/100.0;

        // Calculate SGPA per semester
        List<Double> sgpas = new ArrayList<>();

        for(List<Result> semResults : semesterResults.values()){

            double score = 0;
            int credits = 0;

            for(Result r : semResults){
                score += r.getGradePoint()*r.getCredits();
                credits += r.getCredits();
            }

            sgpas.add(score/credits);
        }

        // Fill missing semesters with average
        double avg = sgpas.stream().mapToDouble(Double::doubleValue).average().orElse(7.0);

        while(sgpas.size()<4){
            sgpas.add(avg);
        }

        double predictedCGPA = aiPredictionService.predictCGPA(
                sgpas.get(0),
                sgpas.get(1),
                sgpas.get(2),
                sgpas.get(3),
                backlogs,
                internalConsistency,
                metrics.attendancePercentage()
        );

        predictedCGPA = Math.round(predictedCGPA*100.0)/100.0;

        // Placement readiness score
        int readinessScore = (int)((cgpa/10)*50 + 20 + 10);

        // Placement probability
        double placementProbability =
                (cgpa*10*0.4) +
                        (readinessScore*0.4) +
                        (predictedCGPA*10*0.2);

        placementProbability = Math.min(100,Math.round(placementProbability));

        // Insight message
        String insight;

        if(predictedCGPA >= 8){
            insight = "Excellent trajectory. You are likely to qualify for most service and product companies.";
        }
        else if(predictedCGPA >= 7){
            insight = "Good progress. Maintain SGPA above 8 to unlock more placement opportunities.";
        }
        else{
            insight = "Academic improvement needed. Focus on improving SGPA in upcoming semesters.";
        }

        Map<String,Object> response = new HashMap<>();

        response.put("currentCGPA",cgpa);
        response.put("predictedFinalCGPA",predictedCGPA);
        response.put("readinessScore",readinessScore);
        response.put("placementProbability",placementProbability);
        response.put("insight",insight);

        return response;
    }

    @GetMapping("/dashboard/{studentId}")
    public Map<String,Object> getDashboard(@PathVariable Long studentId) throws Exception {

        Map<String,Object> response = new HashMap<>();

        List<Result> results = resultRepository.findByStudentId(studentId);
        AcademicAnalyticsService.StudentMetrics metrics = analyticsService.calculateAdvancedMetrics(results);
        int backlogs = metrics.backlogCount();
        double internalConsistency = metrics.internalConsistency();

        // CGPA
        double cgpa = analyticsService.calculateCGPA(studentId);

        // SGPA per semester
        Map<Integer,Double> semesterSgpa =
                analyticsService.calculateSemesterSGPA(studentId);

        // Trend
        String trend = "Stable";

        if(semesterSgpa.size() >= 2){
            List<Double> values = new ArrayList<>(semesterSgpa.values());

            double first = values.get(0);
            double last = values.get(values.size()-1);

            if(last > first) trend = "Improving";
            else if(last < first) trend = "Declining";
        }

        // AI predicted CGPA
        List<Double> sgpas = new ArrayList<>(semesterSgpa.values());

        double avg = sgpas.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(7.0);

        while(sgpas.size() < 4){
            sgpas.add(avg);
        }

        double predictedCGPA =
                aiPredictionService.predictCGPA(
                        sgpas.get(0),
                        sgpas.get(1),
                        sgpas.get(2),
                        sgpas.get(3),
                        backlogs,
                        internalConsistency,
                        metrics.attendancePercentage()
                );

        predictedCGPA = Math.round(predictedCGPA * 100.0) / 100.0;

        // readiness score
        int readinessScore =
                (int)((cgpa / 10) * 50 + 20 + 10);

        // placement probability
        double placementProbability =
                (cgpa * 10 * 0.4) +
                        (readinessScore * 0.4) +
                        (predictedCGPA * 10 * 0.2);

        placementProbability =
                Math.min(100, Math.round(placementProbability));

        response.put("cgpa", cgpa);
        response.put("trend", trend);
        response.put("semesterSgpa", semesterSgpa);
        response.put("predictedCGPA", predictedCGPA);
        response.put("readinessScore", readinessScore);
        response.put("placementProbability", placementProbability);

        return response;
    }

    @GetMapping("/advisor/{studentId}")
    public Map<String,Object> academicAdvisor(@PathVariable Long studentId) throws Exception {

        Map<String,Object> dashboard =
                getDashboard(studentId);

        double cgpa = (double) dashboard.get("cgpa");
        String trend = (String) dashboard.get("trend");
        double predicted = (double) dashboard.get("predictedCGPA");

        String advice;

        if(cgpa >= 8){
            advice = "Excellent performance. Maintain your consistency to stay eligible for most companies.";
        }
        else if(cgpa >= 7){
            advice = "You are close to strong placement eligibility. Aim for SGPA above 8 next semester.";
        }
        else{
            advice = "Your CGPA needs improvement. Focus on scoring SGPA above 8 in upcoming semesters.";
        }

        if(trend.equals("Improving")){
            advice += " Your performance trend is improving which is a positive sign.";
        }

        Map<String, List<String>> subjectInsights = analyticsService.getSubjectInsights(studentId);
        List<String> weakSubjects = subjectInsights.getOrDefault("weak", new ArrayList<>());
        if (!weakSubjects.isEmpty()) {
            advice += " Focus specifically on improving: " + String.join(", ", weakSubjects) + ".";
        }

        Map<String,Object> response = new HashMap<>();

        response.put("cgpa", cgpa);
        response.put("trend", trend);
        response.put("predictedCGPA", predicted);
        response.put("advice", advice);
        response.put("strongSubjects", subjectInsights.get("strong"));
        response.put("weakSubjects", weakSubjects);

        return response;
    }
}