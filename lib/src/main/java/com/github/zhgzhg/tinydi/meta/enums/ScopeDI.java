package com.github.zhgzhg.tinydi.meta.enums;

/** Specifies the way the particular component will be instantiated during injection */
public enum ScopeDI {
    /** A component is created once, and that single instance is shared within the current TinyDI execution context. */
    SINGLETON,
    /** A new instance of the component is created for every dependent. */
    PROTOTYPE
}