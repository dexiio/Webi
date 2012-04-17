package com.vonhof.webi;

import java.util.*;
import java.util.Map.Entry;

/**
 * Collection of path patterns
 * @author Henrik Hofmeister <@vonhofdk>
 */
public class PathPatternMap<T>  {
    private final Map<PathPattern,T> inner = new LinkedHashMap<PathPattern, T>();
    
    public void put(String path,T value) {
        put(new PathPattern(path), value);
    }
    
    public void put(PathPattern path,T value) {
        inner.put(path, value);
    }
    
    public T get(String path) {
        PathPattern ptrn = getPattern(path);
        if (ptrn == null) 
            return null;
        return inner.get(ptrn);
    }
    protected PathPattern getPattern(String path) {
        PathPattern best = null;
        int longest = 0;
                
        for(Entry<PathPattern,T> entry:inner.entrySet()) {
            PathPattern key = entry.getKey();
            int length = key.toString().length();
            if (key.matches(path) && longest < length) {
                best = key;
                longest = length;
            }
        }
        return best;
    }
    
    public List<T> getAll(String path) {
        List<T> out = new ArrayList<T>();
        for(Entry<PathPattern,T> entry:inner.entrySet()) {
            if (entry.getKey().matches(path))
                out.add(entry.getValue());
        }
        return out;
    }


    protected String trimContext(String path) {
        PathPattern pattern = getPattern(path);
        if (pattern == null) 
            return path;
        return pattern.trim(path);
    }
    public Set<Entry<String,T>> entrySet() {
        HashMap<String,T> map = new HashMap<String, T>();
        for(Entry<PathPattern,T> entry:inner.entrySet()) {
            map.put(entry.getKey().toString(),entry.getValue());
        }
                
        return map.entrySet();
    }
}
