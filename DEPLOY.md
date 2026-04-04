# CD 部署指南 - GitHub Actions + GHCR + Watchtower

## 架构图

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  每天 0:00  │────▶│ GitHub      │────▶│  GHCR       │◀────│  Server     │
│  定时触发   │     │  Actions    │     │ (Registry)  │     │  Watchtower │
│  (UTC 0:00) │     │  构建前后端  │     │             │     │  (30s检查)  │
└─────────────┘     └─────────────┘     └─────────────┘     └──────┬──────┘
                                                                   │
                                                                   │ 自动拉取
                                                                   ▼
                                                            ┌─────────────┐
                                                            │ 前后端容器  │
                                                            │ 自动更新    │
                                                            └─────────────┘
```

## 触发机制

| 触发方式 | 说明 |
|---------|------|
| **定时触发** | 每天 UTC 0:00（北京时间 8:00）自动构建 |
| **手动触发** | 在 GitHub Actions 页面点击 "Run workflow" |

## 镜像命名规则

| 服务 | 镜像地址 |
|------|---------|
| 后端 | `ghcr.io/{用户名}/innovation-backend:latest` |
| 前端 | `ghcr.io/{用户名}/innovation-backend-frontend:latest` |

> 镜像标签包括：`latest`、日期标签（如 `20240315`）、commit SHA（如 `sha-abc123`）

## 1. 初始化 Git 子模块（前端代码）

如果是首次克隆仓库，需要初始化子模块：

```bash
# 克隆时包含子模块
git clone --recurse-submodules https://github.com/your-username/innovation-backend.git

# 或者克隆后初始化
git submodule update --init --recursive

# 更新子模块到最新
git submodule update --remote
```

## 2. 配置 GitHub Secrets

默认使用 `GITHUB_TOKEN` 即可，无需额外配置。

## 3. 服务器环境准备

### 3.1 安装 Docker 和 Docker Compose

```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 验证安装
docker --version
docker compose version
```

### 3.2 配置 GHCR 登录

在服务器上创建 GitHub Personal Access Token：

1. 访问 GitHub Settings → Developer settings → Personal access tokens
2. 生成 Token，勾选 `read:packages` 权限
3. 在服务器上登录：

```bash
echo "your-github-token" | docker login ghcr.io -u your-username --password-stdin
```

### 3.3 创建环境变量文件

```bash
mkdir -p /opt/innovation-platform
cd /opt/innovation-platform

cat > .env << 'EOF'
# 数据库配置
MYSQL_ROOT_PASSWORD=your-secure-password
MYSQL_USER=root

# MinIO 配置（如果使用）
MINIO_URL=http://your-minio-server:9000
MINIO_ACCESS_KEY=your-access-key
MINIO_SECRET_KEY=your-secret-key

# JWT 配置
JWT_SECRET=your-jwt-secret-key-change-this
JWT_EXPIRATION=86400000

# 统一身份认证二次校验（可选）
UNIFIED_AUTH_ENABLED=false
UNIFIED_AUTH_MODE=LDAP
UNIFIED_AUTH_LDAP_URL=ldap://ldap.your-school.edu.cn:389
UNIFIED_AUTH_LDAP_USER_DN_PATTERN=uid={0},ou=people,dc=your-school,dc=edu,dc=cn
UNIFIED_AUTH_LDAP_BASE_DN=dc=your-school,dc=edu,dc=cn
UNIFIED_AUTH_LDAP_SEARCH_FILTER=(uid={0})

# GitHub 用户名（用于镜像路径）
GITHUB_OWNER=your-github-username
EOF
```

## 4. 部署应用

### 4.1 下载配置文件

```bash
cd /opt/innovation-platform

# 从仓库下载配置文件
curl -O https://raw.githubusercontent.com/your-username/innovation-backend/main/docker-compose.prod.yml
curl -O https://raw.githubusercontent.com/your-username/innovation-backend/main/nginx.conf

# 重命名
cp docker-compose.yml docker-compose.yml
```

### 4.2 首次启动

```bash
# 拉取最新镜像
docker compose pull

# 启动服务（后台运行）
docker compose up -d

# 查看日志
docker compose logs -f
```

### 4.3 验证部署

```bash
# 检查容器状态
docker ps

# 输出示例：
# CONTAINER ID   IMAGE                              STATUS         PORTS
# xxxxx          innovation-nginx                   Up 10 minutes  0.0.0.0:80->80/tcp
# xxxxx          ghcr.io/.../innovation-backend     Up 10 minutes  
# xxxxx          ghcr.io/.../innovation-backend-frontend  Up 10 minutes
# xxxxx          mysql:8.0                          Up 10 minutes  3306/tcp
# xxxxx          redis:7-alpine                     Up 10 minutes  6379/tcp
# xxxxx          containrrr/watchtower              Up 10 minutes

# 测试访问
curl http://localhost/api/actuator/health
curl http://localhost/health
```

## 5. 自动更新机制

### Watchtower 工作原理

1. **检查周期**：每 30 秒检查一次 GHCR
2. **监控范围**：只监控带有 `com.centurylinklabs.watchtower.enable=true` 标签的容器
3. **检测更新**：对比本地镜像 digest 和远程 digest
4. **自动更新**：发现新镜像后自动拉取并按依赖顺序重启容器
5. **清理旧镜像**：删除过期镜像节省磁盘空间

### 更新顺序

Watchtower 会按照 docker-compose 中的依赖关系智能更新：

```
mysql, redis (基础服务)
    ↓
backend (后端)
    ↓
frontend (前端)
    ↓
nginx (网关)
```

### 手动触发更新

如需立即更新（跳过等待到第二天）：

```bash
# 方法1：重启 watchtower 容器（立即检查）
docker restart watchtower

