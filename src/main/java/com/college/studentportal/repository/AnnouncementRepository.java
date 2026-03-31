package com.college.studentportal.repository;

import com.college.studentportal.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByOrderByCreatedAtDesc();

    @Query("SELECT a FROM Announcement a WHERE a.targetType = 'ALL' " +
           "OR CONCAT(',', a.targetStudentIds, ',') LIKE CONCAT('%,', :studentId, ',%') " +
           "ORDER BY a.createdAt DESC")
    List<Announcement> findAnnouncementsForStudent(@Param("studentId") String studentId);
}
