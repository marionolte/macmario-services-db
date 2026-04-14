/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.macmario.services.db;

/**
 *
 * @author SuMario
 */
class DbUser extends AppVersion{
    private String _dbtype;
    private String _dbhost;
    private String _dbschema;
    private String _dbpara;
    private DbAccount  _user;
    
    DbUser(String url, String user, String pw) {
        // "jdbc:mysql://localhost:3306/test?serverTimezone=UTC";
        if (url== null || ! url.startsWith("jdbc:") ) { 
            throw new DbConfigException("no jdbc url provided"); 
        }
        
        _user = new DbAccount(user, pw);
        
        String[] sp = url.split("/");
        String[] mp = sp[0].split(":");
        //System.out.println("sp[0]=>"+sp[0]+"<-  "+mp.length);
        _dbtype=mp[ 1 ];
        _dbhost=sp[ 2 ];
        
        if ( sp.length>3 ) {
            mp=sp[3].split("\\?");
            _dbschema=mp[0];        
            _dbpara=( mp.length<=1)?"serverTimezone=UTC":sp[3].substring(_dbschema.length()+1);
        }
        //System.out.println("Con:"+getConnection());
    }
    
    String getConnection() {
        StringBuilder sw=new StringBuilder();
        
        sw.append("jdbc:"+getDbTyp()+"://"+getDbHost()+"/"+getDbSchema()+getDbParams() );
        
        return sw.toString();
    }
    
    String getDbTyp()    { return _dbtype ;   } 
    String getDbHost()   { return _dbhost ;   }
    String getDbSchema() { return _dbschema ; }
    String getDbParams() { return ( _dbpara.isEmpty() )?"":"?"+_dbpara ;   }
    
    String getUsername() { return _user.getUsername(); }
    String getPassword() { return _user.getPassword(); }
    
    public static void main(String[] args) {
        new DbUser("jdbc:mysql://localhost:3306/test?serverTimezone=UTC","user","pw");
    }
}
