package com.vonhof.webi;

/**
 * Path patterns are used to identify paths
 * @author Henrik Hofmeister <@vonhofdk>
 */
class PathPattern {
    private final String ptrn;

    public PathPattern(String expression) {
        this.ptrn = expression;
    }
    public boolean matches(String path) {
        return path.startsWith(ptrn);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PathPattern other = (PathPattern) obj;
        if ((this.ptrn == null) ? (other.ptrn != null) : !this.ptrn.equals(other.ptrn)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + (this.ptrn != null ? this.ptrn.hashCode() : 0);
        return hash;
    }

    public String trim(String path) {
        if (matches(path)) {
            return path.substring(ptrn.length()-1);
        }
        return path;
    }

    @Override
    public String toString() {
        return this.ptrn;
    }
}
