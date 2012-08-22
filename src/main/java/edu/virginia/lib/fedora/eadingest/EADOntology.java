package edu.virginia.lib.fedora.eadingest;

import java.util.Properties;

/**
 * A class that contains the configurable values for the various 
 * relationships and object names for the representation of the
 * various portions of EAD in Fedora.
 */
public class EADOntology {

    private Properties p;
    
    public EADOntology(Properties configuration) {
        p = configuration;
    }
    
    public String hasModel() {
        return p.getProperty("hasModel-relationship");
    }
    
    public String isPartOf() {
        return p.getProperty("isPartOf-relationship");
    }
    
    public String follows() {
        return p.getProperty("follows-relationship");
    }
    
    public String hasMarc() {
        return p.getProperty("hasMarc-relationship");
    }
    
    public String definesContainer() {
        return p.getProperty("definesContainer-relationship");
    }
    
    public String isContainedWithin() {
        return p.getProperty("isContainedWithin-relationship");
    }
    
    public String collectionCModel() {
        return p.getProperty("collection-cmodel");
    }
    
    public String componentCModel() {
        return p.getProperty("component-cmodel");
    }
    
    public String eadRootCModel() {
        return p.getProperty("eadRoot-cmodel");
    }
    
    public String eadComponentCModel() {
        return p.getProperty("eadComponent-cmodel");
    }
    
    public String marcCmodel() {
        return p.getProperty("marc-cmodel");
    }
    
    public String modsCmodel() {
        return p.getProperty("mods-cmodel");
    }
    
    public String containerCmodel() {
        return p.getProperty("container-cmodel");
    }
    
    public String eadRootDSID() {
        return p.getProperty("eadRoot-dsId");
    }
    
    public String eadComponentDSID() {
        return p.getProperty("eadComponent-dsId");
    }
    
    public String marcDSID() {
        return p.getProperty("marc-dsId");
    }
    
    public String modsDSID() {
        return p.getProperty("mods-dsId");
    }
    
    public String containerDSID() {
        return p.getProperty("container-dsId");
    }
    
}
