package com.github.zhgzhg.tinydi.meta.annotations;

import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers class instances as dependencies. The action should happen inside @{@link Registrar} annotated class.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Recorded {
    /**
     * The name of the registered component. If not specified the method name returning instance of the component will be used instead.
     * @return Nonnull string of the component name which may be empty.
     */
    String value() default "";

    /**
     * The instantiation approach to be used during injection.
     * @return The specified instantiation strategy enum value, which by default is {@link ScopeDI#SINGLETON}.
     */
    ScopeDI scope() default ScopeDI.SINGLETON;
}
