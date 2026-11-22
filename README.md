# 基于协同过滤的电影推荐系统

用户可以按片名搜索电影、查看详情并提交 1–5 星评分。系统根据评分找出偏好相近的用户，为当前用户生成一份排除已评分电影的 Top-N 片单。访客和评分较少的用户也能得到热门片单。

2023 年Maynooth University CS183FZ Critical Skills课程项目 *Movie Recommendation System — Based on Collaborative Filtering Algorithm*。

[课程最终报告（PDF）](docs/course-project-report.pdf)

## 1. 项目定位与pipeline

电影目录通常用搜索和统一排行榜帮助用户找片。这个项目利用用户已有的评分生成个性化排序，并在有效邻居较少时用热门电影补齐片单。系统用同一套接口连接浏览、评分和推荐更新，便于演示一次评分如何改变用户可见的结果。

```mermaid
flowchart LR
    A["输入：电影目录、历史及新增评分"] --> B["按用户组织评分并筛选相似邻居"]
    B --> C["预测未评分电影并排序"]
    C --> D["片单不足时用热门电影补齐"]
    E["访客或无有效邻居"] --> D
    D --> F["输出：Top-N 片单与推荐依据"]
```

浏览器默认请求 6 部电影。对于协同过滤结果，页面显示预测分与参与预测的邻居数。热门结果使用电影全站平均分，先按平均分降序，再按评分数降序。两类结果均排除该用户已评分的电影。符合条件的电影不足时，接口返回实际可推荐的数量。

## 2. 核心技术栈与算法框架

| 模块 | 技术与职责 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring MVC、Maven。提供目录、演示用户、推荐和评分 REST API。 |
| 持久层 | MyBatis 3、H2、MySQL Connector/J。默认使用 H2 内存库，可切换到 MySQL 8；评分由用户和电影的联合主键约束，并使用原子插入或更新。 |
| 前端 | Vue 3、JavaScript、HTML、CSS。Maven 通过 WebJars 将 Vue 与静态页面一并打包。 |
| 验证 | JUnit 5、Spring Boot Test、MockMvc。覆盖推荐、评分更新、搜索、热门回退和无效请求。 |

核心算法为基于用户的近邻协同过滤（UserCF）。使用显式评分。

1. 将历史数据组织为稀疏的用户–电影评分映射。目标用户与候选邻居至少要有两部共同评分的电影，才能在共同评分集合上计算余弦相似度。
2. 按相似度保留最多 `K` 个相似度为正的邻居，默认 `K = 20`。对候选电影，只使用评价过该电影的邻居计算加权平均。若目标用户为 $u$，候选电影为 $i$，参与预测的邻居为 $N(u,i)$，则预测分为

   $$
   \widehat r_{u,i} = \frac{\sum_{v \in N(u,i)} s(u,v)\,r_{v,i}}{\sum_{v \in N(u,i)} s(u,v)}.
   $$

3. 按预测分降序取片单，同分时优先使用邻居数较多的候选。访客、重叠评分不足或候选不足时，使用已有评分的热门电影补齐。


## 3. 快速上手与运行说明

### 环境准备

安装 JDK 17 或更高版本和 Maven 3.6.3 或更高版本。运行 `mvn -v`，确认 Maven 使用的 Java 版本满足要求。首次构建需要从 Maven 仓库下载依赖。Vue 和 H2 随应用打包，浏览器可直接访问。

在包含 `pom.xml` 的项目根目录执行：

```bash
mvn clean verify
java -jar target/movie-recommender-1.0.0.jar
```

也可直接开发启动：

```bash
mvn spring-boot:run
```

