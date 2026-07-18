package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LeaderboardSnapshotMapper {
    int insert(LeaderboardSnapshot snapshot);
    LeaderboardSnapshot selectByKey(@Param("source") String source, @Param("topicName") String topicName,
                                     @Param("period") String period, @Param("snapshotDate") String snapshotDate);
    LeaderboardSnapshot selectLatest(@Param("source") String source, @Param("topicName") String topicName,
                                     @Param("period") String period);
    int deleteBefore(@Param("beforeDate") String beforeDate);
}
