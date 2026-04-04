package com.abajin.innovation.service;

import com.abajin.innovation.common.PageResult;
import com.abajin.innovation.dto.CreateUserDTO;
import com.abajin.innovation.dto.LoginDTO;
import com.abajin.innovation.dto.RegisterDTO;
import com.abajin.innovation.dto.UserQueryDTO;
import com.abajin.innovation.dto.UserImportDTO;
import com.abajin.innovation.entity.User;
import com.abajin.innovation.entity.College;
import com.abajin.innovation.mapper.UserMapper;
import com.abajin.innovation.mapper.CollegeMapper;
import com.abajin.innovation.util.JwtUtil;
import com.abajin.innovation.common.Constants;
import com.abajin.innovation.listener.UserImportListener;
import com.alibaba.excel.EasyExcel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CollegeMapper collegeMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UnifiedAuthVerifier unifiedAuthVerifier;

    @Transactional
    public String login(LoginDTO loginDTO) {
        User user = userMapper.selectByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (user.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new RuntimeException("账户已被禁用");
        }

        // 二次校验：统一身份认证（仍使用同一个用户名/密码）
        UnifiedAuthVerifier.UnifiedIdentity identity = unifiedAuthVerifier.verify(loginDTO.getUsername(), loginDTO.getPassword());
        if (identity != null && identity.isEnabled()) {
            User patch = new User();
            patch.setId(user.getId());

            boolean needUpdate = false;

            if (identity.getName() != null && !identity.getName().isEmpty()
                    && (user.getRealName() == null || !identity.getName().equals(user.getRealName()))) {
                patch.setRealName(identity.getName());
                needUpdate = true;
            }

            if (identity.getCollegeName() != null && !identity.getCollegeName().isEmpty()
                    && (user.getCollegeName() == null || !identity.getCollegeName().equals(user.getCollegeName()))) {
                patch.setCollegeName(identity.getCollegeName());
                var college = collegeMapper.selectByName(identity.getCollegeName());
                if (college != null) {
                    patch.setCollegeId(college.getId());
                }
                needUpdate = true;
            }

            if (needUpdate) {
                userMapper.update(patch);
            }
        }

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    }

    @Transactional
    public User register(RegisterDTO registerDTO) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(registerDTO.getUsername());
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 只允许注册学生账号
        if (!Constants.ROLE_STUDENT.equals(registerDTO.getRole())) {
            throw new RuntimeException("注册只允许学生角色，其他账号类型请联系管理员创建");
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRealName(registerDTO.getRealName());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setRole(Constants.ROLE_STUDENT);
        user.setCollegeId(registerDTO.getCollegeId());
        user.setStatus(Constants.USER_STATUS_ENABLED);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 如果提供了学院ID，查询学院名称
        if (registerDTO.getCollegeId() != null) {
            var college = collegeMapper.selectById(registerDTO.getCollegeId());
            if (college != null) {
                user.setCollegeName(college.getName());
            }
        }

        userMapper.insert(user);
        return user;
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Transactional(readOnly = true)
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    /**
     * 修改当前用户密码
     * @param userId 当前用户ID（从 token 获取）
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isEmpty()) {
            throw new RuntimeException("请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    /**
     * 管理员创建用户
     * @param createUserDTO 创建用户数据
     * @return 创建的用户
     */
    @Transactional
    public User createUser(CreateUserDTO createUserDTO) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectByUsername(createUserDTO.getUsername());
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(createUserDTO.getUsername());
        user.setPassword(passwordEncoder.encode(createUserDTO.getPassword()));
        user.setRealName(createUserDTO.getRealName());
        user.setEmail(createUserDTO.getEmail());
        user.setPhone(createUserDTO.getPhone());
        user.setRole(createUserDTO.getRole());
        user.setCollegeId(createUserDTO.getCollegeId());
        // 如果未指定状态，默认启用
        user.setStatus(createUserDTO.getStatus() != null ? createUserDTO.getStatus() : Constants.USER_STATUS_ENABLED);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 如果提供了学院ID，查询学院名称
        if (createUserDTO.getCollegeId() != null) {
            var college = collegeMapper.selectById(createUserDTO.getCollegeId());
            if (college != null) {
                user.setCollegeName(college.getName());
            }
        }

        userMapper.insert(user);
        return user;
    }

    /**
     * 分页查询用户列表
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<User> getUserList(UserQueryDTO queryDTO) {
        // 构建查询条件
        List<User> list = userMapper.selectByCondition(
                queryDTO.getUsername(),
                queryDTO.getRealName(),
                queryDTO.getRole(),
                queryDTO.getCollegeId(),
                queryDTO.getStatus()
        );
        
        // 手动分页
        int total = list.size();
        int start = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();
        int end = Math.min(start + queryDTO.getPageSize(), total);
        
        List<User> pageList = start < total ? list.subList(start, end) : List.of();
        
        return new PageResult<>(queryDTO.getPageNum(), queryDTO.getPageSize(), (long) total, pageList);
    }

    /**
     * 更新用户状态
     * @param userId 用户ID
     * @param status 状态：0-禁用，1-启用
     */
    @Transactional
    public void updateUserStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    /**
     * 重置用户密码
     * @param userId 用户ID
     * @param newPassword 新密码
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    /**
     * 更新用户信息
     * @param userId 用户ID
     * @param userData 用户数据
     */
    @Transactional
    public void updateUser(Long userId, User userData) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 不允许修改用户名
        if (StringUtils.hasText(userData.getRealName())) {
            user.setRealName(userData.getRealName());
        }
        if (StringUtils.hasText(userData.getEmail())) {
            user.setEmail(userData.getEmail());
        }
        if (StringUtils.hasText(userData.getPhone())) {
            user.setPhone(userData.getPhone());
        }
        if (userData.getCollegeId() != null) {
            user.setCollegeId(userData.getCollegeId());
            var college = collegeMapper.selectById(userData.getCollegeId());
            if (college != null) {
                user.setCollegeName(college.getName());
            }
        }
        if (StringUtils.hasText(userData.getRole())) {
            user.setRole(userData.getRole());
        }
        
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    /**
     * 删除用户
     * @param userId 用户ID
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // TODO: 检查用户是否有关联数据（项目、团队等），如果有则不允许删除
        userMapper.deleteById(userId);
    }

    /**
     * 从Excel导入用户
     * @param inputStream Excel文件输入流
     * @return 导入成功的用户数量
     */
    @Transactional
    public int importUsersFromExcel(InputStream inputStream) {
        UserImportListener listener = new UserImportListener();
        EasyExcel.read(inputStream, UserImportDTO.class, listener).sheet().doRead();
        List<UserImportDTO> list = listener.getList();

        // 获取所有学院，用于根据名称查找学院ID
        List<College> colleges = collegeMapper.selectAll();
        Map<String, College> collegeNameMap = colleges.stream()
                .collect(Collectors.toMap(College::getName, c -> c, (c1, c2) -> c1));

        int count = 0;
        for (UserImportDTO row : list) {
            // 检查必填字段
            if (row.getUsername() == null || row.getUsername().trim().isEmpty()) {
                throw new RuntimeException("第" + (count + 1) + "行：用户名不能为空");
            }
            if (row.getPassword() == null || row.getPassword().trim().isEmpty()) {
                throw new RuntimeException("第" + (count + 1) + "行：密码不能为空");
            }
            if (row.getRealName() == null || row.getRealName().trim().isEmpty()) {
                throw new RuntimeException("第" + (count + 1) + "行：真实姓名不能为空");
            }
            if (row.getRole() == null || row.getRole().trim().isEmpty()) {
                throw new RuntimeException("第" + (count + 1) + "行：角色不能为空");
            }

            // 验证角色是否有效
            String role = row.getRole().trim();
            if (!Constants.ROLE_STUDENT.equals(role) 
                    && !Constants.ROLE_TEACHER.equals(role)
                    && !Constants.ROLE_COLLEGE_ADMIN.equals(role) 
                    && !Constants.ROLE_SCHOOL_ADMIN.equals(role)) {
                throw new RuntimeException("第" + (count + 1) + "行：无效的角色，必须是 STUDENT、TEACHER、COLLEGE_ADMIN 或 SCHOOL_ADMIN");
            }

            // 检查用户名是否已存在
            User existingUser = userMapper.selectByUsername(row.getUsername().trim());
            if (existingUser != null) {
                throw new RuntimeException("第" + (count + 1) + "行：用户名已存在：" + row.getUsername());
            }

            // 确定学院ID
            Long collegeId = row.getCollegeId();
            String collegeName = null;
            if (collegeId == null && row.getCollegeName() != null && !row.getCollegeName().trim().isEmpty()) {
                College college = collegeNameMap.get(row.getCollegeName().trim());
                if (college != null) {
                    collegeId = college.getId();
                    collegeName = college.getName();
                }
            } else if (collegeId != null) {
                College college = collegeMapper.selectById(collegeId);
                if (college != null) {
                    collegeName = college.getName();
                }
            }

            // 确定状态
            Integer status = row.getStatus();
            if (status == null) {
                status = Constants.USER_STATUS_ENABLED;
            }

            User user = new User();
            user.setUsername(row.getUsername().trim());
            user.setPassword(passwordEncoder.encode(row.getPassword().trim()));
            user.setRealName(row.getRealName().trim());
            user.setEmail(row.getEmail() != null && !row.getEmail().trim().isEmpty() ? row.getEmail().trim() : null);
            user.setPhone(row.getPhone() != null && !row.getPhone().trim().isEmpty() ? row.getPhone().trim() : null);
            user.setRole(role);
            user.setCollegeId(collegeId);
            user.setCollegeName(collegeName);
            user.setStatus(status);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());

            userMapper.insert(user);
            count++;
        }
        return count;
    }
}
