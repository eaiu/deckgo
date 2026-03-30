package com.deckgo.backend.project.mapper;

import com.deckgo.backend.project.pojo.ProjectPO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectMapper {

    List<ProjectPO> selectProjects();

    ProjectPO selectProjectById(@Param("projectId") UUID projectId);

    int insertProject(ProjectPO project);

    int updateProject(ProjectPO project);

    int deleteProject(@Param("projectId") UUID projectId);
}
