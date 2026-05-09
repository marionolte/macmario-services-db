# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**macmario-services-db** is a Java/Maven library service for managing database configuration and connection pooling using an embedded H2 database. The project provides both a command-line interface and programmatic APIs for managing multi-application configuration, database credentials, and session management.

### Key Characteristics
- Single-module Maven JAR project (Java 21)
- Embedded H2 database for configuration storage
- CLI-based configuration management
- Connection pooling with a custom ring buffer
- Multi-application support (one configuration store serves multiple apps)
- Encryption support for sensitive credentials (passwords, secrets)

## Build & Deployment

### Build Commands
```bash
# Clean build
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Build with debug output
mvn -X clean package
```

The build produces:
- **Primary JAR**: `target/macmario-services-db-1.0.jar` (project JAR only)
- **Uber JAR**: `target/macmario-services-db-1.0-jar-with-dependencies.jar` (all dependencies included)
- **Dependencies**: `lib/` directory (copied during package phase)

Main class is `com.macmario.services.db.DbMain`.

### Runtime

**Interactive CLI Mode:**
```bash
java -jar target/macmario-services-db-1.0-jar-with-dependencies.jar [-d]
```
- `-d` flag enables debug output (can be repeated for higher verbosity)
- Provides a REPL prompt (`> `) for interactive commands

**Programmatic Usage:**
Import the library and use `DBConfig`, `Db`, or `DbCache` classes directly.

## Architecture & Key Components

### Core Classes

#### **DBConfig** (`src/main/java/.../db/DBConfig.java`)
The configuration management engine. Handles:
- **Multi-app support**: Each application has isolated configuration entries (apps table)
- **H2 Database initialization**: Creates schema on first run, stores configs in `config_entries` table
- **Configuration CRUD**: Methods like `setProperty()`, `getProperty()`, `getAllKeys()`, etc.
- **Application registration**: `init(app)`, `addApp()`, `isAppExist()`
- **Session management**: Basic CRUD for sessions table (create, renew, delete sessions)
- **Encryption**: Passwords and sensitive keys are auto-encrypted via `Crypt` utility

**Key Schema:**
```
config_entries: id, config_app, config_key, config_value, description, category, data_type, required, created_at, updated_at
apps: app, app_version, created_at, updated_at
sessions: session_id, user_id, data, created_at, expires_at
```

**Default Configuration Categories:**
- Application (app.name, app.version, app.debug, app.base.url)
- Database (db.type, db.host, db.port, db.name, db.user, db.password, db.pool.size)
- Proxy (Proxy.type, Proxy.host, Proxy.port, etc.)
- LDAP (ldap.host, ldap.port, ldap.user, ldap.password, TLS settings for main/server/proxy)
- Mail (mail.smtp.*, mail.imap.* for SMTP and IMAP)
- Security (session timeout, JWT secret, CORS origins)
- Features (registration, API enablement flags)

#### **Db** (`src/main/java/.../db/Db.java`)
Low-level database wrapper for arbitrary SQL queries. Features:
- **Connection pooling**: Uses `DBRingBuffer` to maintain a pool of connections
- **Query execution**: `getResult()` parses SQL results into HashMap<String, ArrayList<String>> format
- **Update execution**: `updateDB()` for INSERT/UPDATE/DELETE operations
- **Parameter replacement**: Supports filter/placeholder pattern replacement in SQL
- **Lazy loading**: Loads SQL templates from `sql.properties` resource file

#### **DbCache** (`src/main/java/.../db/DbCache.java`)
Caching wrapper around `Db`. Provides:
- Multi-level caching: `HashMap<String, HashMap<String, ArrayList<String>>>` with MD5 cache keys
- Database downtime detection (marks `dbDown` flag if DB unavailable)
- Cache invalidation on updates
- Wraps `DBConfig` for configuration access

