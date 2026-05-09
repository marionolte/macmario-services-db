/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.macmario.services.db;

import com.macmario.io.crypt.Crypt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 *
 * @author SuMario
 */
public class DbCache extends AppVersion{
    private HashMap<String,HashMap<String,ArrayList<String>>> map ;
    private Crypt crypt;
    private boolean dbDown=false;
    private DBConfig conf;
    private Db db;
   
   
   public DbCache(String app) {
       this.map = new HashMap();
       this.crypt = new Crypt();
       this.conf = new DBConfig(app);
       this.db    = new Db(conf.getMainJDBC(), conf.getMainUser(), conf.getMainPass());
   }
    
   HashMap<String, HashMap<String, HashMap<String, ArrayList<String>>>> dbmap=new HashMap();
   HashMap<String, ArrayList<String>> getDB(String what, String filter) {
       HashMap<String, ArrayList<String>> plist;
       boolean need = false;
       if ( dbmap.get(what) == null ) { dbmap.put(what, new HashMap<>()); need=true; }
       HashMap<String, HashMap<String, ArrayList<String>>> dblist = dbmap.get(what);
       String md5=crypt.getMD5(filter);
       plist= dblist.get(md5);
       if ( plist == null ) { need=true; }
       if (need) {   
           try {
                plist=db.getResult(what, filter);
                dblist.put(md5, plist);
                dbmap.put(what, dblist);
                dbDown=false;
           } catch(NullPointerException npe) {
               dbDown=true;
           }     
       } 
       log(1, "getDB what:"+what+": filter:"+filter+": result"+plist);
       return plist;     
   }
   
   boolean updateDB(String what, String[] filter) {
       boolean b= db.updateDB(what,  filter);
       if (b) dbmap=new HashMap(); // clean cache
       return b;
   }
   
    public String getNextId(String tab ){ return db.getNextId(tab); }
      
    
    
    @Override
    public void log(int level, String msg) {
       super.log(level, "DbMap::"+msg);
    }
    
    synchronized String getValueOf(String key, HashMap<String, ArrayList<String>> plist) {
        return getValueOf(key, plist.get("head"), plist.get("1"));
    }
    synchronized String getValueOf(String key, String val , HashMap<String, ArrayList<String>> plist) {
        return getValueOf(key, plist.get("head"), plist.get(val));
    }
    synchronized String getValueOf(String val, ArrayList<String> head, ArrayList<String> vl) {
        String ret="";
        if ( val != null && ! val.isEmpty() ) {
            int c = 0; 
            Iterator<String> itter = head.iterator();
            while( itter.hasNext() ) {
                String k = itter.next(); 
                if ( k.equals(val)){
                    log(1,"found c:"+c+"/"+vl.size()+" k:"+k+"=="+val+":");
                    if ( vl.size() > c ) { 
                        ret=vl.get(c); 
                        log(1,"return for :"+val+":  return =>"+ret+"<=");
                        return ret;
                    }
                } else {
                    log(3,"not equals c:"+c+" k:"+k+":"+val+":");
                }
                c++;
            }
        }    
        return ret;
    }

}
