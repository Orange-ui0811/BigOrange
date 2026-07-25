package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.AiCompetition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiCompetitionMapper {
    int insert(AiCompetition competition);
    AiCompetition selectById(@Param("id") Long id);
    AiCompetition selectByIdForUpdate(@Param("id") Long id);
    List<AiCompetition> selectPublic(@Param("history") boolean history,
                                     @Param("now") LocalDateTime now,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);
    int countPublic(@Param("history") boolean history, @Param("now") LocalDateTime now);
    List<AiCompetition> selectAdmin(@Param("status") String status,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);
    int countAdmin(@Param("status") String status);
    int updateContent(AiCompetition competition);
    int approve(@Param("id") Long id,
                @Param("reviewerId") Long reviewerId,
                @Param("rewardGranted") boolean rewardGranted,
                @Param("rewardQuota") int rewardQuota);
    int reject(@Param("id") Long id,
               @Param("reviewerId") Long reviewerId,
               @Param("reason") String reason);
    int deleteById(@Param("id") Long id);
}