#### **DBRingBuffer** (`src/main/java/.../db/DBRingBuffer.java`)
Custom circular buffer (generic queue) for connection pooling:
- Thread-safe via `synchronized` methods and internal locking mechanism
- Push/pop operations with wraparound semantics
- Configurable capacity (default 30 connections)
- Connection health checks via `serviceConnection()` method
- Iterator support for traversing queued connections

#### **DbMain** (`src/main/java/.../db/DbMain.java`)
CLI entry point. Implements a REPL with these commands:
- `set <key>=<value>` — Set a configuration value
- `get <key>` — Retrieve a configuration value
- `list [prefix]` — List all keys (optionally filtered by prefix)
- `add <key>=<value>;<desc>;<category>;<type>;<required>` — Add new config key
- `del <key>` — Delete a configuration key
- `app <app-name>` — Switch active application context
- `apps` — Show all registered applications
- `register <app-name>` — Register a new application
- `unregister <app-name>` — Unregister an application
- `version` — Display app version and exit
- `quit`/`q` — Exit the program

#### **Supporting Classes**
- **DbUser**: Parses JDBC URLs into components (type, host, schema, params)
- **DbAccount**: Extends `com.macmario.io.account.User` for credential storage
- **AppVersion**: Version info constants and debug flag management
- **DbConfigException**: Custom exception for configuration errors

### Database Storage

**Location**: `.config-app/configdb.h2.db` (relative to working directory)
- H2 file-based database with AUTO_SERVER mode (allows multiple JVM connections)
- Password protected (randomly generated strong password on first run, encrypted in `.config-app/initPa`)
- Default user: `sa` with empty password (overridden by settings)

### Dependencies

Key external dependencies:
- **H2 Database 2.4.240**: Embedded relational database
- **SLF4J 2.0.17**: Logging facade
- **macmario-core-io**: Internal utility library (file I/O, cryptography, password generation)

## Development Notes

### Testing
No test suite currently exists in `src/test/`. When adding tests:
- Use H2 in-memory mode for isolation (`jdbc:h2:mem:testdb`)
- Mock `Crypt` and file I/O dependencies
- Test multi-app config isolation

### Configuration Defaults
Default values are inserted on first `init(app)` if the database is empty. Modify `insertDefaults()` in `DBConfig` to change template values.

### Encryption
Sensitive fields (password, key, secret in key name) are automatically encrypted before storage. The `Crypt` utility from `macmario-core-io` is used. Encrypted values are decrypted on retrieval.

### Multi-App Architecture
- **ROOT** app is always present and cannot be deleted
- Each app has its own isolated configuration entries (query filters on `config_app` column)
- Database schema is shared; apps are differentiated by `config_app` field
- Switching apps with `setApp()` re-initializes context for that app

### Connection Pooling Strategy
- Ring buffer maintains a fixed capacity (default 30)
- Connections are checked for validity before reuse (`conn.isClosed()`)
- Dead connections are removed via `serviceConnection()`
- If buffer is full, oldest connection is evicted on new `push()`

### Logging
Uses SLF4J. Set logging level via standard SLF4J configuration (e.g., `logback.xml` or `log4j.properties` on classpath). Classes extend `AppVersion` which provides a `log(level, message)` method for debug output.

## Common Operations

### Programmatic Configuration Access
```java
DBConfig conf = new DBConfig("MYAPP");
String dbHost = conf.getDbHost();
String dbUrl = conf.getDbUrl();
Properties allAppConfig = conf.getAllKeyProperty("MYAPP", "db");
```

### Query Data via Db Class
```java
Db db = new Db("jdbc:mysql://localhost:3306/mydb", "user", "pass");
HashMap<String, ArrayList<String>> result = db.getResult("SELECT * FROM users", "");
```

### Session Management
```java
String sessionId = DBConfig.createSession("user123", "sessionData", 30); // 30 min TTL
DBConfig.deleteSession(sessionId);
```

## Known Issues

- **SQL Injection**: `Db` and `DBConfig` use string concatenation for SQL queries — not parameterized. Handle all external input carefully until this is addressed.
