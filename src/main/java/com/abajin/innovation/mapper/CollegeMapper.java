package com.abajin.innovation.mapper;

import com.abajin.innovation.entity.College;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学院Mapper接口
 */
@Mapper
public interface CollegeMapper {
    /**
     * 根据ID查询学院
     */
    College selectById(@Param("id") Long id);

    /**
     * 查询所有学院
     */
    List<College> selectAll();

    /**
     * 根据名称查询学院
     */
    College selectByName(@Param("name") String name);

    /**
     * 插入学院
     */
    int insert(College college);

    /**
     * 更新学院
     */
    int update(College college);
}
