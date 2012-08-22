package edu.virginia.lib.fedora.eadingest.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A ContainerReferenceResolver implementation that works for the values
 * apparently entered in the finding aid with call number MSS5950.  In 
 * this case, the mapping is programmatic.
 */
public class MSS5950ContainerReferenceResolver implements ContainerReferenceResolver {

    
    public List<String> getCannonicalContainerIdentifier(String enteredGarbage, Collection<String> validValues) {
        Matcher rangeMatcher = Pattern.compile("Box (\\d+)\\-(\\d+)").matcher(enteredGarbage);
        if (rangeMatcher.matches()) {
            List<String> results = new ArrayList<String>();
            for (int i = Integer.parseInt(rangeMatcher.group(1)) ; i <= Integer.parseInt(rangeMatcher.group(2)); i ++) {
                String result = "MSS 5950, Box " + i; 
                if (!validValues.contains(result)) {
                    throw new IllegalArgumentException("\"" + enteredGarbage + "\" (-->\"" + result + "\") does not match a valid value!");
                }
                results.add(result);
            }
            return results;
        }
        
        Matcher m = Pattern.compile("Box (\\d+)").matcher(enteredGarbage);
        if (m.matches()) {
            String result = "MSS 5950, Box " + m.group(1);
            if (!validValues.contains(result)) {
                throw new IllegalArgumentException("\"" + enteredGarbage + "\" (-->\"" + result + "\") does not match a valid value!");
            }
            return Collections.singletonList(result);
        }
        
        throw new IllegalArgumentException("\"" + enteredGarbage + "\" could not be matched to any container!");
    }

    
    
}
