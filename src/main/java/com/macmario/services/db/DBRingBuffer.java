/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.macmario.services.db;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;



// suppress unchecked warnings in Java 1.5.0_6 and later

/**
 *
 * @author SuMario
 */
public class DBRingBuffer<Item> extends AppVersion implements Iterable<java.sql.Connection> {

    // private RingBuffer<String> ring  ;
    private java.sql.Connection[] elements;     // queue elements
    private int number = 0;      // number of elements on queue
    private int first  = 0;      // first element in the queue
    private int last   = 0;      // last element in the queue
    private String lock = null ;
    private DBRingBuffer<java.sql.Connection> ring;
    private boolean autoInc=true;
    private int capa;
    
    

    // contructor for the RingBuffer - array where not generated on default
    public DBRingBuffer(int capacity) { this.capa=capacity; clear(); } 

    public DBRingBuffer() { this(20); }
    
    public  synchronized boolean clear() { 
        getLock("clear");
            this.elements = new java.sql.Connection[capa]; 
            this.number = 0;      // number of elements on queue
            this.first  = 0;      // first element in the queue
            this.last   = 0;
        clearLock("clear");
        return true;
    }

    public  synchronized boolean isEmpty()  { return number == 0;  }
    public  synchronized boolean isFull()   { return number == this.elements.length; }
    public  synchronized int     getSize()  { return number;       }
    public  synchronized boolean reSize(int newSize) {
        if ( ! this.isEmpty() ) { return false; }
        getLock("resize");
            this.capa=newSize;
        clearLock("resize");
            clear();  
        
        return true;
    }
    public void setAutoIncrease(boolean b) { this.autoInc=b; }
    
    public synchronized boolean isLocked() { return (lock == null)?false:true; }

    private final String mylock="RingBuffer sync";
    
    public synchronized void getLock(String s) {
         final String func="getLock(String s)";
         synchronized (mylock) {   
            while( this.isLocked() ) {
                printf(func,5,"locked with:"+lock);
                try {
                   this.wait(500);
                } catch ( InterruptedException l ) {}
            }
            this.lock=s;
         }   
    }

    public synchronized void clearLock(String s) {
        final String func="clearLock(String s)";
        synchronized (mylock) { 
            if ( this.lock.equals(s)) { lock=null; this.notify(); }
        }
    }

    public synchronized boolean push( java.sql.Connection s ) { 
        if ( ! autoInc && isFull() ) { 
            log(1, "queue full - get one of "+number+" connection ");
            pop(); 
        }
        log(1, "push one of "+number+" connection");
        return this.enqueue( s ); 
    }
    
    private synchronized boolean enqueue(java.sql.Connection item) {
        final String func="enqueue(Item item)";
        printf(func,6,"verify RingBuffer length number:"+number+" == element.length:"+this.elements.length+" ?");
        if ( isFull() ) { return false; }

        printf(func,5,"ask for queue lock");
        getLock("queue");
        printf(func,5,"have the queue lock now \nset item to last:"+last);
        
        this.elements[last] = item;
        this.last = (this.last + 1) % this.elements.length ;  // set next element wrap-around
        this.number++;
        
        printf(func,5, "number:"+number+": last:"+last+" updated - size:"+this.getSize()+"\nclear the lock from queue");
        clearLock("queue");
        
        return true;
    }

    public java.sql.Connection pop() { 
        log(1, "get one of "+number+" connection");
        return this.dequeue(); 
    }
    
    
    private synchronized java.sql.Connection dequeue() {
        final String func="dequeue()";
        printf(func,5,"if empty return ("+this.isEmpty()+")- no extra debug msg");
        if ( this.isEmpty() ) { return null; }

        printf(func,5,"ask for release lock");
        getLock("release");
        printf(func,5,"have the release lock now\nget Item from "+this.first);
        
        java.sql.Connection item = this.elements[ this.first ];
        this.elements[ this.first ] = null;      // cleanup for gc
        this.number--;
        this.first = ( this.first + 1 ) % this.elements.length; // set next element + wrap around
        
        printf(func,5,"number:"+number+": last:"+last+" updated - size:"+this.getSize()+"\nclear the lock from release");
        clearLock("release");
        
        printf(func,5,"return item :"+item.toString()+":");
        return(item);
    }
    
    synchronized public  java.sql.Connection getFirst()       { return  getFirstObject(); }
    synchronized public  java.sql.Connection getFirstObject() { return      getObject(0); }

    synchronized public  java.sql.Connection getLast()        { return    getLastObject(); }
    synchronized public  java.sql.Connection getLastObject()  { return  getObject(last-1); }
    synchronized private java.sql.Connection getObject(int pos){
        getLock("pos");
        java.sql.Connection o = (pos >=0 && pos < this.elements.length )?this.elements[pos]:null;                 
        clearLock("pos");    
        return o;
    }
    
    public synchronized void serviceConnection(Db db){
        getLock("service");
        
        ArrayList<java.sql.Connection> ar = new ArrayList<>();
        while( ! isEmpty() ) {
            java.sql.Connection sa = pop();
            if ( ! db.isConnectionClosed(sa) ) {
                 ar.add(sa);
            }
        }
        for( java.sql.Connection sa : ar){ push(sa); }
        
        clearLock("service");
    }
   
    @Override
    public Iterator<java.sql.Connection> iterator() { return new RingBufferIterator(); }
    
    @Override
    public void log(int level, String msg) {
        super.log(level, "DBRingBuffer::"+msg);
    }

    private class RingBufferIterator extends AppVersion implements Iterator<java.sql.Connection> {
        private int i = 0;
        @Override
        public boolean hasNext() { return i < number;  }
        public boolean isEmpty() { return i == 0; }
        
        RingBufferIterator() { super(); }
        
        @Override
        public void remove()  {
            if ( ! this.isEmpty() ) { throw new NoSuchElementException(); }
            elements[i]=null; i--;
             
        }

        @Override
        public java.sql.Connection next() {
            if ( ! this.hasNext() ) { throw new NoSuchElementException(); }
            return elements[i++];
        }

    }
    
}
