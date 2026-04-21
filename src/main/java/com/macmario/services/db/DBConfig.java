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
import java.util.Collections;
import java.util.Enumeration;
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

    // H2 file-based DB stored inside the webapp's data directory
    private static       String DB_NAME     = "configdb";
    private static       String DB_USER     = "sa";
    private static       String DB_PASSWORD = "";

    private static String jdbcUrl;
    private static final AtomicBoolean initialized = new AtomicBoolean(false); 
    
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

    /** Call once at application startup. */
    public static void init() {
        if (initialized.getAndSet(true)) return;

        // Store DB files in <tomcat-work>/config-data/ so they survive redeployments
        String base = System.getProperty("user.dir");
        ReadDir dir = new ReadDir(base);
        if ( ! dir.getPath().endsWith("/.config-app") ) {
            base = base + "/.config-app";
            dir = new ReadDir(base);
        }            
        if ( ! dir.isDirectory() ) {
            boolean b = dir.mkdirs();
            log.info("BASE Directory created: ", b +"("+base+")");
        }
        jdbcUrl = "jdbc:h2:file:" + base + "/" + DB_NAME
                + ";AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=TRUE;MODE=MYSQL;";

        log.info("H2 JDBC URL: {}", jdbcUrl);

        // Load H2 driver
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("H2 driver not found", e);
        }

        createSchema();
        seedDefaultsIfEmpty();
        log.info("DatabaseManager initialised successfully.");
        log.info("allKeys"+getAllKeyProperty());
    }
    
    // ------------------------------------------------------------------ //
    //  Connection                                                          //
    // ------------------------------------------------------------------ //

    public static Connection getConnection() throws SQLException {
        init();
        if (jdbcUrl == null) {
            throw new IllegalStateException("DatabaseManager.init() has not been called yet.");
        }
        return DriverManager.getConnection(jdbcUrl, DB_USER, DB_PASSWORD);
    }
    public static Connection getDbConnection() throws SQLException {
        init();
        return DriverManager.getConnection(getDbUrl(), getDbUsername(), getDbPassword());
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
                config_key  VARCHAR(255) NOT NULL UNIQUE,
                config_value VARCHAR(2048),
                description VARCHAR(1024),
                category    VARCHAR(128) NOT NULL DEFAULT 'General',
                data_type   VARCHAR(32)  NOT NULL DEFAULT 'STRING',
                required    BOOLEAN      NOT NULL DEFAULT FALSE,
                created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """;
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute(ddl);
            log.info("schema table ready.");
            initSessionTable(c);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create schema", e);
        }
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

    private static void seedDefaultsIfEmpty() {
        String countSql = "SELECT COUNT(*) FROM config_entries";
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
        insertDefaults();
    }

    public   static void addConfigs(Object[][] obj) {
        
         String sql = """
            INSERT INTO config_entries
                (config_key, config_value, description, category, data_type, required)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

         try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            for (Object[] row : obj) {
                if ( obj.length >= 6 ) {
                    ps.setString(1, (String) row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setString(4, (String) row[3]);
                    ps.setString(5, (String) row[4]);
                    ps.setBoolean(6, (Boolean) row[5]);
                    ps.addBatch();
                }
            }
            int[] result = ps.executeBatch();
            log.info("Inserted {} default configuration entries.", result.length);

        } catch (SQLException e) {
            log.error("Failed to insert default configuration", e);
        }
         
    }
    private static void insertDefaults() {
        String sql = """
            INSERT INTO config_entries
                (config_key, config_value, description, category, data_type, required)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        Object[][] defaults = {
            // Application
            {"app.name",        "Config App",     "Display name of the application",      "Application", "STRING",  true},
            {"app.version",     "1.0",               "Current application version",          "Application", "STRING",  false},
            {"app.debug",       "false",             "Enable debug mode (true/false)",       "Application", "BOOLEAN", false},
            {"app.base.url",    "http://localhost:18080/config", "Base URL of the application",  "Application", "URL",     true},

            // Database
            {"db.type",             "mysql",          "Database server hostname",                  "Database",    "STRING",  true},
            {"db.host",             "localhost",      "Database server hostname",                  "Database",    "STRING",  true},
            {"db.port",             "3306",           "Database server port",                      "Database",    "INTEGER", true},
            {"db.name",             "app",            "Database / schema name",                    "Database",    "STRING",  true},
            {"db.user",             "nb",             "Database username",                         "Database",    "STRING",  true},
            {"db.password",         "changeit",       "Database password",                         "Database",    "PASSWORD",true},
            {"db.pool.size",        "10",             "Maximum number of DB pool connections",     "Database",    "INTEGER", false},

            {"Proxy.type",             "transparent",    "Proxy server type",                      "Proxy",    "STRING",  true},
            {"Proxy.host",             "localhost",      "Proxy server hostname",                  "Proxy",    "STRING",  true},
            {"Proxy.port",             "1080",           "Proxy server port",                      "Proxy",    "INTEGER", true},
            {"Proxy.name",             "app",            "Proxy / schema name",                    "Proxy",    "STRING",  true},
            {"Proxy.user",             "admin",          "Proxy username",                         "Proxy",    "STRING",  true},
            {"Proxy.password",         "changeit",       "Proxy password",                         "Proxy",    "PASSWORD",true},
            {"Proxy.apps",         "HTTP,HTTPS,SFTP,SMTP,IMAP",    "Proxy application",                      "Proxy",    "STRING",  true},
            
            // Ldap
            {"ldap.host",      "ldap.example.com", "Ldap host",                          "Ldap",        "STRING",  false},
            {"ldap.port",      "389",              "Ldap port",                          "Ldap",        "INTEGER", false},
            {"ldap.user",      "cn=admin",         "Ldap username / from address",              "Ldap",        "STRING",  false},
            {"ldap.password",  "",                 "Ldap password",                             "Ldap",        "PASSWORD",false},
            {"ldap.tls",       "true",             "Use STARTTLS",                              "Ldap",        "BOOLEAN", false},
            {"ldap.server.config",    "",                 "Ldap server config",                        "Ldap",        "STRING",false},
            {"ldap.server.host",      "ldap.example.com", "Ldap server host",                          "Ldap",        "STRING",  false},
            {"ldap.server.port",      "389",              "Ldap server port",                          "Ldap",        "INTEGER", false},
            {"ldap.server.user",      "cn=admin",         "Ldap username / from address",              "Ldap",        "STRING",  false},
            {"ldap.server.password",  "",                 "Ldap password",                             "Ldap",        "PASSWORD",false},
            {"ldap.server.tls",       "true",             "Use STARTTLS",                              "Ldap",        "BOOLEAN", false},
            {"ldap.proxy.config",    "",                 "Ldap proxy config",                        "Ldap",        "STRING",false},
            {"ldap.proxy.host",      "ldap.example.com",  "Ldap server host",                          "Ldap",        "STRING",  false},
            {"ldap.proxy.port",      "389",               "Ldap server port",                          "Ldap",        "INTEGER", false},
            {"ldap.proxy.user",      "cn=proxyadmin",     "Ldap username / from address",              "Ldap",        "STRING",  false},
            {"ldap.proxy.password",  "",                  "Ldap password",                             "Ldap",        "PASSWORD",false},
            {"ldap.proxy.tls",       "true",              "Use STARTTLS",                              "Ldap",        "BOOLEAN", false},

            // Mail 
            {"mail.smtp.host",      "smtp.example.com","SMTP server host",                          "Mail",        "STRING",  false},
            {"mail.smtp.port",      "587",              "SMTP server port",                          "Mail",        "INTEGER", false},
            {"mail.smtp.user",      "noreply@example.com","SMTP username / from address",            "Mail",        "STRING",  false},
            {"mail.smtp.password",  "",                 "SMTP password",                             "Mail",        "PASSWORD",false},
            {"mail.smtp.tls",       "true",             "Use STARTTLS",                              "Mail",        "BOOLEAN", false},

            {"mail.imap.host",      "imap.example.com", "IMAP server host",                          "Mail",        "STRING",  false},
            {"mail.imap.port",      "993",              "IMAP server port",                          "Mail",        "INTEGER", false},
            {"mail.imap.user",      "noreply@example.com","IMAP username / from address",            "Mail",        "STRING",  false},
            {"mail.imap.password",  "",                 "IMAP password",                             "Mail",        "PASSWORD",false},
            {"mail.imap.tls",       "true",             "Use STARTTLS",                              "Mail",        "BOOLEAN", false},

            // Security
            {"security.session.timeout", "1800",        "HTTP session timeout in seconds",           "Security",    "INTEGER", false},
            {"security.jwt.secret",      "",            "JWT signing secret key",                    "Security",    "PASSWORD",true},
            {"security.allowed.origins", "http://localhost:8080","CORS allowed origins (comma-sep)","Security",    "STRING",  false},

            // Features
            {"feature.registration", "true",            "Allow new user registration",               "Features",    "BOOLEAN", false},
            {"feature.api.enabled",  "true",            "Enable REST API endpoints",                 "Features",    "BOOLEAN", false},
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
    
    public static boolean delProperty(String key) {
        init();
        boolean b = false;
        
        String sql = "delete from config_entries where config_key='"+key.replaceAll(" ", "")+"'";
         try ( Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            b=ps.execute();
            ps.close();
            c.commit();
            prop=null;
        } catch (SQLException e) {
            log.error("Failed to delete configuration of "+key, e);
        }   
        return b;
    }
    
    public static boolean addProperty(String key, String value, String desc,String cat, String typ,String require) {
        init();
        boolean b = false;
        
         String sql = "INSERT INTO config_entries "
                 + "(config_key, config_value, description, category, data_type, required)"
                 + " VALUES ('"+key+"', '"+value+"', '"+desc+"',"
                 + "'"+cat+"', '"+typ+"', "+getBoolean(require)+")            ";
         try ( Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            b=ps.execute();
            ps.close();
            c.commit();
            prop=null;
        } catch (SQLException e) {
            log.error("Failed to insert configuration "+key+"="+value, e);
        }   
        return b;
    }
    
    
    public static boolean setProperty(String key, String value) {
        init();
        boolean b = false;
        if ( key.toLowerCase().contains("password") ) {
            if ( ! crypt.isCrypted(value) ) {
                value=crypt.getCrypted(value);
            }
        }
        String sql ="UPDATE config_entries SET config_value=? WHERE config_key=?";
        try ( Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, key);
            b=ps.execute();
            ps.close();
            c.commit();
            prop=null;
        } catch (SQLException e) {
            log.error("Failed to update exist configuration", e);
        }
        return b;
    }
    public  static Properties getProperty(String part) {
        Properties p = new Properties();
        if ( isNullOrEmpty(p) ) { return p;} 
        String sql = """
            SELECT config_key,config_value from config_entries
            """;
        
            try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs =ps.executeQuery();
                
                while (  rs.next() ) {
                    String k = rs.getString("config_key");
                    log.info("db-key k:"+k+":");
                    String value =  rs.getString("config_value");
                    if ( k.contains(part) ) {
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

    private static Properties prop=null;
    public static Properties getConfig(String part) {
        init();
        if ( isNullOrEmpty(prop) || ! prop.getProperty("_part", "").equals(part) ) {
             prop=getProperty(part);
             prop.put("_part", part);
        }
        return prop;
    } 
    public static String getDbUrl() {
        Properties p = getConfig("db");
        
        return "jdbc:"+p.getProperty("db.driver", "mysql")+"://"+
                p.getProperty("db.host", "localhost")+":"+
                p.getProperty("db.port", "3306")+"/"+
                p.getProperty("db.name","basedb")
                ;
    }

    public static String getDbUsername() { 
        Properties p = getConfig("db");
        return p.getProperty("db.user", ""); 
    }
    public static void setDbUsername(String value) { 
        if (value != null ) { setProperty("db.user", value); }
    }
    
    public static String getDbName() { 
        Properties p = getConfig("db");
        return p.getProperty("db.name", ""); 
    }
    public static void setDbName(String value) { 
        if (value != null ) { setProperty("db.name", value); }
    }

    public static String getDbPassword() { 
        Properties p = getConfig("db");
        String a = p.getProperty("db.password", "");
        log.info("DB password:"+a+":");
        return a; 
    }
    public static void setDbPassword(String value) { 
        if ( value != null ) { setProperty("db.password", value); }
    }
    
    public static String getDbDriver() { 
        Properties p = getConfig("db");
        return p.getProperty("db.driver", "mysql"); 
    }
    public static void setDbDriver(String value) { 
        if ( isNotNullOrEmpty(value) ) { setProperty("db.driver", value); }
    }
    public static String getDbHost() { 
        Properties p = getConfig("db");
        return p.getProperty("db.host", "localhost"); 
    }
    public static void setDbHost(String value) { 
        if (value != null ) { setProperty("db.host", value); }
    }
    
    public static String getDbPort() { 
        Properties p = getConfig("db");
        return p.getProperty("db.port", "3306"); 
    }
    
    public static void setDbPort(String value) { 
        if (value != null && getInt(value) >0 ) { setProperty("db.port", value); }
    }
    
    public static String getDbJDBC() { 
        
        String a =  "jdbc:"+getDbDriver()+"://"+
                    getDbHost()+":"+getDbPort()+"/"+
                    getDbName();
        log.info("DB jdbc =>"+a+"<= ");
        return a ;
    }
    public static String getDbSchema() {   return getDbName();     }
    public static void setDbSchema(String value) { 
        if (value != null ) { setProperty("db.name", value); }
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
    
    String getValue(String k) {
        init();
        k=k.replaceAll(" ", "");
        StringBuilder sw=new StringBuilder();
        String sql = "SELECT config_key,config_value FROM config_entries;";
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
    boolean setValue(String k,String v) {
        init();
        k=k.replaceAll(" ", "");
        StringBuilder sw=new StringBuilder();
        String sql = "update config_entries set config_value='"+v+"' where config_key='"+k.replaceAll(" ", "")+"'";
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

    public static Properties getAllKeyProperty() { return getAllKeyProperty(""); }
    public static Properties getAllKeyProperty(String part) {
        init();
        part = ( isNullOrEmpty(part) )?"":part;
        Properties sw=new Properties();
        String sql = "SELECT config_key,config_value FROM config_entries";
        
        try (Connection c = getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ResultSet rs =ps.executeQuery();
                
                while (  rs.next() ) {
                    String k = rs.getString("config_key");
                    String v = rs.getString("config_value");
                    if ( k.startsWith(part) ) {
                        sw.put(k, v);
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
    
    public static String getAllKeys() { return getAllKeys(""); }
    public static String getAllKeys(String part) {
        Properties p = getAllKeyProperty(part);
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
    
    public static DBConfig getInstance(){ 
        return getInstance( 
                "jdbc:h2:file:" + dir.getFQDNDirName() + "/" + DB_NAME
                                + ";AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=TRUE;MODE=MYSQL;",
                "sa",
                GetPassword.getStrongPassword() 
        );
    }
    public static DBConfig getInstance(String h2Jdbc, String h2User, String h2Pass) {
          DBConfig conf=new DBConfig();
                   conf.jdbcUrl=h2Jdbc;
                   conf.DB_USER=h2User;
                   conf.DB_PASSWORD=h2Pass;
          
          return conf;
    }
}
