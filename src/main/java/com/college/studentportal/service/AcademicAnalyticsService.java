package com.college.studentportal.service;

import com.college.studentportal.model.Result;
import com.college.studentportal.repository.ResultRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AcademicAnalyticsService {

    private final ResultRepository resultRepository;
    private final AttendanceService attendanceService;

    public AcademicAnalyticsService(ResultRepository resultRepository, AttendanceService attendanceService) {
        this.resultRepository = resultRepository;
        this.attendanceService = attendanceService;
    }

    public double calculateCGPA(Long studentId){

        List<Result> results = resultRepository.findByStudentId(studentId);

        double totalWeightedScore = 0;
        int totalCredits = 0;

        for(Result r : results){
            totalWeightedScore += r.getGradePoint()*r.getCredits();
            totalCredits += r.getCredits();
        }

        if(totalCredits == 0) return 0;

        return Math.round((totalWeightedScore/totalCredits)*100.0)/100.0;
    }

    public Map<Integer,Double> calculateSemesterSGPA(Long studentId){

        List<Result> results = resultRepository.findByStudentId(studentId);

        Map<Integer,List<Result>> semesterResults = new HashMap<>();

        for(Result r : results){

            int sem = r.getSemester();

            semesterResults
                    .computeIfAbsent(sem,k->new ArrayList<>())
                    .add(r);
        }

        Map<Integer,Double> semesterSgpa = new TreeMap<>();

        for(Map.Entry<Integer,List<Result>> entry : semesterResults.entrySet()){

            double weightedScore = 0;
            int credits = 0;

            for(Result r : entry.getValue()){
                weightedScore += r.getGradePoint()*r.getCredits();
                credits += r.getCredits();
            }

            double sgpa = credits==0?0:weightedScore/credits;

            semesterSgpa.put(entry.getKey(),
                    Math.round(sgpa*100.0)/100.0);
        }

        return semesterSgpa;
    }

    public Map<String, List<String>> getSubjectInsights(Long studentId) {
        List<Result> results = resultRepository.findByStudentId(studentId);
        
        List<String> strongSubjects = new ArrayList<>();
        List<String> weakSubjects = new ArrayList<>();

        for (Result r : results) {
            if (r.getGradePoint() >= 8.5) {
                strongSubjects.add(r.getCourseName());
            } else if (r.getGradePoint() < 7.0) {
                weakSubjects.add(r.getCourseName());
            }
        }

        Map<String, List<String>> insights = new HashMap<>();
        insights.put("strong", strongSubjects);
        insights.put("weak", weakSubjects);
        
        return insights;
    }

    public record StudentMetrics(int backlogCount, double internalConsistency, double attendancePercentage) {}

    public StudentMetrics calculateAdvancedMetrics(List<Result> results) {
        int backlogs = 0;
        double intT = 0, extT = 0;
        for (Result r : results) {
            if (r.getGradePoint() < 5) backlogs++;
            intT += r.getInternalMarks();
            extT += r.getExternalMarks();
        }
        double internalConsistency = (intT + extT == 0) ? 0 : intT / (intT + extT);
        
        // Fetch attendance for the student linked to these results
        double attendancePercentage = 100.0;
        if (!results.isEmpty()) {
            attendancePercentage = attendanceService.getOverallAttendancePercentage(results.get(0).getStudent().getId());
        }
        
        return new StudentMetrics(backlogs, internalConsistency, attendancePercentage);
    }
}