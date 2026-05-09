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
    
    private static String app="ROOT";
    
    public static void main(String[] args) {
        DBConfig conf = new DBConfig(app);
        
        for ( String s : args ) {
            if ( s.equals("-d") ) { DBConfig.appDebug++; }
        }
        System.out.println("");
        System.out.println("Type something (type 'quit' to exit):");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
            String line;

            // Read lines until "quit" is entered
            while (true) {
                System.out.print(" > ");
                line = reader.readLine().trim();
                System.out.println("");
                
                if ("quit".equalsIgnoreCase(line.trim()) || "q".equals(line.trim()) ) {
                    System.out.println("Quit command received. Goodbye!");
                    break;
                } else if ( "version".equalsIgnoreCase(line.trim())) {   
                    System.out.println(conf.getAppInfo());
                    break;
                } else {
                   try {
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
                            case "apps": { showAllApps(conf); break; }
                            case "app" : {       setApp(conf,line.substring("app ".length()));  break; }
                            case "register"   : {  regApp(conf,line.substring("register ".length()));  break; }
                            case "unregister" : {unregApp(conf,line.substring("register ".length()));  break; }
                            case "help":       {
                                                if ( line.length() == sp[0].length()) {
                                                    printHelp(null);
                                                } else {
                                                    printHelp(line.substring("help ".length()).trim());
                                                }
                                                break;
                                               }
                            default:{
                                System.out.println("command: "+sp[0]+" not found; type 'help' for available commands.");
                                break;
                            }
                        }
                    } catch(StringIndexOutOfBoundsException ne) {

                    }
                }    
                    
            }    
        
        } catch (java.io.IOException e) {
            System.err.println("Error reading from input: " + e.getMessage());
        }
    }
    
    private static void setApp(DBConfig conf, String line){
        if ( conf.isAppExist(line.toUpperCase()) ) {
            app=line.toUpperCase();
            conf.setApp(app);
        } else {
            System.out.println("Application "+line+" not registered");
        }
    }
    
    private static void regApp(DBConfig conf, String line){
        if ( ! conf.isAppExist(line.toUpperCase()) ) {
            line=line.toUpperCase().replaceAll(" ", "").replaceAll("\t", "").replaceAll("\n", "");
            conf.init(line);
            conf.addApp(line);
            conf.setApp(line);
        } else {
            System.out.println("Application "+line+" already registered");
        }
        showAllApps(conf);
    }
    private static void unregApp(DBConfig conf, String line){
        if ( conf.isAppExist(line.toUpperCase()) ) {
            line=line.toUpperCase().replaceAll(" ", "").replaceAll("\t", "").replaceAll("\n", "");
            conf.unApp(line);
        } else {
            System.out.println("Application "+line+" not registered");
        }
        showAllApps(conf);
    }
    
    private static void showAllApps(DBConfig conf) {
        System.out.println("Applications : "+conf.getAppList());
    }

    private static void setConfig(DBConfig conf, String line) {
        String[] sp = line.split("=");
        String  k= sp[0];
        if ( line.length()> (k.length()+1) ) {
            String  v = line.substring(k.length()+1);
            System.out.println( ( (  conf.setValue(app,k,v) )?"OK":"Failed" ) );
        } else {
            System.out.println("SKIP: missing value for key "+k);
        }
    }

    private static void getConfig(DBConfig conf, String line) {
        System.out.println( conf.getValue(app,line) );
    }
    
    private static void addConfigKey(DBConfig conf, String line) {
         // addProperty(String app, String key, String value, String desc,String cat, String typ,String require)
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
            conf.addProperty(app, k,v,desc,cat,typ,require);
         } else { 
             System.out.println("ERROR: No key given.");
         }   
    }
    
    private static void delConfigKey(DBConfig conf, String line) {
        System.out.println( ( ( conf.delProperty(app,line))?"OK":"Failed" ) );
    }
        

    private static void getListKeys(DBConfig conf,String line) {
        System.out.println("List all keys from config for Application "+app);
        System.out.println(conf.getAllKeys(app,line));
        System.out.println("");
    }

    private static final String[][] HELP = {
        {"set",        "set <key>=<value>",                                    "Set the value of an existing configuration key."},
        {"get",        "get <key>",                                            "Print the value of a configuration key."},
        {"list",       "list [prefix]",                                        "List all keys (optionally filtered by prefix, e.g. list db)."},
        {"add",        "add <key>=<value>;<desc>;<category>;<type>;<required>","Add a new configuration key. Type: STRING|INTEGER|BOOLEAN|URL|PASSWORD. Required: true|false."},
        {"del",        "del <key>",                                            "Delete a configuration key."},
        {"app",        "app <name>",                                           "Switch the active application context (must be registered)."},
        {"apps",       "apps",                                                 "Show all registered applications."},
        {"register",   "register <name>",                                      "Register a new application and seed it with default configuration entries."},
        {"unregister", "unregister <name>",                                    "Unregister an application and delete all its configuration entries."},
        {"version",    "version",                                              "Print version information and exit."},
        {"help",       "help [command]",                                       "Show help for all commands or a specific command."},
        {"quit",       "quit | q",                                             "Exit the program."},
    };

    private static void printHelp(String command) {
        if ( command == null || command.isEmpty() ) {
            System.out.println("Available commands:");
            for ( String[] entry : HELP ) {
                System.out.printf("  %-30s %s%n", entry[1], entry[2]);
            }
        } else {
            boolean found = false;
            for ( String[] entry : HELP ) {
                if ( entry[0].equalsIgnoreCase(command) ) {
                    System.out.println("Usage:       " + entry[1]);
                    System.out.println("Description: " + entry[2]);
                    found = true;
                    break;
                }
            }
            if ( !found ) {
                System.out.println("No help available for '" + command + "'. Type 'help' for all commands.");
            }
        }
        System.out.println("");
    }

}
