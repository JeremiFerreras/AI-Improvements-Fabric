package com.builtbroken.ai.improvements.modifier.filters;

public enum FilterResult {
    /** Nothing happened; continue to the next filter. */
    DID_NOTHING,
    /** The filter accepted/consumed the entity; stop this layer. */
    FILTERED,
    /** Entity was modified, but continue through remaining filters. */
    MODIFIED
}
