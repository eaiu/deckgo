package com.deckgo.backend.project.mapper;

import com.deckgo.backend.project.pojo.ProjectEventPO;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectEventMapper {

    List<ProjectEventPO> selectEventsAfterStreamId(
        @Param("projectId") UUID projectId,
        @Param("afterStreamId") long afterStreamId,
        @Param("limit") int limit
    );
}
