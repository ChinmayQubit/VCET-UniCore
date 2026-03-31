package com.college.studentportal.controller;

import com.college.studentportal.model.Announcement;
import com.college.studentportal.repository.AnnouncementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/announcements")
public class AnnouncementController {

    @Autowired
    private AnnouncementRepository announcementRepository;

    /**
     * Admin creates a new announcement.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAnnouncement(@RequestBody Announcement announcement) {
        announcement.setCreatedAt(LocalDateTime.now());

        // Clean up targetStudentIds if targetType is ALL
        if ("ALL".equalsIgnoreCase(announcement.getTargetType())) {
            announcement.setTargetStudentIds(null);
        }

        Announcement saved = announcementRepository.save(announcement);
        return ResponseEntity.ok(Map.of(
                "announcement", saved,
                "message", "Announcement posted successfully."
        ));
    }

    /**
     * Get all announcements (admin view, newest first).
     */
    @GetMapping
    public List<Announcement> getAllAnnouncements() {
        return announcementRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Get announcements visible to a specific student.
     */
    @GetMapping("/student/{id}")
    public List<Announcement> getAnnouncementsForStudent(@PathVariable Long id) {
        return announcementRepository.findAnnouncementsForStudent(String.valueOf(id));
    }

    /**
     * Admin deletes an announcement.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAnnouncement(@PathVariable Long id) {
        if (!announcementRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        announcementRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Announcement deleted."));
    }
}
