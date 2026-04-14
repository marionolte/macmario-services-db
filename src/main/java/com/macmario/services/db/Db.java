/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.macmario.services.db;



import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

/**
 *
 * @author SuMario
 */
public class Db extends AppVersion{
    
    private DbUser dbu;
    private boolean _loaded; 
    private final Properties prop; 
    private boolean closeAfterStatement=false;
    
    
    public Db(String url, String user, String pw){
        log(2,"url:"+((url==null)?"NULL":url)
                       +": user:"+((user==null)?"NULL":user)
                       +": pw->|"+((pw==null)?"NULL":pw)+"|<-");
        dbu = new DbUser(url, user, pw);
        prop = new Properties();
        
        this.ring = new DBRingBuffer(30);
    }
    
    public String getNextId(String tab){
        String ret="-1";
        if ( tab == null || tab.isEmpty() ) { return ret; }
        String sql="select MAX(id) from "+tab+" ;";
        HashMap<String, ArrayList<String>> p = this.getResult(sql, "");
        if ( ! p.isEmpty() && p.size() >1 ) {
            ArrayList<String> ar = p.get(""+1);
            String max=ar.get(0);
            log(2,"max:"+max+" in tabelle "+tab);
            if ( max != null && ! max.isEmpty() ){
                int m = Integer.parseInt(max);
                ret=""+(++m);
            }
        }
        return ret;
    }
    
    
    public Connection getConnection(){ return getConnection(dbu); }
    public Connection getConnection(DbUser user)  {
          Connection conn=getOpenConnection();
          if ( conn != null ) {
              try {
                conn.isClosed() ;
                closeAfterStatement=false;
                return conn;
              } catch(SQLException se){} 
          }
          try {
            //  System.out.println(user.getConnection()+"<->"+user.getUsername()+"<->"+user.getPassword()+"<-|");  
            conn =  DriverManager.getConnection(user.getConnection(), user.getUsername(), user.getPassword());
            if ( ! ring.isFull() )  {
                closeAfterStatement=false;
            } else {
                closeAfterStatement=true;
            }
            
          } catch ( SQLException se ) {
              System.out.println("ERROR: create connection "+user.getConnection()+" - "+se.getLocalizedMessage() );
          }  
          //System.out.println("db connection:"+user.getConnection()+":"+conn);
          return conn;
    }
    private Connection getOpenConnection() {
        if ( ! ring.isEmpty() ) {
            log(1, "getOpenConnection have "+ring.getSize()+" Connections");
            Connection conn = null;
            while( ! ring.isEmpty()  ) {
                conn = (Connection) ring.pop();
                boolean service = false;
                try {
                    if ( ! conn.isClosed()  )  {
                        return conn;
                    } else {
                        service=true;
                    }    
                } catch(SQLException se){ conn=null; service=true; }
                if (service) { ring.serviceConnection(this); }
            }
        }
        return null;
    }
    
    boolean isConnectionClosed(Connection co) {
        try {
            return co.isClosed();
        } catch (SQLException se){
            try {  co.close(); }catch(SQLException sep) {}
        }
        return true;
    }
    
    private DBRingBuffer ring;
    