# 方法2：手动拉取并重启
docker compose pull backend frontend
docker compose up -d

# 方法3：使用 watchtower 一次性运行
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  containrrr/watchtower \
  --run-once \
  --label-enable
```

## 6. 查看构建历史

在 GitHub 仓库页面：
1. 点击 "Actions" 标签
2. 查看 "CD - Build and Push to GHCR" 工作流
3. 可以看到每天的定时构建记录

## 7. 版本回滚

如需回滚到特定版本：

```bash
# 查看可用镜像标签
# 访问：https://github.com/your-username/innovation-backend/pkgs/container/innovation-backend

# 修改 docker-compose.yml 指定版本
sed -i 's/:latest/:20240315/g' docker-compose.yml

# 重新部署
docker compose up -d
```

## 8. 监控与日志

### 查看各服务日志

```bash
# 后端应用日志
docker logs -f innovation_backend

# 前端应用日志
docker logs -f innovation_frontend

# Nginx 访问日志
docker logs -f innovation_nginx

# Watchtower 更新日志
docker logs -f watchtower

# 所有服务日志
docker compose logs -f
```

### 配置通知（可选）

在 `docker-compose.prod.yml` 中配置 Watchtower 通知：

```yaml
environment:
  WATCHTOWER_NOTIFICATIONS: email
  WATCHTOWER_NOTIFICATION_EMAIL_FROM: alerts@yourdomain.com
  WATCHTOWER_NOTIFICATION_EMAIL_TO: admin@yourdomain.com
  WATCHTOWER_NOTIFICATION_EMAIL_SERVER: smtp.gmail.com
  WATCHTOWER_NOTIFICATION_EMAIL_SERVER_PORT: 587
  WATCHTOWER_NOTIFICATION_EMAIL_SERVER_USER: your-email@gmail.com
  WATCHTOWER_NOTIFICATION_EMAIL_SERVER_PASSWORD: your-app-password
```

## 9. 安全加固

### 9.1 配置防火墙

```bash
# 只开放必要端口
sudo ufw default deny incoming
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 80/tcp      # HTTP
sudo ufw allow 443/tcp     # HTTPS
sudo ufw enable
```

### 9.2 定期更新 Token

```bash
# 重新登录（更新 token 后）
docker logout ghcr.io
echo "new-token" | docker login ghcr.io -u your-username --password-stdin
```

## 10. 故障排查

### 常见问题

#### Q: GitHub Actions 构建失败？

检查子模块是否正确检出：
```bash
# 在 Actions 日志中查看是否有 frontend/ 目录内容
```

确保 `.gitmodules` 配置正确：
```bash
cat .gitmodules
```

#### Q: Watchtower 没有自动更新？

检查标签配置：
```bash
# 确认容器有 watchtower 标签
docker inspect innovation_backend | grep watchtower
docker inspect innovation_frontend | grep watchtower

# 手动运行 watchtower 测试
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock containrrr/watchtower --run-once --debug --label-enable
```

#### Q: 镜像拉取失败？

```bash
# 检查登录状态
cat ~/.docker/config.json | grep ghcr.io

# 重新登录
docker login ghcr.io -u your-username
```

#### Q: 前端无法访问后端 API？

检查 Nginx 配置：
```bash
# 进入 Nginx 容器查看配置
docker exec -it innovation_nginx nginx -t

# 查看 Nginx 错误日志
docker logs innovation_nginx
```

#### Q: 数据库连接失败？

```bash
# 检查数据库健康状态
docker ps

# 查看数据库日志
docker logs innovation_mysql
```

## 11. 项目目录结构

```
innovation-backend/
├── .github/
│   └── workflows/
│       ├── ci.yml           # CI：测试
│       └── cd.yml           # CD：构建镜像（含子模块）
├── frontend/                # Git 子模块（前端代码）
│   ├── Dockerfile
│   └── ...
├── src/                     # 后端代码
├── Dockerfile               # 后端 Dockerfile
├── docker-compose.prod.yml  # 生产环境配置
├── nginx.conf               # Nginx 反向代理配置
├── DEPLOY.md               # 本文档
└── ...
```

## 12. 相关命令速查

```bash
# ========== 部署 ==========
docker compose up -d                    # 启动所有服务
docker compose pull                     # 拉取最新镜像
docker compose logs -f                  # 查看实时日志
docker compose ps                       # 查看服务状态

# ========== 管理 ==========
docker compose restart backend          # 重启后端
docker compose restart frontend         # 重启前端
docker compose down                     # 停止并删除容器
docker compose down -v                  # 停止并删除容器+卷（⚠️ 数据会丢失）

# ========== 查看 ==========
docker ps                               # 查看运行中的容器
docker images                           # 查看本地镜像
docker system df                        # 查看磁盘使用
docker logs -f <容器名>                  # 查看容器日志

# ========== Git 子模块 ==========
git submodule update --init             # 初始化子模块
git submodule update --remote           # 更新子模块到最新
git submodule update --remote --merge   # 更新并合并
```

## 13. 更新 CD 配置

如需修改定时触发时间，编辑 `.github/workflows/cd.yml`：

```yaml
on:
  schedule:
    # 每天 UTC 16:00（北京时间 0:00）
    - cron: '0 16 * * *'
```

Cron 表达式格式：`分 时 日 月 星期`

| 示例 | 说明 |
|-----|------|
| `'0 0 * * *'` | 每天 UTC 0:00 |
| `'0 16 * * *'` | 每天 UTC 16:00（北京时间 0:00）|
| `'0 0 * * 0'` | 每周日 UTC 0:00 |
| `'0 0 1 * *'` | 每月 1 日 UTC 0:00 |
