package com.github.zhgzhg.tinydi.meta.annotations;

import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the classes eligible for dependency injection.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Supervised {
    /** Specifies the name the annotated component will registered with. If not specified the simple class name will be used instead. */
    String value() default "";

    /** The instantiation approach to be used during injection. */
    ScopeDI scope() default ScopeDI.SINGLETON;
}
