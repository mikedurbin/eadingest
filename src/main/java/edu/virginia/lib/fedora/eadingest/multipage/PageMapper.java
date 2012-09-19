package edu.virginia.lib.fedora.eadingest.multipage;

import java.util.List;

public interface PageMapper {
    
    public List<String> getDigitizedItemPagePids(String id);
    
    public String getExemplar(String id);
}
