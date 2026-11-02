package com.orangejd.server.customer;

import org.apache.ibatis.annotations.*;
import java.util.*;

@Mapper
public interface CustomerSourceFactMapper {
    @Select("SELECT * FROM customer_source_fact ORDER BY id") List<Map<String,Object>> all();
    @Select("SELECT * FROM customer_source_fact WHERE id=#{id} FOR UPDATE") Map<String,Object> lock(String id);
    @Insert("INSERT IGNORE INTO customer_source_fact(id,fact_json,status) VALUES(#{id},#{fact},#{status})")
    int insert(@Param("id") String id,@Param("fact") String fact,@Param("status") String status);
    @Update("UPDATE customer_source_fact SET status=#{status},revision=revision+1,reviewed_by=#{actor} WHERE id=#{id} AND revision=#{revision}")
    int transition(@Param("id") String id,@Param("revision") long revision,@Param("status") String status,@Param("actor") Long actor);
    @Insert("INSERT INTO customer_source_fact_event(fact_id,revision,status,actor_id,reason) VALUES(#{id},#{revision},#{status},#{actor},#{reason})")
    int event(@Param("id") String id,@Param("revision") long revision,@Param("status") String status,@Param("actor") Long actor,@Param("reason") String reason);
    @Select("SELECT * FROM customer_source_fact_event WHERE fact_id=#{id} ORDER BY id DESC LIMIT 100") List<Map<String,Object>> events(String id);
}
