package com.vonhof.webi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
        for(Entry<PathPattern,T> entry:inner.entrySet()) {
            if (entry.getKey().matches(path))
                return entry.getKey();
        }
        return null;
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
}