    public HashMap<String, ArrayList<String>> getResult(String sql, String filterValue                                  ) { return getResult(getConnection(),sql, "@@FILTER@@", filterValue); }
    public HashMap<String, ArrayList<String>> getResult(String sql, String filter, String filterValue                   ) { return getResult(getConnection(),sql, filter, filterValue); }
    public HashMap<String, ArrayList<String>> getResult(Connection conn, String sql, String filter, String filterValue  ) { return getResult(getConnection(),sql, new String[]{ filter+"="+filterValue });}
    public HashMap<String, ArrayList<String>> getResult(String sql, String[] fmap                                       ) { return getResult(getConnection(),sql, fmap );  }
    public HashMap<String, ArrayList<String>> getResult(Connection conn, String sql, String[] fmap) {    
        if ( conn == null ) { conn=getConnection(); }
        for ( String s : fmap ) {
            log(2, "like update :"+sql+": with :"+s+":");
            String filter      = s.split("=")[0];
            String filterValue = s.substring(filter.length()+1);
            log(3, "filter:"+filter+":  filterValue:"+filterValue+": ");
            sql = updateSQL(sql, filter, filterValue);
            log(2, "like to execute sql:"+sql+": ");
        }    
        log(1, "sql:"+sql+":");
        ArrayList<String> head = new ArrayList();
        ArrayList<String> head1 = new ArrayList();
        HashMap<String, ArrayList<String>> map = new HashMap<>();
            map.put("head", head1);
        try {
            PreparedStatement ps = conn.prepareStatement(sql+"\n");
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData ms = rs.getMetaData(); 
            int count =  ms.getColumnCount();
            log(2, "sql colunm count:"+count);
            while ( rs.next() ) {
                if ( rs.isFirst() ) {
                    log(3, "first - getHeader");
                    for ( int i=1; i<=count; i++ ) {
                        head.add(ms.getColumnName(i));
                        head1.add(getUniq(head1,head.get(head.size()-1)));
                        log(3, "sql add head:"+head.get(head.size()-1)+":  =>"+head1.get(head1.size()-1)+"<=");
                    }
                } else {
                    log(4, "not first");
                }
                ArrayList<String> val = new ArrayList();
                for ( int i=1; i<=count && i > 0; i++) { 
                        //log(1, "ask sval["+i+"] ");
                        //val.add(rs.getString(head.get(i-1)));  //only uniq Columns
                        val.add(rs.getString( (i) ));
                        log(2, "sval["+i+"] "+head.get(val.size()-1)+"=>"+val.get(val.size()-1)+":");
                }
                map.put(""+rs.getRow(), val);
            }
            log(2, "have "+(map.size()-1)+" entries");
            
            if ( closeAfterStatement ) {
                rs.close();
                conn.close();
            } else {
                ring.push(conn);
            }
        } catch(SQLException se ){
                log(1, "ERROR: SQl Exception - "+se.getLocalizedMessage() );
        }    
        return map;
    }
    
    private String getUniq(ArrayList<String> head, String val){
        String ret=val; int c=0;
        for( String s : head ) {
            if (s.equals(ret)) { c++; ret=val+c; }
        }
        log(3, "Header ->"+head+"<-  val:"+val+": return:"+ret+":");
        return ret;
    }
    
    public boolean updateDB(String sql, String[] sp){
        loadMainSql();
        log(3, "update sql:"+sql+":");
        String r=prop.getProperty(sql);
        String ret = (  (r==null)?sql:r ).replaceAll("^\"", "").replaceAll("\"$", "");
        if( ret == null ) { ret=sql; } 
        for( String s:sp){
            if( ! s.isEmpty() ) {
                String k = s.split("=")[0]; String v="";
                if ( ret.contains(k) ) {
                    v = ( s.length()>(k.length()+1))?s.substring(k.length()+1):v;
                    log(1,"update =>"+k+"<=  with =>"+v+"<=   from :"+s+":");
                    ret = ret.replaceAll(k, v);
                }
            }
        }
        log(1,"like to execute =>"+ret+"<=");
        return executeSQL(ret);
    }
    public boolean updateDB(String sql, String pattern, String fvalue){
        return updateDB(sql, new String[]{ pattern+"="+fvalue } );
    }
    
    private boolean executeSQL(String sql){
        boolean b=false;
        try {
            log(3, "execute sql:"+sql+":");
            Connection conn = getConnection();
            Statement st = conn.createStatement();
            st.execute(sql);
            b=true;
            log(2, "execute sql:"+sql+":  OK");
        } catch (SQLException | NullPointerException se )  {
           log(1, "execute sql:"+sql+":  Error - "+se.getMessage()); 
        }  
        return b;
    }
    
    public String updateSQL(String sql, String pattern, String fvalue) {
        loadMainSql();
        log(3, "sql:"+sql+":");
        String r=prop.getProperty(sql);
        String ret = (  (r==null)?sql:r ).replaceAll("^\"", "").replaceAll("\"$", "");
        if( ret == null ) { ret=sql; }
        
        if ( sql.contains(pattern)) {
             sql = sql.replaceAll(pattern, fvalue);
        }
        
        ret= ret.replaceAll(pattern, fvalue);
        
        return ret;
    }
    
    
    private void loadMainSql() {
       if ( _loaded ) { return; }
       try { 
        prop.load( this.getClass().getResourceAsStream("/com/macmario/services/ssmcheck/db/sql.properties") );
        _loaded=true;
       }catch(java.io.IOException io){
           log(1,"ERROR: "+io.getLocalizedMessage() );
       }
        
    }
    
    @Override
    public void log(int level, String msg) {
        super.log(level, "SSM-Db::"+msg);
    }
    
    public static void main(String[] args) {
        
        Db db = new Db(args[0], args[1], args[2] );
        
        String f="";
        if ( args.length > 4 ) { f=args[4]; }
        String v="";
        if ( args.length > 5 ) { v=args[5]; }
        
        System.out.println("result:"+db.getResult(db.getConnection(), args[3], f, v));
    }
}
