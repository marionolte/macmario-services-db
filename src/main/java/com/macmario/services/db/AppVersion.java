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
    
    final public static String mhfile="macmario-core-db.jar";
    final public static String mh="MarioHelpServices";
    final public static String mhservice="MHServices - "+mhfile;
    final public static String prodauthor="Mario Nolte";
    final public static int majorVersion=0;
    final public static int minorVersion=0;
    final public static int patchVersion=1;
    final public static int fixedVersion=0;
    final public static int   libVersion=0;
    final public static int  betaVersion=1;
    
    
    public boolean isDebug(){ return isDebug(0); }
    public boolean isDebug(int l){ return ( l>=0 && debug>l ); }
    
}
