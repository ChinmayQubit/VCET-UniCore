package com.college.studentportal.repository;

import com.college.studentportal.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByEmail(String email);
    List<Student> findBySemester(int semester);
    List<Student> findBySemesterAndDepartment(int semester, String department);
}