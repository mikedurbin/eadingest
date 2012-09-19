package edu.virginia.lib.fedora.eadingest.multipage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

/**
 * Parses a text file or stream that is the result of Ruby's
 * standard serialization of a Hash and exposes the content
 * as a PageMapper.
 */
public class RubyHashPageMapper implements PageMapper {

    public static void main(String [] args) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(RubyHashPageMapper.class.getClassLoader().getResourceAsStream("ead/viu00003-digitized-item-mapping.txt"), baos);
        new RubyHashPageMapper(baos.toString());
    }
    
    private Map<String, List<String>> map;
    
    public RubyHashPageMapper(String serialization) {
        map = new HashMap<String, List<String>>();
        
        Pattern groupPattern = Pattern.compile("\\Q{\"\\E([^\\}]*?)\\Q\"=>\\E\\s*\\Q[\\E((\"[^\\}]*?\"(, )?)*)\\Q]}\\E", (Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
        Pattern valuePattern = Pattern.compile("\"(.*?)\"");
        
        Matcher hashes = groupPattern.matcher(serialization);
        while (hashes.find()) {
            String key = hashes.group(1);
            String list = hashes.group(2);
            List<String> valueList = new ArrayList<String>();
            if (list != null) {
                Matcher values = valuePattern.matcher(list);
                while (values.find()) {
                    valueList.add(values.group(1));
                }
            }
            map.put(key,  valueList);
        }
    }
    
    /**
     * Returns the list of pids for the given id.  This 
     * value comes from the parsed serialized hash.  This
     * method returns null for keys that did not appear in
     * the source data, but otherwise returns a list 
     * (which may or may not be empty).
     */
    public List<String> getDigitizedItemPagePids(String id) {
        return map.get(id);
    }

    public String getExemplar(String id) {
        List<String> values = map.get(id);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        } else {
            return null;
        }
    }

}
