package edu.virginia.lib.fedora.eadingest.container;

import java.util.Collection;
import java.util.List;

/**
 * An interface that when implemented can be used to lookup the container(s)
 * referenced by arbitrary entered text.  A common implementation will convert
 * some shorthand like "boxes 1-3" to the values "box 1", "box 2" and "box 3".
 * Others may have to do complex lookups for alternate id resolution.
 */
public interface ContainerReferenceResolver {

    public List<String> getCannonicalContainerIdentifier(String enteredGarbage, Collection<String> validValues);
    
}
