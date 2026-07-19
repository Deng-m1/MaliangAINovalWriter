# 🖌️ 马良AI写作 - AI智能小说创作平台


> 基于 Flutter (Web) + Spring Boot 的专业AI小说创作平台，集成先进AI模型，提供从内容创作、世界观构建到平台运维的完整工具链。

<p align="center">
  <img alt="Flutter" src="https://img.shields.io/badge/Flutter-02569B?style=for-the-badge&logo=flutter&logoColor=white"/>
  <img alt="Spring" src="https://img.shields.io/badge/Spring-6DB33F?style=for-the-badge&logo=spring&logoColor=white"/>
  <img alt="Java" src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img alt="MongoDB" src="https://img.shields.io/badge/MongoDB-47A248?style=for-the-badge&logo=mongodb&logoColor=white"/>
  <img alt="Docker" src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
  <img alt="License" src="https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge"/>
</p>

[![Star History](https://api.star-history.com/svg?repos=Deng-m1/MaliangAINovalWriter&type=Date)](https://star-history.com/#Deng-m1/MaliangAINovalWriter&Date)

马良AI写作是一个专为小说作者与平台运营者设计的智能化创作平台。它结合了强大的AI模型（支持OpenAI, Gemini, Anthropic等）与专业的在线富文本编辑器，旨在帮助作者激发灵感、提高写作效率、管理创作内容，同时为平台管理员提供了强大的后台管理与监控功能。

官网地址[AINoval](https://maliangwriter.com/)

> **AINoval (Maliang Writer / 马良写作)** is an open-source AI novel writer built for Chinese webnovels (网文). It pairs a professional rich-text editor with multi-agent AI orchestration, so authors can plan, draft, and revise 300,000+ word serials without losing coherence. The platform generates three-level outlines (novel → volume → chapter), builds structured worldbuilding settings with AI, and enforces knowledge-graph consistency across characters, plotlines, and foreshadowing to keep long-running stories on track. It supports mainstream LLMs — GPT, Claude, Gemini, and OpenRouter-compatible models — with private API keys, prompt and preset management, and full LLM observability for platform operators. Whether you write 玄幻, 都市, or any long-form fiction, AINoval (Maliang Writer, 马良写作) takes you from first idea to publishable chapters faster. Official site: https://maliangwriter.com

<img src="assets/小说开始页面.jpg" alt="小说开始页面"  />![image-20250910191841271](assets/image-20250910191841271.png)

![小说编辑区](assets/小说编辑区.jpg)

![快捷预设和ai工具栏](assets/快捷预设和ai工具栏.jpg)

![大纲与聊天](assets/大纲与聊天.jpg)

![提示词管理](assets/提示词管理.jpg)



![数据分析](assets/数据分析.jpg)



![导入小说](assets/导入小说.jpg)



## ✨ 核心特色

-   🤖 **沉浸式AI创作引擎**:
    -   支持集成主流AI模型 (`GPT`, `Claude`, `Gemini`, `OpenRouter` 等)。
    -   提供续写、扩写、润色、翻译、角色设定、大纲生成等多种AI功能。
    -   小说编辑区集成方便的AI工具栏和AI聊天功能，方便沉浸式AI创作。

-   🌍 **系统化世界观构建**:
    -   AI辅助生成结构化的世界观设定树，并支持设定节点的AI修改。
    -   支持对设定树进行增量式修改与迭代，并保存为历史快照。
    -   可视化的设定UI，UX，支持多种设定分组形式。

-   🔧 **灵活的模型与提示词管理**:
    -   **管理员**: 可配置公共大模型池，供所有用户使用。
    -   **用户**: 可配置私有API Key，使用个人专属模型。
    -   **提示词管理**: 用户可以自己配置系统提示词和用户提示词，让提示词原生发送给大模型。
    -   **预设管理**: 预设管理是指将提示词和其余指令，上下文，模型参数等关联起来，方便创作。
    
-   📊 **强大的管理与可观测性后台**:
    -   **LLM可观测性**: 详细记录每一次大模型调用，提供日志查询、统计分析（按用户/模型/功能）、成本追踪等功能。
    -   **用户管理**: 完整的用户与角色权限管理系统 (RBAC)。
    -   **系统配置**: 提供系统级的参数配置与功能开关。

## 🚀 主要功能

### ✍️ 核心写作与编辑

-   **层级化内容管理**: 采用 `作品 -> 卷 -> 章节 -> 场景` 的四级结构，清晰管理长篇内容。
-   **专业富文本编辑器**: 基于 `Flutter Quill`，支持上千章节连续滚动，提供稳定流畅的写作体验，支持丰富的格式选项。
-   **大纲总览**: 方便观察大纲内容和总览全局。
-   **多格式导入**: 支持 `txt` 导入，智能解析目录结构，并在导入的过程，有后台大模型快速生成每章大纲，快速迁移现有作品。
-   **多功能侧边栏**:
    -   **设定库**: 快速查阅和管理与当前作品关联的所有世界观设定。
    -   **片段管理**: 记录灵感片段、素材或待办事项。
    -   **章节目录**: 清晰的树状目录结构，快速定位和跳转章节。

### 🤖 智能AI助手

-   **快速开始和黄金三章**: 由设定提示词快速开始设定生成，完成设定生成后，如果对结果不太满意，则可以微调设定节点，完成后，点击黄金三章，生成前三章大纲和摘要。
-   **剧情推演 (Next Outline)**: AI根据上下文生成多个后续剧情大纲选项，辅助构思，并支持对不满意的选项进行独立重生成。
-   **摘要与扩写**:
    -   **场景摘要**: AI自动为长篇场景内容生成精炼摘要。
    -   **摘要扩写**: 将简单的摘要或大纲扩写为完整的场景内容。
-   **通用内容优化**:
    -   **AI续写**: 在当前光标位置后，由AI继续生成内容。
    -   **AI润色**: 对选中文本进行风格、语法、表达等方面的优化。
-   **AI聊天**: 在创作过程中随时与AI对话，获取灵感或解决创作难题。

### 🌍 世界观构建与设定管理

-   **结构化设定**: 支持创建角色、地点、物品、势力等多种类型的设定条目。
-   **关系网络**: 可定义设定条目之间的父子、同盟、敌对、从属等复杂关系，构建完整的世界观网络。
-   **AI一键生成设定树**: 输入核心创意或故事背景，由AI自动生成结构化的世界观设定树。系统内置多种设定生成策略（如「番茄小说网文设定」等），针对不同类型小说提供专业化的设定模板。
-   **增量式修改与迭代**: 支持对AI生成的设定树进行手动调整，或通过AI进行局部重生成和优化。
-   **历史快照**: 所有设定生成会话都将保存为历史快照，支持版本对比、复制与恢复。

![设定生成页面](assets/设定生成页面.jpg)

### 📊 写作分析与统计

-   **作者仪表盘**:
    -   **核心指标**: 实时统计总字数、总写作天数、连续写作天数等。
    -   **月度报告**: 展示当月新增字数与Token消耗。
-   **可视化图表**:
    -   **Token消耗趋势**: 通过 `fl_chart` 图表库，展示每日/每月的Token使用趋势。
    -   **功能使用分布**: 统计各项AI功能的使用频率，分析创作习惯。
    -   **模型偏好分析**: 展示不同AI模型的使用占比。
-   **近期活动**: 查看最近的AI调用记录，了解消耗详情。

### ⚙️ 高度个性化配置

-   **多模型支持**:
    -   **私有模型**: 用户可添加并管理自己的API Key，支持OpenAI、Anthropic、Gemini等多种服务商。
    -   **公共模型**: 可使用由管理员配置的公共模型池。
    -   **模型验证**: 提供API Key有效性测试功能。
-   **提示词 (Prompt) 管理**:
    -   **系统预设**: 管理员可创建丰富的系统级提示词预设。
    -   **个人预设**: 用户可创建、修改、收藏和管理自己的提示词库，实现高度个性化的内容生成。
-   **编辑器自定义**: 用户可根据偏好调整编辑器的字体、主题、布局等外观与行为。

### 🔐 管理员后台功能

管理员后台是一个独立的 Flutter Web 应用（入口 `lib/admin_main.dart`），通过 `AdminDashboardScreen` 将各个管理模块整合在同一个侧边导航中，主要包括：

-   **系统仪表盘** (`AdminOverviewScreen`): 汇总用户数、调用量、Token 消耗等指标，并通过图表组件 `UserActivityChart`、`ModelUsageChart` 展示用户活跃度与模型占比趋势。
-   **用户管理** (`UserManagementScreen` + `UserManagementTable`):
    -   支持按用户名 / 邮箱 / 手机搜索、状态筛选、分页与排序。
    -   在表格或卡片视图中查看用户状态、积分余额、角色列表、注册时间等信息。
    -   提供编辑用户信息、调整积分（`CreditOperationDialog`）、导出用户数据为 CSV 等操作。
-   **角色与权限 (RBAC)** (`RoleManagementScreen`): 通过 `RoleManagementTable` 管理角色列表，支持查看角色权限、启用 / 禁用角色等，为前台和后台功能做精细化授权。
-   **公共模型管理** (`PublicModelManagementScreen`):
    -   以供应商分组展示公共模型（`PublicModelProviderGroupCard`），例如 Gemini / OpenAI 等。
    -   每个模型支持添加 / 编辑 / 删除、启用 / 禁用开关、API Key 池验证和状态标记（已验证 / 未验证）。
    -   支持按关键字与状态过滤，并通过 tags（如 `jsonify` / `cheap` / `fast`）为后端路由策略提供信号。

    ![管理员公共模型管理](assets/管理员公共模型管理.jpg)

-   **模型定价与计费审计** (`ModelPricingManagementScreen`, `BillingAuditScreen`):
    -   对每个模型维护分层计价信息（按 provider + modelId），支持搜索、按提供商过滤与分页。
    -   通过对接后端计费仓库 `BillingRepositoryImpl`，可以结合 LLM 调用日志做成本核算与审计。
-   **LLM 可观测性** (`LLMObservabilityScreen`):
    -   基于 `LLMObservabilityRepositoryImpl` 拉取调用日志，支持按用户、提供商、模型、业务类型、TraceId / CorrelationId、时间范围、是否出错等多维过滤。
    -   提供调用列表 + 详情视图，展示请求 Prompt、模型响应、耗时、Token 用量等字段，并支持内容搜索与复制。
    -   内置多维统计：概览指标、按提供商 / 模型 / 用户的聚合统计，以及多折线趋势图（`MultiSeriesLineChart`）帮助分析流量与异常。

    ![大模型调用请求追踪](assets/大模型调用请求追踪.jpg)

-   **订阅与积分管理** (`SubscriptionManagementScreen`): 结合 `SubscriptionBloc` 和后台订阅仓库管理订阅计划、用户订阅状态以及赠送积分。
-   **模板与预设管理** (`SystemPresetsManagementScreen`, `PublicTemplatesManagementScreen`, `EnhancedTemplatesManagementScreen`): 管理系统级提示词预设、公共模板和增强模板，并为创作端的 AI 工具栏提供统一的策略来源。
-   **知识库拆书任务 & 内容审核** (`KnowledgeExtractionTaskManagementPage`, `ContentReviewScreen`): 管理 AI 拆书任务进度与结果，对用户提交的公开内容进行人工或 AI 辅助审核。

### 📖 智能剧情推演与知识库

马良AI写作集成了先进的剧情推演引擎和知识库拆书功能，为作者提供从学习成功案例到创意生成的完整闭环。

#### 🎯 剧情推演 (Next Outline)

剧情推演是一个强大的AI辅助剧情构思工具，根据当前写作上下文智能生成多个后续剧情走向：

-   **多模型抽卡机制**: 支持同时调用多个不同AI模型生成剧情方案，通过对比不同模型的创意输出，获取更丰富多样的剧情可能性，类似"抽卡"的方式选择最合适的方案。
-   **上下文智能分析**: 自动分析当前章节内容、角色关系、世界观设定和已有大纲，生成与故事逻辑高度一致的剧情推演。
-   **分支剧情生成**: 每次推演可生成多个不同走向的剧情分支，帮助作者探索不同叙事可能性，打破创作瓶颈。
-   **独立重生成**: 对不满意的某个剧情选项可独立重新生成，无需重新生成全部选项，提高创作效率。
-   **一键应用**: 选定的剧情大纲可直接应用到当前章节或场景的摘要中，无缝衔接到写作流程。

![剧情推演](assets/剧情推演.png)

![剧情推演选择多模型抽卡](assets/剧情推演选择多模型抽卡.png)

#### 📚 AI拆书与知识库

知识库拆书功能通过AI深度分析优秀小说作品，提取可复用的创作知识，帮助作者学习成功作品的叙事技巧和创作模式：

-   **多维度知识提取**: 从小说文本中智能提取多个维度的创作知识，包括：
    -   **文风叙事**: 叙事方式、文风特点、用词习惯
    -   **情节设计**: 核心冲突、悬念设计、故事节奏
    -   **人物塑造**: 角色塑造技巧与性格刻画
    -   **小说特点**: 世界观构建、金手指系统、力量体系
    -   **读者情绪**: 共鸣点、爽点布局、嗨点设计
    -   **热梗搞笑**: 流行梗、搞笑点、网络文化元素
    -   **章节大纲**: 章节结构与情节发展脉络

-   **番茄小说直连拆书**: 集成第三方 **「番茄小说拆书服务」**，只需提供小说URL即可自动抓取全文并进行AI分析，快速构建专属知识库。该功能依赖外部服务接口进行小说内容获取与预处理。

-   **自定义文本拆书**: 支持用户上传或导入自己的小说文本，由AI进行多维度分析与知识提取，适用于学习任意小说作品的创作技巧。

-   **分组批量任务**: 可同时提取多个知识维度，后台异步处理，任务完成后自动保存到个人知识库中。

-   **管理后台监控**: 管理员可通过后台监控所有拆书任务的执行状态、进度、失败重试等，支持任务级和子任务级的精细管理。

![拆书知识库](assets/拆书知识库.jpg)

**外部服务说明**：番茄小说拆书功能需要对接第三方小说内容获取服务。该服务负责从番茄小说平台爬取小说内容并返回给本系统进行AI分析。部署时需配置相应的服务地址和访问权限。

## 🛠️ 技术栈

**前端 (`AINoval`)**

| 类型           | 技术                                                                                                                              |
| :------------- | :-------------------------------------------------------------------------------------------------------------------------------- |
| **框架**       | [Flutter](https://flutter.dev/)                                                                                                   |
| **状态管理**   | [flutter_bloc](https://bloclibrary.dev/), [Provider](https://pub.dev/packages/provider)                                            |
| **UI组件**     | [Flutter Quill](https://pub.dev/packages/flutter_quill) (富文本编辑器), [fl_chart](https://pub.dev/packages/fl_chart) (图表)           |
| **本地存储**   | [Hive](https://pub.dev/packages/hive), [shared_preferences](https://pub.dev/packages/shared_preferences)                          |
| **网络**       | [Dio](https://pub.dev/packages/dio), [flutter_client_sse](https://pub.dev/packages/flutter_client_sse) (Server-Sent Events)      |
| **工具**       | [file_picker](https://pub.dev/packages/file_picker), [share_plus](https://pub.dev/packages/share_plus), [fluttertoast](https://pub.dev/packages/fluttertoast) |

**后端 (`AINovalServer`)**

| 类型           | 技术                                                                          |
| :------------- | :---------------------------------------------------------------------------- |
| **框架**       | [Spring Boot 3](https://spring.io/projects/spring-boot) (WebFlux 响应式编程) |
| **语言**       | [Java 21](https://www.oracle.com/java/)                                       |
| **AI框架**     | [LangChain4j](https://github.com/langchain4j/langchain4j)                     |
| **数据库**     | [MongoDB](https://www.mongodb.com/) (Reactive)                                |
| **向量数据库** | [Chroma](https://docs.trychroma.com/)                                         |
| **认证**       | [Spring Security](https://spring.io/projects/spring-security) + [JWT](https://jwt.io/) |
| **云服务**     | 阿里云 OSS & SMS                                                               |
| **异步任务**   | RabbitMQ                                                                      |

## 🚀 快速开始 (Docker 一键部署)

本指南面向开源用户，提供无需自行构建前端与后端的简易部署方案：一个镜像同时打包后端 JAR 与已编译的 Web 静态文件，配合 docker-compose 可一键启动，并内置可选的 MongoDB 服务。

### 目录结构

```
deploy/
  ├─ dist/
  │   ├─ ainoval-server.jar       # 预编译后端
  │   └─ web/                     # 预编译前端静态文件
  ├─ open/
  │   ├─ README.md                # 本指南
  │   ├─ Dockerfile               # 开源镜像 Dockerfile
  │   ├─ docker-compose.yml       # 开源 docker-compose
  │   ├─ production.env.example   # 环境变量示例
  │   └─ production.env           # 实际运行环境变量
```

### 系统要求

-   Docker 24+，Docker Compose v2+
-   至少 1GB 可用内存（建议 2GB+），磁盘 2GB+

### 环境准备 (Environment Setup)

#### Windows 用户 (WSL2 + Docker Desktop)

在 Windows 上，我们强烈建议通过 WSL2 (Windows Subsystem for Linux 2) 来运行 Docker 环境，以获得最佳的性能和兼容性。

1.  **安装 WSL2**:
    -   以管理员身份打开 PowerShell 或 Windows 命令提示符。
    -   运行以下命令来安装 WSL 和默认的 Ubuntu 发行版：
        ```powershell
        wsl --install
        ```
    -   根据提示重启计算机。重启后，WSL将完成安装并启动 Ubuntu。您需要为新的 Linux 环境设置用户名和密码。

2.  **安装 Docker Desktop**:
    -   访问 [Docker Desktop 官网](https://www.docker.com/products/docker-desktop/)下载适用于 Windows 的安装程序。
    -   运行安装程序，并按照向导进行操作。请确保在设置中勾选 "Use WSL 2 instead of Hyper-V (recommended)" 选项。
    -   安装完成后，Docker Desktop 会自动与您已安装的 WSL2 发行版集成。

3.  **验证安装**:
    -   打开 PowerShell 或命令提示符，运行 `docker --version` 和 `docker compose version`。如果能看到版本号，说明安装成功。
    -   您也可以在 Ubuntu (WSL) 终端中运行相同的命令进行验证。

#### Linux 用户 (Docker Engine + Docker Compose)

在 Linux 系统上，您需要安装 Docker Engine 和 Docker Compose 插件。

**对于 Ubuntu/Debian 系统:**

1.  **安装 Docker Engine**:
    
    ```bash
    # 更新软件包列表
    sudo apt-get update
    # 安装必要的依赖
    sudo apt-get install -y ca-certificates curl gnupg
    # 添加 Docker 的官方 GPG 密钥
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg
    # 设置 Docker 仓库
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
      sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    # 安装 Docker Engine
    sudo apt-get update
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin
    ```
    
2.  **安装 Docker Compose**:
```bash
    sudo apt-get install docker-compose-plugin -y
```

3.  **将当前用户添加到 `docker` 组 (可选，但推荐)**:
    这样可以避免每次都使用 `sudo`。
    ```bash
    sudo usermod -aG docker $USER
    # 重新登录或重启终端以使更改生效
    newgrp docker
    ```

**对于 CentOS/Fedora/RHEL 系统:**

1.  **安装 `yum-utils`**:
    ```bash
    sudo yum install -y yum-utils
    ```
2.  **设置 Docker 仓库**:
    ```bash
    sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
    ```
3.  **安装 Docker Engine**:
    ```bash
    sudo yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin
    ```
4.  **启动 Docker 服务**:
    ```bash
    sudo systemctl start docker
    sudo systemctl enable docker
    ```
5.  **安装 Docker Compose**:
    ```bash
    sudo dnf install docker-compose-plugin -y
    ```

### 部署步骤

1.  **准备环境变量**
    -   复制 `deploy/open/production.env.example` 到 `deploy/open/production.env`。
    -   根据你的实际情况修改变量（尤其是 Mongo, JWT, 对象存储, 代理, AI API Key 等）。
    -   将github右边的release下的ainovel.jar包下载，复制到deploy/dist目录下

2.  **构建镜像**
    在仓库根目录执行:
```bash
    docker compose -f deploy/open/docker-compose.yml build
```

3.  **启动服务**
    在仓库根目录执行:
```bash
    docker compose -f deploy/open/docker-compose.yml up -d
```
    启动后访问：`http://localhost:18080/`

4.  **初始化管理员账号** ⚠️ **重要步骤**
    服务启动后，需要运行初始化脚本创建管理员账号：
    
    **Windows 用户：**
    ```powershell
    # 在项目根目录执行
    .\deploy\init-admin.bat
    ```
    
    **Linux/Mac 用户：**
    ```bash
    # 在项目根目录执行
    chmod +x deploy/init-admin.sh
    ./deploy/init-admin.sh
    ```
    
    脚本会自动完成以下操作：
    - 创建用户名为 `admin` 的管理员账号
    - 设置初始密码为 `123456`
    - 分配管理员权限 (ROLE_ADMIN)
    - 赠送 999999 初始积分
    
    **管理后台登录：**
    - 地址：`http://localhost:18080/admin`
    - 用户名：`admin`
    - 密码：`123456`
    
    > ⚠️ **安全提示**：请在首次登录后立即修改默认密码！

### 重要环境变量（节选）

-   **端口与 JVM**: `SERVER_PORT`, `JVM_XMS`, `JVM_XMX`
-   **Mongo**: `SPRING_DATA_MONGODB_URI`（默认 `mongodb://mongo:27017/ainovel`）
-   **向量库 (Chroma)**: `VECTORSTORE_CHROMA_ENABLED`, `CHROMA_URL`
-   **JWT**: `JWT_SECRET`（务必改成强随机值）
-   **存储**: `STORAGE_PROVIDER`（local/alioss…）
-   **AI Keys**: `OPENAI_API_KEY`, `GEMINI_API_KEY`, `ANTHROPIC_API_KEY` 等

> **注意**：`production.env.example` 仅用于演示，生产环境请务必替换为你自己的安全值。

### 常见操作

-   **查看日志**: 
    ```bash
    docker compose -f deploy/open/docker-compose.yml logs -f ainoval
    ```
-   **重启服务**: 
    ```bash
    docker compose -f deploy/open/docker-compose.yml restart ainoval
    ```
-   **停止并删除容器**: 
    ```bash
    docker compose -f deploy/open/docker-compose.yml down
    ```

### 故障排除

#### 问题 1：首次启动时 MongoDB 容器显示 Error

**现象：**
```bash
[+] Running 5/5
 ✔ Network open_default        Created
 ✔ Volume "open_mongo-config"  Created
 ✔ Volume "open_mongo-data"    Created
 ✘ Container ainoval-mongo     Error
 ✔ Container ainoval-server    Created
dependency failed to start: container ainoval-mongo is unhealthy
```

**原因：**  
MongoDB 首次启动时需要初始化副本集，健康检查可能超时。这是 **正常现象**！

**解决方法：**  
等待 10-20 秒后，手动启动 ainoval-server 容器：

```bash
# 方法1：直接启动 server 容器
docker start ainoval-server

# 方法2：重新执行 docker-compose
docker compose -f deploy/open/docker-compose.yml up -d
```

查看容器状态：
```bash
docker ps
# 应该看到 ainoval-mongo (healthy) 和 ainoval-server (Up)
```

#### 问题 2：无法访问管理后台

**现象：** 访问 `http://localhost:18080/admin` 提示 401 或 404

**解决方法：**  
1. 确认服务已正常启动：`docker ps`
2. 运行管理员初始化脚本（见上文第 4 步）
3. 检查日志：`docker compose -f deploy/open/docker-compose.yml logs -f ainoval`

## 🎨 使用场景

-   **个人作者**:
    利用AI辅助功能（续写、润色、剧情推演）高效完成小说创作，通过写作分析追踪个人进度。
-   **团队协作**:
    (未来) 多人协作编辑同一部小说，共享世界观设定库，由管理员统一管理AI模型与成本。
-   **平台运营者**:
    部署平台为小圈子或公开提供服务，通过强大的后台管理用户、模型、财务和系统状态，并通过LLM可观测性洞察平台消耗。

## 🤝 贡献指南

我们欢迎所有形式的贡献！无论是提交 Issue、修复 Bug 还是贡献新功能。

1.  **Fork** 本仓库
2.  创建你的特性分支 (`git checkout -b feature/AmazingFeature`)
3.  提交你的修改 (`git commit -m 'Add some AmazingFeature'`)
4.  推送到分支 (`git push origin feature/AmazingFeature`)
5.  提交一个 **Pull Request**

## 💬 社区与支持

-   **提交 Issue**: 如果你遇到 Bug 或者有功能建议，欢迎在 [GitHub Issues](https://github.com/north-al/MaliangAINovalWriter/issues) 中提出。
-   **加入讨论**: (社区链接，QQ群1062403092)![qrcode_1757509375829](assets/qrcode_1757509375829.jpg)

## 📄 开源协议

本项目基于 [Apache License 2.0](LICENSE) 协议开源。
