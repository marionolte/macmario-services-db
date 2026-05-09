/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.macmario.services.db;


/**
 *
 * @author SuMario
 */
public class AppVersion extends com.macmario.general.Version {
    
    final public static String mhfile="macmario-services-db.jar";
    final public static String mh="MarioHelpServices";
    final public static String mhservice="MHServices - "+mhfile;
    final public static String prodauthor="Mario Nolte";
    final public static int majorAppVersion=0;
    final public static int minorAppVersion=0;
    final public static int patchAppVersion=2;
    final public static int fixedAppVersion=0;
    final public static int   libAppVersion=0;
    final public static int  betaAppVersion=0;
          public static int        appDebug=0;
    
    
    @Override
    public boolean isDebug(){ return isDebug(appDebug); }
    @Override
    public boolean isDebug(int l){ return ( l>=0 && (appDebug>=l || debug>=l ) ); }
    
    public String getAppInfo() {
        return (mhservice+"/"+(isAppBeta()?getAppBetaVersion():getAppVersion())+" - "+prodauthor);
    }
    
    final public boolean isAppBeta() { return betaAppVersion==1; }
    final public String getAppVersion() { return majorAppVersion+"."+minorAppVersion+"."+patchAppVersion; }
    final public String getAppBetaVersion() { return getAppVersion()+"."+fixedAppVersion; }
    
    
}
