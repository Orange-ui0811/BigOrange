package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserProfileMapper {

    int insert(UserProfile userProfile);

    UserProfile selectByAccountId(@Param("accountId") Long accountId);

    int update(UserProfile userProfile);
}
