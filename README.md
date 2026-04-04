# 天津科技大学创新创业信息平台

## 项目简介

本项目是**天津科技大学创新创业信息平台**的后端服务，旨在为学校的创新创业活动提供信息化支持，包括活动管理、项目管理、团队管理、空间预约等功能。

## 技术栈

- **框架**: Spring Boot 3.5.0
- **JDK**: Java 17
- **安全**: Spring Security + JWT
- **ORM**: MyBatis
- **数据库**: MySQL 5.7+
- **缓存**: Redis
- **对象存储**: MinIO
- **其他**: Lombok、EasyExcel、Validation

## 快速开始

### 环境要求

- JDK 17+
- MySQL 5.7+
- Redis 6.0+
- Maven 3.8+

### 配置

1. 创建数据库并导入初始数据：
```bash
mysql -u root -p < innovation_platform.sql
```

2. 修改 `src/main/resources/application.yml` 中的数据库、Redis、MinIO等配置

3. （可选）启用统一身份认证“二次校验”（仍使用同一个用户名/密码登录表单）：

```bash
# 开关：开启后 /auth/login 会先校验本地账号，再校验统一身份认证
UNIFIED_AUTH_ENABLED=true

# 目前支持 LDAP 校验（示例值需要替换为学校实际参数）
UNIFIED_AUTH_MODE=LDAP
UNIFIED_AUTH_LDAP_URL=ldap://ldap.your-school.edu.cn:389
UNIFIED_AUTH_LDAP_USER_DN_PATTERN=uid={0},ou=people,dc=your-school,dc=edu,dc=cn
UNIFIED_AUTH_LDAP_BASE_DN=dc=your-school,dc=edu,dc=cn
UNIFIED_AUTH_LDAP_SEARCH_FILTER=(uid={0})
```

### 运行

```bash
# 使用 Maven 运行
./mvnw spring-boot:run

# 或打包后运行
./mvnw clean package
java -jar target/innovation-0.0.1-SNAPSHOT.jar
```

### Docker 部署

```bash
docker-compose up -d
```

## 项目结构

```
src/
├── main/
│   ├── java/com/abajin/innovation/
│   │   ├── config/        # 配置类
│   │   ├── controller/    # 控制器
│   │   ├── service/       # 业务逻辑
│   │   ├── mapper/        # 数据访问层
│   │   ├── entity/        # 实体类
│   │   ├── dto/           # 数据传输对象
│   │   ├── vo/            # 视图对象
│   │   ├── utils/         # 工具类
│   │   └── security/      # 安全配置
│   └── resources/
│       ├── mapper/        # MyBatis XML
│       └── application.yml
└── test/                  # 测试代码
```

## 许可证

本项目采用 **[GNU Affero General Public License v3.0 (AGPLv3)](./LICENSE)** 开源协议发布。

> AGPLv3 是一种强 copyleft 许可证，要求任何修改或衍生作品在通过网络提供服务时也必须开源。详情请参阅 [LICENSE](./LICENSE) 文件。

## 致谢

感谢项目原作者 **jin** 的辛勤工作和贡献。

## TODO

- [ ] 编写单元测试和集成测试
- [ ] 修复已知 Bug

## 贡献

欢迎提交 Issue 和 Pull Request。

---

© 天津科技大学
