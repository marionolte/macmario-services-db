/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.macmario.services.db;

import com.macmario.io.crypt.Crypt;
import com.macmario.io.crypt.GetPassword;
import com.macmario.io.file.ReadDir;
import com.macmario.io.file.ReadFile;
import com.macmario.io.file.WriteFile;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;

/**
 *
 * @author SuMario
 */
public class DBConfig extends AppVersion {
    
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(DBConfig.class);
    
    private static ReadDir dir = null;
    private static Crypt crypt = new Crypt();
    private static String defapp = "ROOT";
    private static int dbSchemaVersion=1;
    private static boolean dbSchemaVersionUpdate=false;

    // H2 file-based DB stored inside the webapp's data directory
    private static       String DB_NAME     = "configdb";
    private static       String DB_USER     = "sa";
    private static       String DB_PASSWORD = "";

    private static String jdbcUrl;
    private static boolean initialized = false; 
    
    static {
        String base = System.getProperty("user.dir") + "/.config-app/";
        dir = new ReadDir(base);
        if ( ! dir.isDirectory() ) {
            boolean b = dir.mkdirs();
            log.info("BASE Directory created: ", b +"("+base+")");
        }
        ReadFile rf = new ReadFile( dir.getAbsolutePath()+"/initPa");
        if ( rf.isReadableFile() ) {
            DB_PASSWORD=crypt.getUnCrypted( rf.readOut().toString() );
        } else {
            DB_PASSWORD=GetPassword.getStrongPassword();
            WriteFile wf = new WriteFile(rf); 
                      wf.append(crypt.getCrypted(DB_PASSWORD));
        }
        jdbcUrl="jdbc:h2:file:" + base + "/" + DB_NAME
                + ";AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=TRUE;MODE=MYSQL;";
        
    }
    
    public DBConfig(String ap) {
        
        defapp=( isNotNullOrEmpty(ap) )?ap:this.defapp;
        init(defapp);
        if ( isNotNullOrEmpty(ap) ) init(ap);
        
    }

    private static HashMap<String, String> appMap=new HashMap<>();
    
    /** Call once at application startup. */
    public static boolean init() { return init(defapp); }
    public static boolean init(String app) {
        app=(isNullOrEmpty(app))?defapp:app.toUpperCase();
        boolean b=getBoolean( appMap.getOrDefault(app, "0" ) );
        log.info("DBConfig::init ("+app+") init:"+initialized+":  appinit:"+b);
        
        if (initialized  && b ) return true;
        appMap.put(app, "0" );
        
        // Store DB files in <tomcat-work>/config-data/ so they survive redeployments
        String base = System.getProperty("user.dir");
        ReadDir dir = new ReadDir(base);
        if ( ! dir.getPath().endsWith("/.config-app") ) {
            base = base + "/.config-app";
            dir = new ReadDir(base);
        }            
        if ( ! dir.isDirectory() ) {
            boolean b1 = dir.mkdirs();
            log.info("BASE Directory created: ", b1 +"("+base+")");
        }
        jdbcUrl = "jdbc:h2:file:" + base + "/" + DB_NAME
                + ";AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=TRUE;MODE=MYSQL;";

        log.info("H2 JDBC URL: {}", jdbcUrl);

        // Load H2 driver
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
             initialized=false;
             return initialized;
        }

