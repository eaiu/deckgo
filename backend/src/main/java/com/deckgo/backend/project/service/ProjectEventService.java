package com.deckgo.backend.project.service;

import com.deckgo.backend.project.dto.ProjectEventSnapshot;
import java.util.List;
import java.util.UUID;

public interface ProjectEventService {

    List<ProjectEventSnapshot> listEventsAfter(UUID projectId, long afterStreamId, int limit);
}
