package com.github.zhgzhg.tinydi.meta.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the classes providing {@link Recorded} components.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Registrar {
    /** Specifies the name the annotated component will registered with. If not specified the simple class name will be used instead. */
    String value() default "";
}
