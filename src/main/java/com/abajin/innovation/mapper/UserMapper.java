package com.abajin.innovation.mapper;

import com.abajin.innovation.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper {
    /**
     * 根据ID查询用户
     */
    User selectById(@Param("id") Long id);

    /**
     * 根据用户名查询用户
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 根据真实姓名查询用户列表
     */
    List<User> selectByRealName(@Param("realName") String realName);

    /**
     * 插入用户
     */
    int insert(User user);

    /**
     * 更新用户
     */
    int update(User user);

    /**
     * 根据学院ID查询用户列表
     */
    List<User> selectByCollegeId(@Param("collegeId") Long collegeId);

    /**
     * 根据角色查询用户列表
     */
    List<User> selectByRole(@Param("role") String role);

    /**
     * 查询所有用户
     */
    List<User> selectAll();

    /**
     * 根据条件查询用户列表
     */
    List<User> selectByCondition(
            @Param("username") String username,
            @Param("realName") String realName,
            @Param("role") String role,
            @Param("collegeId") Long collegeId,
            @Param("status") Integer status
    );

    /**
     * 根据ID删除用户
     */
    int deleteById(@Param("id") Long id);
}
