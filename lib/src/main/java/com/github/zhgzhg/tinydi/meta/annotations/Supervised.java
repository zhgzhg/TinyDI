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
    /**
     * The name the annotated component which will be registered with. If it's not specified the simple class name will be used instead.
     * @return Nonnull string with the component's name, or a blank one.
     */
    String value() default "";

    /**
     * The instantiation approach to be used during injection.
     * @return The specified instantiation strategy enum value, which by default is {@link ScopeDI#SINGLETON}.
     */
    ScopeDI scope() default ScopeDI.SINGLETON;
}
