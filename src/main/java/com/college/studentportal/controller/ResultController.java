package com.college.studentportal.controller;

import com.college.studentportal.model.*;
import com.college.studentportal.repository.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/results")
public class ResultController {

    private final ResultRepository resultRepository;
    private final StudentRepository studentRepository;

    public ResultController(ResultRepository resultRepository,
                            StudentRepository studentRepository) {
        this.resultRepository = resultRepository;
        this.studentRepository = studentRepository;
    }

    @PostMapping
    public Result addResult(@RequestBody java.util.Map<String, Object> payload) {
        
        java.util.Map<String, Object> studentMap = (java.util.Map<String, Object>) payload.get("student");
        Long studentId = Long.parseLong(studentMap.get("id").toString());
        
        Student student = studentRepository.findById(studentId).orElseThrow();

        Result result = new Result();
        result.setStudent(student);
        result.setCourseCode(String.valueOf(payload.get("courseCode")));
        result.setCourseName(String.valueOf(payload.get("courseName")));
        result.setInternalMarks(Double.parseDouble(payload.get("internalMarks").toString()));
        result.setExternalMarks(Double.parseDouble(payload.get("externalMarks").toString()));
        result.setCredits(Integer.parseInt(payload.get("credits").toString()));
        result.setSemester(Integer.parseInt(payload.get("semester").toString()));

        // MU NEP Calculation Logic
        double total = result.getInternalMarks() + result.getExternalMarks();
        result.setTotalMarks(total);

        String grade = "F";
        double point = 0.0;

        if (total >= 90) { grade = "O"; point = 10.0; }
        else if (total >= 80) { grade = "A+"; point = 9.0; }
        else if (total >= 70) { grade = "A"; point = 8.0; }
        else if (total >= 60) { grade = "B+"; point = 7.0; }
        else if (total >= 50) { grade = "B"; point = 6.0; }
        else if (total >= 45) { grade = "C"; point = 5.0; }
        else if (total >= 40) { grade = "P"; point = 4.0; }

        result.setGrade(grade);
        result.setGradePoint(point);
        result.setCreditGrade(point * result.getCredits());

        return resultRepository.save(result);
    }

    /**
     * Bulk Upload Results via CSV
     * Format: Student Email, Course Code, Course Name, Internal Marks, External Marks, Credits, Semester
     */
    @PostMapping("/upload")
    public org.springframework.http.ResponseEntity<Map<String, Object>> uploadResults(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        List<Result> createdResults = new ArrayList<>();
        int skipped = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] columns = line.split(",");
                if (columns.length < 7) continue;

                String email = columns[0].trim();
                String courseCode = columns[1].trim();
                String courseName = columns[2].trim();
                String internalStr = columns[3].trim();
                String externalStr = columns[4].trim();
                String creditsStr = columns[5].trim();
                String semStr = columns[6].trim();

                if (isFirstLine) {
                    isFirstLine = false;
                    if (email.toLowerCase().contains("email") || courseCode.toLowerCase().contains("course")) {
                        continue;
                    }
                }

                Optional<Student> studentOpt = studentRepository.findByEmail(email);
                if (studentOpt.isEmpty()) {
                    skipped++;
                    System.err.println("Cannot assign result to non-existent student email: " + email);
                    continue;
                }

                try {
                    double internalMarks = Double.parseDouble(internalStr);
                    double externalMarks = Double.parseDouble(externalStr);
                    int credits = Integer.parseInt(creditsStr);
                    int semester = Integer.parseInt(semStr);

                    Result result = new Result();
                    result.setStudent(studentOpt.get());
                    result.setCourseCode(courseCode);
                    result.setCourseName(courseName);
                    result.setInternalMarks(internalMarks);
                    result.setExternalMarks(externalMarks);
                    result.setCredits(credits);
                    result.setSemester(semester);

                    // MU NEP Calculation Logic
                    double total = internalMarks + externalMarks;
                    result.setTotalMarks(total);

                    String grade = "F";
                    double point = 0.0;
                    if (total >= 90) { grade = "O"; point = 10.0; }
                    else if (total >= 80) { grade = "A+"; point = 9.0; }
                    else if (total >= 70) { grade = "A"; point = 8.0; }
                    else if (total >= 60) { grade = "B+"; point = 7.0; }
                    else if (total >= 50) { grade = "B"; point = 6.0; }
                    else if (total >= 45) { grade = "C"; point = 5.0; }
                    else if (total >= 40) { grade = "P"; point = 4.0; }

                    result.setGrade(grade);
                    result.setGradePoint(point);
                    result.setCreditGrade(point * credits);

                    createdResults.add(result);
                } catch (NumberFormatException ignored) {
                    skipped++;
                }
            }
            
            resultRepository.saveAll(createdResults);
            
            return org.springframework.http.ResponseEntity.ok(Map.of(
                    "message", "Successfully imported " + createdResults.size() + " results. Skipped " + skipped + " bad rows/missing students.",
                    "importedCount", createdResults.size(),
                    "skippedCount", skipped
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return org.springframework.http.ResponseEntity.status(500).body(Map.of("error", "Error processing CSV file: " + e.getMessage()));
        }
    }

    // 🔹 Get results of a student
    @GetMapping("/student/{studentId}")
    public List<Result> getResultsByStudent(@PathVariable("studentId") Long studentId) {
        return resultRepository.findByStudentId(studentId);
    }

    // 🔹 Get ALL results
    @GetMapping
    public List<Result> getAllResults() {
        return resultRepository.findAll();
    }

    // 🔹 Delete a result by id
    @DeleteMapping("/{id}")
    public void deleteResult(@PathVariable("id") Long id) {
        resultRepository.deleteById(id);
    }

    // 🔹 Calculate CGPA
    @GetMapping("/cgpa/{studentId}")
    public double calculateCGPA(@PathVariable("studentId") Long studentId) {
        List<Result> results = resultRepository.findByStudentId(studentId);
        double totalCreditGrades = 0;
        int totalCredits = 0;

        for (Result r : results) {
            totalCreditGrades += r.getCreditGrade();
            totalCredits += r.getCredits();
        }

        if (totalCredits == 0) return 0;
        return Math.round((totalCreditGrades / totalCredits) * 100.0) / 100.0;
    }

    // 🔹 Calculate SGPA (semester-wise)
    @GetMapping("/sgpa/{studentId}/{semester}")
    public double calculateSGPA(@PathVariable("studentId") Long studentId, @PathVariable("semester") int semester) {
        List<Result> results = resultRepository.findByStudentIdAndSemester(studentId, semester);
        double totalCreditGrades = 0;
        int totalCredits = 0;

        for (Result r : results) {
            totalCreditGrades += r.getCreditGrade();
            totalCredits += r.getCredits();
        }

        if (totalCredits == 0) return 0;
        return Math.round((totalCreditGrades / totalCredits) * 100.0) / 100.0;
    }
}