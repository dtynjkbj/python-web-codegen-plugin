# FastAPI CodeGen Plugin

[English](#english) | [中文](#中文)

---

## English

A PyCharm plugin that generates CRUD code for Python web frameworks from database schemas or existing Python models.

### Features

- **Multi-ORM Support**: SQLAlchemy, SQLModel, Tortoise ORM, Peewee, Django ORM, Piccolo
- **Multi-Framework Support**: FastAPI, Flask, Django REST Framework, Sanic, Starlette
- **Smart Detection**: Automatically detects your project's ORM, framework, and response patterns
- **Granular Generation**: Choose what to generate - Models, Schemas, Services, or Routers
- **Database Sync**: Sync database schema directly to Python models
- **i18n Support**: Automatic Chinese/English detection based on PyCharm language
- **Custom Response Patterns**: Detects and adapts to your project's response wrapper style

### Installation

1. Download the latest release from [Releases](https://github.com/yourusername/fastapi-codegen-plugin/releases)
2. Open PyCharm → Settings → Plugins → ⚙️ → Install Plugin from Disk
3. Select the downloaded `.zip` file
4. Restart PyCharm

### Usage

1. Open your Python project in PyCharm
2. Go to **Tools → FastAPI CodeGen** or open the **FastAPI CodeGen** tool window (right sidebar)
3. Select generation mode:
   - **From Python Models**: Parse existing `.py` model files
   - **From Database**: Connect to database and generate from schema
4. Select target tables/models
5. Choose what to generate (Models/Schemas/Services/Routers)
6. Click **Generate**

### Generated Code Structure

```
your_project/
├── models/          # SQLAlchemy/Tortoise/ORM models
├── schemas/         # Pydantic schemas (Base/Create/Update/Response)
├── services/        # Business logic layer
└── routers/         # API routes/endpoints
```

### Supported Databases

- MySQL
- PostgreSQL

### Requirements

- PyCharm 2024.1 or later
- Java 21+

### Building from Source

```bash
./gradlew buildPlugin
```

The plugin zip will be in `build/distributions/`

---

## 中文

一个 PyCharm 插件，可以从数据库表结构或现有 Python 模型文件生成 Python Web 框架的 CRUD 代码。

### 功能特性

- **多 ORM 支持**：SQLAlchemy、SQLModel、Tortoise ORM、Peewee、Django ORM、Piccolo
- **多框架支持**：FastAPI、Flask、Django REST Framework、Sanic、Starlette
- **智能检测**：自动检测项目的 ORM、框架和响应模式
- **细粒度生成**：选择生成内容 - 模型、模式、服务或路由
- **数据库同步**：直接从数据库表结构同步到 Python 模型
- **国际化支持**：根据 PyCharm 语言自动检测中文/英文
- **自定义响应模式**：检测并适配项目的响应封装风格

### 安装方法

1. 从 [Releases](https://github.com/yourusername/fastapi-codegen-plugin/releases) 下载最新版本
2. 打开 PyCharm → 设置 → 插件 → ⚙️ → 从磁盘安装插件
3. 选择下载的 `.zip` 文件
4. 重启 PyCharm

### 使用方法

1. 在 PyCharm 中打开 Python 项目
2. 进入 **工具 → FastAPI CodeGen** 或打开 **FastAPI CodeGen** 工具窗口（右侧边栏）
3. 选择生成模式：
   - **从 Python 模型**：解析现有的 `.py` 模型文件
   - **从数据库**：连接数据库并从表结构生成
4. 选择目标表/模型
5. 选择要生成的内容（模型/模式/服务/路由）
6. 点击 **生成**

### 生成的代码结构

```
your_project/
├── models/          # SQLAlchemy/Tortoise/ORM 模型
├── schemas/         # Pydantic 模式（Base/Create/Update/Response）
├── services/        # 业务逻辑层
└── routers/         # API 路由/端点
```

### 支持的数据库

- MySQL
- PostgreSQL

### 系统要求

- PyCharm 2024.1 或更高版本
- Java 21+

### 从源码构建

```bash
./gradlew buildPlugin
```

插件 zip 文件将位于 `build/distributions/` 目录

---

## License

MIT License - see [LICENSE](LICENSE) file for details
