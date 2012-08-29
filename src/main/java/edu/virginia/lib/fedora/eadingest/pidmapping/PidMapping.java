package edu.virginia.lib.fedora.eadingest.pidmapping;

import org.w3c.dom.Element;

/**
 * An interface whose implementations are responsible for
 * determining the PID of an already-ingested object that
 * represents the metadata in the DOM Element provided.
 * 
 * Simple implementations may require that the Element have
 * a particular attribute or characteristic, others may use
 * some sort of hard-coded mapping. 
 */
public interface PidMapping {
    
    /**
     * Gets the Fedora PID representing the object
     * that corresponds to the metadata represented 
     * in the DM Element.
     * 
     * Some implementations may throw an exception
     * if no PID exists, others may simply return
     * null.  It is up to the implementation to define
     * the semantics.
     * 
     * @param el a DOM element for the metadata 
     * @return the Fedora PID for the corresponding
     * object
     */
    public String getPidForElement(Element el);
    
}
