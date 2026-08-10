package cs590.ResumeService.repository;

import cs590.ResumeService.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<ResumeEntity, String> {
}
