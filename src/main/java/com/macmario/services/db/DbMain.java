/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.macmario.services.db;

/**
 *
 * @author SuMario
 */
public class DbMain extends AppVersion {
    
    public static void main(String[] args) {
        DBConfig conf = new DBConfig();
        System.out.println("");
        System.out.println("Type something (type 'quit' to exit):");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
            String line;

            // Read lines until "quit" is entered
            while (true) {
                System.out.print(" > ");
                line = reader.readLine().trim();
                System.out.println("");
                
                if ("quit".equalsIgnoreCase(line.trim())) {
                    System.out.println("Quit command received. Goodbye!");
                    break;
                } else {
                    String[] sp = line.split(" ");
                    switch(sp[0].toLowerCase()) {
                        case "set" : { setConfig(conf,   line.substring("set ".length()));  break; }
                        case "get" : { getConfig(conf,   line.substring("get ".length()));  break; }
                        case "list": { 
                                        if ( line.length()== sp[0].length()) {
                                            getListKeys(conf, "");
                                        } else {    
                                            getListKeys(conf, line.substring("list ".length()));
                                        }
                                        break; }
                        case "add" : { addConfigKey(conf,line.substring("add ".length()));  break; }
                        case "del" : { delConfigKey(conf,line.substring("add ".length()));  break; }
                        default:{ break; }
                    }
                }
                    
            }    
        
        } catch (java.io.IOException e) {
            System.err.println("Error reading from input: " + e.getMessage());
        }
    }

    private static void setConfig(DBConfig conf, String line) {
        String[] sp = line.split("=");
        String  k= sp[0];
        if ( line.length()> (k.length()+1) ) {
            String  v = line.substring(k.length()+1);
            System.out.println( ( (  conf.setValue(k,v) )?"OK":"Failed" ) );
        } else {
            System.out.println("SKIP: missing value for key "+k);
        }
    }

    private static void getConfig(DBConfig conf, String line) {
        System.out.println( conf.getValue(line) );
    }
    
    private static void addConfigKey(DBConfig conf, String line) {
         // addProperty(String key, String value, String desc,String cat, String typ,String require)
         // {"app.name",        "Vermieter App",     "Display name of the application",      "Application", "STRING",  true},
         String k="";
         String v="";
         String desc="";
         String cat = "Other";
         String typ = "STRING";
         String require="true";
         int c=0;
         for( String s: line.split(";") ) {
            if ( c==0 ) {
                String[] sp = s.split("=");
                k= sp[0];
                if ( s.length() > k.length()+1) { v=s.substring(k.length()+1); }
            } 
            else if ( c==1 ) { desc=s; }
            else if ( c==2 ) {  cat=s; }
            else if ( c==3 ) {  typ=s; }
            else if ( c==4 ) { require=(getBoolean(s))?"true":"false"; }
            c++;
         }
         if ( isNotNullOrEmpty(k)) { 
            conf.addProperty(k,v,desc,cat,typ,require);
         } else { 
             System.out.println("ERROR: No key given.");
         }   
    }
    
    private static void delConfigKey(DBConfig conf, String line) {
        System.out.println( ( ( conf.delProperty(line))?"OK":"Failed" ) );
    }
        

    private static void getListKeys(DBConfig conf,String line) {
        System.out.println("List all keys from config");
        System.out.println(conf.getAllKeys(line));
        System.out.println("");
    }

}