打开 [http://127.0.0.1:8080](http://127.0.0.1:8080)。首页以访客身份显示热门电影。选择「林澈」可查看协同过滤结果，选择「许晴（少量评分）」可体验热门回退。以演示用户身份打开一部电影并提交评分后，页面会刷新电影目录和推荐片单。已评分的电影可以从目录中重新打开并更新评分。

| 配置项 | 默认值或示例 | 用途 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | HTTP 端口。服务默认监听本机回环地址。 |
| `MOVIE_RECOMMENDATION_MAX_NEIGHBORS` | `20` | UserCF 计算使用的最大邻居数，需为正整数。 |
| `SPRING_PROFILES_ACTIVE` | `mysql` | 显式设置时切换到 MySQL；默认使用 H2。 |
| `MOVIE_DB_URL` | `jdbc:mysql://127.0.0.1:3306/movie_recommender` | MySQL JDBC 地址。 |
| `MOVIE_DB_USER`、`MOVIE_DB_PASSWORD` | 本地数据库凭据 | 仅 MySQL 模式需要。 |

### 可选的 MySQL 运行方式

用具有建库及建表权限的数据库账号，从项目根目录启动 MySQL 8 客户端，可加 `--default-character-set=utf8mb4` 以读取中文演示数据。运行以下命令创建数据库和表，最后一行用于导入可选的演示数据。

```sql
CREATE DATABASE IF NOT EXISTS movie_recommender CHARACTER SET utf8mb4;
USE movie_recommender;
SOURCE src/main/resources/schema.sql;
SOURCE src/main/resources/data.sql;
```

在启动应用的终端中设置数据库连接信息。以下为 PowerShell 示例，需替换账号和密码占位值。

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:MOVIE_DB_URL = 'jdbc:mysql://127.0.0.1:3306/movie_recommender'
$env:MOVIE_DB_USER = '<your-database-user>'
$env:MOVIE_DB_PASSWORD = '<your-database-password>'
java -jar target/movie-recommender-1.0.0.jar
```

### API

| 方法和路径 | 参数与返回 |
| --- | --- |
| `GET /api/movies` | 可选查询参数 `q`，按片名匹配。返回电影信息、平均分与评分数。 |
| `GET /api/users` | 返回可选的演示用户。 |
| `GET /api/recommendations` | `limit` 为 1–50，默认 6。省略 `userId` 时返回热门结果。推荐来源为 `collaborative` 或 `popular`。 |
| `PUT /api/users/{userId}/ratings/{movieId}` | JSON 示例为 `{"score": 5}`。评分为 1–5 的整数。首次提交和覆盖旧分均返回 HTTP 204。 |

## 4. 核心贡献与模块归属

作为主要维护负责人，我的职责覆盖以下模块。

- **推荐算法**（`RecommendationEngine`、`MovieService`）。负责共同评分筛选、邻居相似度计算、评分预测和 Top-N 排序，定义热门回退与已评分过滤的规则。
- **数据与接口**（`MovieStore`、`MovieController`）。负责电影、演示用户和评分的数据契约，将评分读写与推荐服务接入 REST API，处理同一评分的重复写入和无效请求。
- **交互演示**（`static/`）。负责把电影检索、详情、评分与刷新推荐串成一条可操作的流程，并在推荐卡片上展示结果依据。
- **构建与验证**（`pom.xml`、`src/test/`）。负责 Maven 构建、H2 演示数据与 MySQL 接入，验证从提交评分到推荐结果更新的主流程。

课程报告由 Jianshu Zheng、Zhihan Lin、Jinqiu Zhang、Mingtao Chen 和 Zhuofan Luo 共同完成。成绩：全组4.0。
均为2022级福州大学梅努斯国际工程学院软件工程专业学生，与爱尔兰国立大学梅努斯学院计算机科学系联合培养。现简介：
Jianshu Zheng 深圳大学人工智能学院研究生，计算机视觉研究所；
Zhihan Lin 澳门大学研究生；
Jinqiu Zhang 墨尔本大学研究生；
Mingtao Chen CityU研究生，电子商务专业；
Zhuofan Luo 国家电网员工。

## 5. 项目目录

```text
movie-recommender/
├── .gitignore
├── README.md
├── pom.xml
└── src/
    ├── main/
    │   ├── java/io/github/janshuz/movie/
    │   │   ├── MovieRecommenderApplication.java
    │   │   ├── MovieController.java
    │   │   ├── MovieService.java
    │   │   ├── MovieStore.java
    │   │   └── RecommendationEngine.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-mysql.yml
    │       ├── schema.sql
    │       ├── data.sql
    │       └── static/
    │           ├── index.html
    │           ├── app.js
    │           └── styles.css
    └── test/java/io/github/janshuz/movie/
        └── MovieRecommenderFlowTest.java
```

## 6. 状态

- 可演示。选取演示用户即可评分，尚无注册登录、访问控制、评论、反馈和管理后台。
- H2 模式使用合成电影和评分，重启会恢复初始数据。MySQL 模式可持久化评分。
- 推荐在请求时加载当前的评分记录并计算近邻，适用于小规模演示。无评分电影可以在目录中查看，收到评分后才进入推荐候选。算法使用原始评分计算相似度，用户之间的打分习惯差异会影响排序。



