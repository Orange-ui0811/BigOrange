package com.orangejd.server.mapper;

import com.orangejd.pojo.entity.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountMapper {

    Account selectByUsername(@Param("username") String username);

    Account selectById(@Param("id") Long id);

    int insert(Account account);

    int updatePassword(@Param("id") Long id, @Param("password") String password);
}
