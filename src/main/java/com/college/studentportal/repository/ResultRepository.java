package com.college.studentportal.repository;

import com.college.studentportal.model.Result;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResultRepository extends JpaRepository<Result, Long> {

    List<Result> findByStudentId(Long studentId);

    List<Result> findByStudentIdAndSemester(Long studentId, int semester);
}