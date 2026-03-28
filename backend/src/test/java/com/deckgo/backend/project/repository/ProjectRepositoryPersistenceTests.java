package com.deckgo.backend.project.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.deckgo.backend.project.entity.ProjectEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:projectrepo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON"
})
class ProjectRepositoryPersistenceTests {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void shouldKeepCreatedAtWhenAssignedIdEntityIsSavedTwice() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setTitle("Assigned ID project");
        project.setTopic("Verify created_at stays intact across saves");
        project.setAudience("developer");
        project.setTemplateId("clarity-blue");

        ProjectEntity created = projectRepository.saveAndFlush(project);

        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertFalse(created.isNew());

        created.setTitle("Assigned ID project updated");
        ProjectEntity updated = projectRepository.saveAndFlush(created);

        assertNotNull(updated.getCreatedAt());
        assertNotNull(updated.getUpdatedAt());
    }
}
