package com.github.zhgzhg.tinydi.meta.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the classes providing {@link Recorded} components.
 * Such classes are always singletons.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Registrar {
    /**
     * The name the annotated component which will registered with. If not specified the simple class name will be used instead.
     * @return Nonnull string with the component's name, or a blank one.
     */
    String value() default "";
}
