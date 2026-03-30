package com.deckgo.backend.project.mapper;

import com.deckgo.backend.project.pojo.RequirementFormPO;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RequirementFormMapper {

    RequirementFormPO selectByProjectId(@Param("projectId") UUID projectId);

    int updateAnswersJson(
        @Param("requirementFormId") UUID requirementFormId,
        @Param("answersJson") String answersJson,
        @Param("status") String status,
        @Param("updatedAt") OffsetDateTime updatedAt
    );
}