        log.info("DBConfig::init - now create schema "+app);
        // create config_entries if not exist
        createSchema();
        log.info("DBConfig::init - now fill empty schema");
        seedDefaultsIfEmpty(app);
        log.info("DatabaseManager initialised successfully.");
        //    log.info("allKeys"+getAllKeyProperty(app));
        initialized=true;
        appMap.put(app, "1");
        return getBoolean( appMap.get(app) );
    }
    
    public static void setApp(String ap) {
        if ( isAppExist(ap) ) {
            init(ap);
        }    
    }
    
    // ------------------------------------------------------------------ //
    //  Connection                                                          //
    // ------------------------------------------------------------------ //

    private static Connection getConnection() throws SQLException {
        //if ( ! initialized )  init();
        if (jdbcUrl == null) {
            throw new IllegalStateException("DatabaseManager.init() has not been called yet.");
        }
        return DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
    }
    public static Connection getDbConnection() throws SQLException {
        //init();
        return DriverManager.getConnection(getDbUrl(), getDbUsername(), getDbPassword());
    }
    public static Connection getDbConnection(String app) throws SQLException {
        //init(app);
        return DriverManager.getConnection(getDbUrl(app), getDbUsername(app), getDbPassword(app));
    }
    
    
    
    static String getMainJDBC(){ return jdbcUrl; }
    static String getMainUser(){ return DB_USER; }
    static String getMainPass(){ return DB_PASSWORD; }
    static String getMainName(){ return DB_NAME; } 

    // ------------------------------------------------------------------ //
    //  Schema                                                              //
    // ------------------------------------------------------------------ //

    
    private static void createSchema() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS config_entries (
                id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                config_app  VARCHAR(255) NOT NULL DEFAULT 'ROOT',
                config_key  VARCHAR(255) NOT NULL,
                config_value VARCHAR(2048),
                description VARCHAR(1024),
                category    VARCHAR(128) NOT NULL DEFAULT 'General',
                data_type   VARCHAR(32)  NOT NULL DEFAULT 'STRING',
                required    BOOLEAN      NOT NULL DEFAULT FALSE,
                created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                CONSTRAINT uq_app_key UNIQUE (config_app, config_key)
            )
            """;
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute(ddl);
            log.info("schema table ready.");
            migrateSchema(c);
            initSessionTable(c);
            initAppsTable(c);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create schema", e);
        }
    }

    private static void migrateSchema(Connection c) {
        // Migration v1→v2: replace single-column unique on config_key with composite (config_app, config_key).
        // H2 auto-names inline UNIQUE constraints, so we query INFORMATION_SCHEMA to find them.
        try {
            ArrayList<String> toDrop = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT tc.CONSTRAINT_NAME " +
                    "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc " +
                    "JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE ccu " +
                    "  ON tc.CONSTRAINT_NAME = ccu.CONSTRAINT_NAME " +
                    " AND tc.TABLE_SCHEMA   = ccu.TABLE_SCHEMA " +
                    "WHERE tc.TABLE_NAME      = 'CONFIG_ENTRIES' " +
                    "  AND tc.CONSTRAINT_TYPE = 'UNIQUE' " +
                    "  AND ccu.COLUMN_NAME    = 'CONFIG_KEY' " +
                    "  AND tc.CONSTRAINT_NAME <> 'UQ_APP_KEY' " +
                    "GROUP BY tc.CONSTRAINT_NAME HAVING COUNT(*) = 1")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) toDrop.add(rs.getString(1));
            }
            for (String name : toDrop) {
                try (Statement st = c.createStatement()) {
                    st.execute("ALTER TABLE config_entries DROP CONSTRAINT IF EXISTS \"" + name + "\"");
                    log.info("Dropped legacy unique constraint: {}", name);
                } catch (SQLException e) {
                    log.warn("Could not drop constraint {}: {}", name, e.getMessage());
                }
            }
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE config_entries ADD CONSTRAINT IF NOT EXISTS uq_app_key UNIQUE (config_app, config_key)");
            } catch (SQLException e) {
                log.warn("Could not add composite unique constraint: {}", e.getMessage());
            }
        } catch (SQLException e) {
            log.warn("Schema migration attempt failed: {}", e.getMessage());
        }
    }
    
    private static void initAppsTable(Connection con) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS apps (
                app          VARCHAR(64)  NOT NULL,
                app_version  VARCHAR(32)  NOT NULL,
                created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                updated_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
            )
        """;
       
        try ( Statement stmt = con.createStatement()) {
            stmt.execute(sql);
            log.info("session table ready.");
        }
        
        
        addApp("ROOT");
    }
    
    public static String getAppList() {
        StringBuilder sw = new StringBuilder("ROOT");
        ArrayList<String> ar = new ArrayList<>();
        String sql = """
                     select * FROM apps
                     """;
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs =ps.executeQuery();
                log.debug("DBConfig::APPS : "+rs.toString());
                while (  rs.next() ) {
                    String k = rs.getString("app");
                    if ( ! ar.contains(k) && ! k.equals("ROOT") ) {
                        ar.add(k);
                    }
                    
                    int i = getInt( rs.getString("app_version") );
                    if ( dbSchemaVersion > i ) {
                        dbSchemaVersionUpdate=true;
                    }
                }
                rs.close();
                ps.close();
                c.commit();
        } catch (SQLException e) {
            System.out.println(sql+"\nerror - "+e.getMessage()); e.printStackTrace();
                log.error("Failed to select configuration", e);
        }
        
        for( String k: ar) { sw.append(",").append(k); }
        
        return sw.toString();
    }
    
    public static void initSessionTable(Connection con) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS sessions (
                session_id   VARCHAR(64)  PRIMARY KEY,
                user_id      VARCHAR(64),
                data         TEXT,
                created_at   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                expires_at   TIMESTAMP    NOT NULL
            )
        """;
        try ( Statement stmt = con.createStatement()) {
            stmt.execute(sql);
            log.info("session table ready.");
        }
    }

    // ------------------------------------------------------------------ //
    //  Default seed data                                                   //
    // ------------------------------------------------------------------ //

    private static void seedDefaultsIfEmpty(String app) {
        app = ( isNullOrEmpty(app) )?"ROOT":app;
        String countSql = "SELECT COUNT(*) FROM config_entries where config_app='"+app+"'";
        try (Connection c = getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(countSql)) {

            rs.next();
            int count = rs.getInt(1);
            if (count > 0) {
                log.info("Database already contains {} entries – skipping seed.", count);
                return;
            }
        } catch (SQLException e) {
            log.error("Cannot check row count", e);
            return;
        }

        log.info("Database is empty – inserting default configuration …");
        insertDefaults(app);
        addApp(app);
    }

    public static void addConfigs(Object[][] obj) {
        
         String sql = """
            INSERT INTO config_entries
                (config_app,config_key, config_value, description, category, data_type, required)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

         try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            for (Object[] row : obj) {
                if ( row.length >= 7 ) {
                    ps.setString(1, (String) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setString(4, (String) row[3]);
                    ps.setString(5, (String) row[4]);
                    ps.setString(6, (String) row[5]);
                    ps.setBoolean(7, (Boolean) row[6]);
                    ps.addBatch();
                }
            }
            int[] result = ps.executeBatch();
            log.info("Inserted {} default configuration entries.", result.length);

        } catch (SQLException e) {
            log.error("Failed to insert default configuration", e);
        }
         
    }
    
    public static void addApp(String app) {
        String sql = "INSERT INTO apps (app,app_version) VALUES ('"+app+"','"+dbSchemaVersion+"')";
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql) ) {
            
              ps.execute();
            
        }catch (SQLException e) {
            System.out.println(e.getMessage()+"");
            log.error("Failed to insert application", e);
        }
    }
     public static void unApp(String app) {
        String sql = "delete from apps where app='"+app+"'";
        String sql1= "delete from config_entries where config_app='"+app+"'";
        try (Connection c = getConnection() ) {
             PreparedStatement ps = c.prepareStatement(sql);
                               ps.execute();
                               
                               ps = c.prepareStatement(sql1);
                               ps.execute();
            
        }catch (SQLException e) {
            log.error("Failed to insert application", e);
        }
    }
    
    private static void insertDefaults(String app) {
        /*String sql = """
            INSERT INTO config_entries
                (config_app, config_key, config_value, description, category, data_type, required)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        */
        Object[][] defaults = {
            // Application
            {app, "app.name",           "Config App",                    "Display name of the application",  "Application", "STRING",  true},
            {app, "app.version",        "1.0",                           "Current application version",      "Application", "STRING",  false},
            {app, "app.debug",          "false",                         "Enable debug mode (true/false)",   "Application", "BOOLEAN", false},
            {app, "app.base.url",       "http://localhost:18080/config", "Base URL of the application",      "Application", "URL",     true},

            // Database
            {app, "db.type",             "mysql",          "Database server hostname",                  "Database",    "STRING",  true},
            {app, "db.host",             "localhost",      "Database server hostname",                  "Database",    "STRING",  true},
            {app, "db.port",             "3306",           "Database server port",                      "Database",    "INTEGER", true},
            {app, "db.name",             "app",            "Database / schema name",                    "Database",    "STRING",  true},
            {app, "db.user",             "nb",             "Database username",                         "Database",    "STRING",  true},
            {app, "db.password",         "changeit",       "Database password",                         "Database",    "PASSWORD",true},
            {app, "db.pool.size",        "10",             "Maximum number of DB pool connections",     "Database",    "INTEGER", false},

            {app, "Proxy.type",             "transparent",    "Proxy server type",                      "Proxy",    "STRING",  true},
            {app, "Proxy.host",             "localhost",      "Proxy server hostname",                  "Proxy",    "STRING",  true},
            {app, "Proxy.port",             "1080",           "Proxy server port",                      "Proxy",    "INTEGER", true},
            {app, "Proxy.name",             "app",            "Proxy / schema name",                    "Proxy",    "STRING",  true},
            {app, "Proxy.user",             "admin",          "Proxy username",                         "Proxy",    "STRING",  true},
            {app, "Proxy.password",         "changeit",       "Proxy password",                         "Proxy",    "PASSWORD",true},
            {app, "Proxy.apps",         "HTTP,HTTPS,SFTP,SMTP,IMAP",    "Proxy application",                      "Proxy",    "STRING",  true},
            
            // Ldap
            {app, "ldap.host",      "ldap.example.com", "Ldap host",                          "Ldap",        "STRING",  false},
            {app, "ldap.port",      "389",              "Ldap port",                          "Ldap",        "INTEGER", false},
            {app, "ldap.user",      "cn=admin",         "Ldap username / from address",              "Ldap",        "STRING",  false},
            {app, "ldap.password",  "",                 "Ldap password",                             "Ldap",        "PASSWORD",false},
            {app, "ldap.tls",       "true",             "Use STARTTLS",                              "Ldap",        "BOOLEAN", false},
            {app, "ldap.server.config",    "",                 "Ldap server config",                        "Ldap",        "STRING",false},
            {app, "ldap.server.host",      "ldap.example.com", "Ldap server host",                          "Ldap",        "STRING",  false},
            {app, "ldap.server.port",      "389",              "Ldap server port",                          "Ldap",        "INTEGER", false},
            {app, "ldap.server.user",      "cn=admin",         "Ldap username / from address",              "Ldap",        "STRING",  false},
            {app, "ldap.server.password",  "",                 "Ldap password",                             "Ldap",        "PASSWORD",false},
            {app, "ldap.server.tls",       "true",             "Use STARTTLS",                              "Ldap",        "BOOLEAN", false},
            {app, "ldap.proxy.config",    "",                 "Ldap proxy config",                        "Ldap",        "STRING",false},
            {app, "ldap.proxy.host",      "ldap.example.com",  "Ldap server host",                          "Ldap",        "STRING",  false},
            {app, "ldap.proxy.port",      "389",               "Ldap server port",                          "Ldap",        "INTEGER", false},
            {app, "ldap.proxy.user",      "cn=proxyadmin",     "Ldap username / from address",              "Ldap",        "STRING",  false},
            {app, "ldap.proxy.password",  "",                  "Ldap password",                             "Ldap",        "PASSWORD",false},
            {app, "ldap.proxy.tls",       "true",              "Use STARTTLS",                              "Ldap",        "BOOLEAN", false},

            // Mail 
            {app, "mail.smtp.host",      "smtp.example.com","SMTP server host",                          "Mail",        "STRING",  false},
            {app, "mail.smtp.port",      "587",              "SMTP server port",                          "Mail",        "INTEGER", false},
            {app, "mail.smtp.user",      "noreply@example.com","SMTP username / from address",            "Mail",        "STRING",  false},
            {app, "mail.smtp.password",  "",                 "SMTP password",                             "Mail",        "PASSWORD",false},
            {app, "mail.smtp.tls",       "true",             "Use STARTTLS",                              "Mail",        "BOOLEAN", false},

            {app, "mail.imap.host",      "imap.example.com", "IMAP server host",                          "Mail",        "STRING",  false},
            {app, "mail.imap.port",      "993",              "IMAP server port",                          "Mail",        "INTEGER", false},
            {app, "mail.imap.user",      "noreply@example.com","IMAP username / from address",            "Mail",        "STRING",  false},
            {app, "mail.imap.password",  "",                 "IMAP password",                             "Mail",        "PASSWORD",false},
            {app, "mail.imap.tls",       "true",             "Use STARTTLS",                              "Mail",        "BOOLEAN", false},

            // Security
            {app, "security.session.timeout", "1800",        "HTTP session timeout in seconds",           "Security",    "INTEGER", false},
            {app, "security.jwt.secret",      "",            "JWT signing secret key",                    "Security",    "PASSWORD",true},
            {app, "security.allowed.origins", "http://localhost:8080","CORS allowed origins (comma-sep)","Security",    "STRING",  false},

            // Features
            {app, "feature.registration", "true",            "Allow new user registration",               "Features",    "BOOLEAN", false},
            {app, "feature.api.enabled",  "true",            "Enable REST API endpoints",                 "Features",    "BOOLEAN", false},
        };
        
        addConfigs(defaults);

    }

    public static void shutdown() {
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute("SHUTDOWN");
            log.info("H2 database shut down cleanly.");
        } catch (SQLException e) {
            log.warn("Error during H2 shutdown: {}", e.getMessage());
        }
    }
    
    public static boolean delProperty(String app, String key) {
        init(app);
        boolean b = false;
        
        String sql = "delete from config_entries where config_key='"+key.replaceAll(" ", "")+"' and config_app='"+app+"'";
         try ( Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            b=ps.execute();
            ps.close();
            c.commit();
            invalidateCache();
        } catch (SQLException e) {
            log.error("Failed to delete configuration of "+key, e);
        }   
        return b;
    }
    
    
    public static boolean isAppExist(String app) {
        app = ( isNullOrEmpty(app) )?"ROOT":app;
        if ( app.equals("ROOT") ) { return true; }
        String countSql = "SELECT COUNT(*) FROM apps where app='"+app+"'";
        try (Connection c = getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(countSql)) {

            rs.next();
            int count = rs.getInt(1);
            if (count > 0) {
                log.info("app "+app+" is registered ");
                return true;
            }
        } catch (SQLException e) {
            log.error("Cannot check row count for apps ", e);
            
        }
        return false;
    }
    
    public static boolean addProperty(String app, String key, String value, String desc,String cat, String typ,String require) {
        init(app);
        boolean b = false;
        if ( isAppExist(app) ) {
         String sql = "INSERT INTO config_entries "
                 + "(config_app, config_key, config_value, description, category, data_type, required)"
                 + " VALUES ('"+app+"','"+key+"', '"+value+"', '"+desc+"',"
                 + "'"+cat+"', '"+typ+"', "+getBoolean(require)+")            ";
         try ( Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            b=ps.execute();
            ps.close();
            c.commit();
            invalidateCache();
         } catch (SQLException e) {
            log.error("Failed to insert configuration "+key+"="+value, e);
         }
        } else {
            System.out.println("ERROR: application "+app+" is not registered");
        } 
        return b;
    }
    
    
    public static boolean setProperty(String app, String key, String value) {
        init(app);
        boolean b = false;
        if ( key.toLowerCase().contains("password") ) {
            if ( ! crypt.isCrypted(value) ) {
                value=crypt.getCrypted(value);
            }
        }
        String sql ="UPDATE config_entries SET config_value=? WHERE config_app=? and config_key=?";
        try ( Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, app);
            ps.setString(3, key);
            b=ps.execute();
            ps.close();
            c.commit();
            invalidateCache();
        } catch (SQLException e) {
            log.error("Failed to update exist configuration", e);
        }
        return b;
    }
    public  static Properties getProperty(String app, String part) {
        Properties p = new Properties();
        if ( isNullOrEmpty(p) ) { return p;} 
        String sql = """
            SELECT config_app,config_key,config_value from config_entries
            """;
        
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs =ps.executeQuery();
                
                while (  rs.next() ) {
                    String a = rs.getString("config_app");
                    String k = rs.getString("config_key");
                    log.info("db-key k:"+k+":");
                    String value =  rs.getString("config_value");
                    if ( a.equals(app) && k.contains(part) ) {
                        if ( k.toLowerCase().contains("password") && isNotNullOrEmpty(value)) {
                            value=crypt.getUnCrypted(value);                        
                        }
                        p.put(k,value);
                    }                    
                }
                
                rs.close();
                ps.close();
                c.commit();
            } catch (SQLException e) {
                log.error("Failed to select configuration", e);
            }
        return p;
    }

    private static final HashMap<String, Properties> propCache = new HashMap<>();
    public static Properties getConfig(String app, String part) {
        init();
        String cacheKey = app + ":" + part;
        if (!propCache.containsKey(cacheKey)) {
            Properties p = getProperty(app, part);
            propCache.put(cacheKey, p);
        }
        return propCache.get(cacheKey);
    }

    private static void invalidateCache() { propCache.clear(); }
    public static String getDbUrl() { return getDbUrl(defapp); }
    public static String getDbUrl(String app) {
        Properties p = getConfig(app, "db");
        
        return "jdbc:"+p.getProperty("db.driver", "mysql")+"://"+
                p.getProperty("db.host", "localhost")+":"+
                p.getProperty("db.port", "3306")+"/"+
                p.getProperty("db.name","basedb")
                ;
    }

    public static String getDbUsername() { return getDbUsername(defapp); } 
    public static String getDbUsername(String app) { 
        Properties p = getConfig(app, "db");
        return p.getProperty("db.user", ""); 
    }
    
    public static void setDbUsername(String value) {  setDbUsername(defapp,value); }
    public static void setDbUsername(String app, String value) { 
        if (value != null ) { setProperty(app, "db.user", value); }
    }
    
    public static String getDbName() { return getDbName(defapp); }
    public static String getDbName(String app) { 
        Properties p = getConfig(app, "db");
        return p.getProperty("db.name", ""); 
    }
    public static void setDbName(String value) { setDbName(defapp, value); }
    public static void setDbName(String app, String value) { 
        if (value != null ) { setProperty(app, "db.name", value); }
    }

    public static String getDbPassword() { return getDbPassword(defapp); }
    public static String getDbPassword(String app) { 
        Properties p = getConfig(app, "db");
        String a = p.getProperty("db.password", "");
        log.info("DB password:"+a+":");
        return a; 
    }
    public static void setDbPassword(String value) { setDbPassword(defapp, value); }
    public static void setDbPassword(String app, String value) { 
        if ( value != null ) { setProperty(app, "db.password", value); }
    }
    
    public static String getDbDriver() { return getDbDriver(defapp); } 
    public static String getDbDriver(String app) { 
        Properties p = getConfig(app,"db");
        return p.getProperty("db.driver", "mysql"); 
    }
    public static void setDbDriver(String value) { setDbDriver(defapp,value); }
    public static void setDbDriver(String app, String value) { 
        if ( isNotNullOrEmpty(value) ) { setProperty(app, "db.driver", value); }
    }
    public static String getDbHost() { return getDbHost(defapp); }
    public static String getDbHost(String app) { 
        Properties p = getConfig(app, "db");
        return p.getProperty("db.host", "localhost"); 
    }
    public static void setDbHost(String value) { setDbHost(defapp,value); }
    public static void setDbHost(String app,String value) { 
        if (value != null ) { setProperty(app, "db.host", value); }
    }
    
    public static String getDbPort() { return getDbPort(defapp); } 
    public static String getDbPort(String app) { 
        Properties p = getConfig(app, "db");
        return p.getProperty("db.port", "3306"); 
    }
    
    public static void setDbPort(String value) { setDbPort(defapp,value); }
    public static void setDbPort(String app, String value) { 
        if (value != null && getInt(value) >0 ) { setProperty(app, "db.port", value); }
    }
    
    public static String getDbJDBC() { return getDbJDBC(defapp); }
    public static String getDbJDBC(String app) { 
        app=( isNullOrEmpty(app) )? defapp: app; 
        String a =  "jdbc:"+getDbDriver(app)+"://"+
                    getDbHost(app)+":"+getDbPort(app)+"/"+
                    getDbName(app);
        log.info("DB jdbc =>"+a+"<= ");
        return a ;
    }
    public static String getDbSchema()           {   return getDbName();     }
    public static String getDbSchema(String app) {   return getDbName(app);  }
    public static void setDbSchema(String value) {  setDbSchema(defapp,value); }
    public static void setDbSchema(String app, String value) { 
        if (value != null ) { setProperty(app, "db.name", value); }
    }
    
    
    public static String getDbDriverClass() { return getDbDriverClass(getDbDriver()); }
    
    public static String getDbDriverClass(String clname) { 
        String ret="";
        if ( isNotNullOrEmpty(clname) ) {
            switch(clname) {
                case "mysql"      : { ret="com.mysql.cj.jdbc.Driver"; break; }
                case "postgresql" : { ret="org.postgresql.Driver"; break; } 
                case "oracle"     : { ret="oracle.jdbc.driver.OracleDriver"; break; }
                default: { ret="org.h2.Driver"; break; }
            }   
        }
        return ret; 
    }
    
    String getValue(String k) { return getValue(defapp,k); }
    String getValue(String app, String k) {
        if ( ! init(app) ) { 
            log.error("Application "+app+" not registered" );
            return null; 
        }
        k=k.replaceAll(" ", "");
        StringBuilder sw=new StringBuilder();
        String sql = "SELECT config_key,config_value FROM config_entries where config_app='"+app+"';";
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs =ps.executeQuery();
                
                while (  rs.next() ) {
                    String k1 = rs.getString("config_key");
                    if ( k.toLowerCase().equals(k1.toLowerCase())) {
                        String v = rs.getString("config_value");
                        sw.append(k).append(": ").append(v).append("\n");
                    }    
                }
                rs.close();
                ps.close();
                c.commit();
        } catch (SQLException e) {
            System.out.println(sql+"\nerror - "+e.getMessage()); e.printStackTrace();
                log.error("Failed to select configuration", e);
        }
        return sw.toString();
    }
    boolean setValue(String app,String k,String v) {
        app  = ( isNullOrEmpty(app) ) ? defapp: app;         
        if ( ! init(app) ) {
            log.error("Application "+app+" not registered" );
            return false;
        }
        
        k=k.replaceAll(" ", "");
        StringBuilder sw=new StringBuilder();
        String sql = "update config_entries set config_value='"+v+"' where config_key='"+k.replaceAll(" ", "")+"' and config_app='"+app+"'";
        //System.out.print("sql:"+sql);
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                int eu =ps.executeUpdate();
                //System.out.println(".. "+eu);
                ps.close();
                c.commit();
                return ( eu > 0 );
        } catch (SQLException e) {
            System.out.println(sql+"\nerror - "+e.getMessage()); e.printStackTrace();
                log.error("Failed to select configuration", e);
        }
        return false;
    }

    public static Properties getAllKeyProperty() { return getAllKeyProperty(defapp,""); }
    public static Properties getAllKeyProperty(String app) { return getAllKeyProperty(app,""); }
    public static Properties getAllKeyProperty(String app, String part) {
        app  = ( isNullOrEmpty(app) ) ? defapp: app; 
        Properties sw=new Properties();
        if ( initialized ) {
            if ( ! init(app) ) {
                log.error("Application "+app+" not registered" );
                return sw;
            }
        }    
        part = ( isNullOrEmpty(part) )?"":part;        
        
        String sql = "SELECT config_app,config_key,config_value FROM config_entries";
        
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs =ps.executeQuery();
                
                while (  rs.next() ) {
                    String a = rs.getString("config_app");
                    String k = rs.getString("config_key");
                    String v = rs.getString("config_value");
                    if ( app.equals(a) ) {
                        if ( k.startsWith(part) ) {
                            sw.put(k, v);
                        }
                    }
                }
                
                rs.close();
                ps.close();
                c.commit();
            } catch (SQLException e) {
                log.error("Failed to select configuration", e);
            }
        
        return sw;
    }
    
    public static String getAllKeys() { return getAllKeys(defapp,""); }
    public static String getAllKeys(String app,String part) {
        Properties p = getAllKeyProperty(app,part);
        Enumeration<String> e = sortProp(p);
        StringBuilder sw=new StringBuilder();
        
        while ( e.hasMoreElements() ) {
            String k = e.nextElement();
                    if ( k.startsWith(part) ) {
                        sw.append("\t").append(k).append(": ");
                        final String k1=k.toLowerCase();
                        if (    k1.contains("password")
                             || k1.contains("key")
                             || k1.contains("secret")
                           ) {
                            String v=p.getProperty(k, "");
                            sw.append("<").append(  (isNotNullOrEmpty(v)?"":" NOT") ).append(" SET >");
                        } else  {
                            sw.append(p.getProperty(k, ""));
                        }
                        sw.append("\n");
                    }
        }        
        return sw.toString();
    }
    
    private static Enumeration<String> sortProp(Properties p) {
        Enumeration keysEnum = p.keys();
        Vector<String> keyList = new Vector<String>();
        while(keysEnum.hasMoreElements()){
          keyList.add((String)keysEnum.nextElement());
        }
        Collections.sort(keyList);
        return keyList.elements();  
    }
    
    
    // CREATE — save a new session
    public static String createSession(String userId, String data, int ttlMinutes) throws SQLException {
        String sessionId = UUID.randomUUID().toString();
        String sql = "INSERT INTO sessions (session_id, user_id, data, expires_at) VALUES (?, ?, ?, ?)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            ps.setString(2, userId);
            ps.setString(3, data);
            ps.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now().plusMinutes(ttlMinutes)));
            ps.executeUpdate();
        }
        return sessionId;
    }

    // READ — retrieve a valid (non-expired) session
    public static ResultSet getSession(String sessionId) throws SQLException {
        String sql = "SELECT * FROM sessions WHERE session_id = ? AND expires_at > CURRENT_TIMESTAMP";

        Connection con = getConnection();
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setString(1, sessionId);
        return ps.executeQuery();
    }

    // UPDATE — refresh session expiry
    public void renewSession(String sessionId, int ttlMinutes) throws SQLException {
        String sql = "UPDATE sessions SET expires_at = ? WHERE session_id = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().plusMinutes(ttlMinutes)));
            ps.setString(2, sessionId);
            ps.executeUpdate();
        }
    }

    // DELETE — invalidate/logout
    public static void deleteSession(String sessionId) throws SQLException {
        String sql = "DELETE FROM sessions WHERE session_id = ?";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            ps.executeUpdate();
        }
    }

    // CLEANUP — purge expired sessions
    public static void purgeExpiredSessions() throws SQLException {
        String sql = "DELETE FROM sessions WHERE expires_at <= CURRENT_TIMESTAMP";

        try (Connection con = getConnection();
             Statement stmt = con.createStatement()) {
            stmt.execute(sql);
        }
    }
    
    public static DBConfig getInstance(String app){ 
        return getInstance( app,  GetPassword.getStrongPassword() );
    }
    public static DBConfig getInstance(){ 
        return getInstance( defapp, GetPassword.getStrongPassword() );
    }
    public static DBConfig getInstance(String app,String pw){
        pw=(pw==null)?"":pw;
        app=(app == null || app.isEmpty() )?defapp:app;
        return getInstance( 
                app,
                "jdbc:h2:file:" + dir.getFQDNDirName() + "/" + DB_NAME
                                + ";AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=TRUE;MODE=MYSQL;",
                "sa",
                pw 
        );
    }
    public static DBConfig getInstance(String app, String h2Jdbc, String h2User, String h2Pass) {
          DBConfig conf=new DBConfig(app);
                   conf.jdbcUrl=h2Jdbc;
                   conf.DB_USER=h2User;
                   conf.DB_PASSWORD=h2Pass;
          
          return conf;
    }
    
    public static String getAppPassword(String app){
        app=(app == null || app.isEmpty() )?defapp:app;
        DBConfig conf = new DBConfig(app);
        String k="master.password";
        
        String pw=conf.getValue(app, k);
        if ( pw == null || pw.isEmpty() ) {
               pw=GetPassword.getStrongPassword();
               conf.setValue(app, k, pw);
        }
        return pw;
    }
}
