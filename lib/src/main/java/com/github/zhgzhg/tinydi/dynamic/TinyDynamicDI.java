package com.github.zhgzhg.tinydi.dynamic;

import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

/**
 * Helper utilities for dynamic dependency injection.
 */
@UtilityClass
public class TinyDynamicDI {

    /**
     * Helper used to associate a @{@link Recorded} annotation implementation and an instance of an object not implementing.
     */
    @RequiredArgsConstructor
    public static class TransparentInvocationHandler implements InvocationHandler {
        @NonNull
        private final Supplier<Object> instanceSupplier;
        @NonNull
        private final Class<?> instanceClass;
        @NonNull
        private final Recorded recorded;

        //private Object actualInstance;

        Object supplyInstance() {
            /*if (recorded.scope() == ScopeDI.SINGLETON) {
                if (actualInstance == null) {
                    actualInstance = instanceSupplier.get();
                }
                return actualInstance;
            }*/
            return instanceSupplier.get();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() != Object.class) {
                if (method.getDeclaringClass().isAssignableFrom(this.recorded.getClass())) {
                    return method.invoke(recorded, args);
                }
            }

            Object instance = this.supplyInstance();
            return method.invoke(instance, args);
        }
    }

    /**
     * Attaches a {@link Recorded} instance to non-annotated object by creating a proxy object around it.
     * Useful when dynamically injecting more components into an already initialized TinyDI context.
     * @param instanceSupplier The instance supplier whose instance will be bound to the annotation.
     * @param instanceClass The type of the instance.
     * @param recorded An {@link Recorded} instance with the specified values. Usually {@link RecordedAnnotation} can be used for the purpose.
     * @param <T> The type the result to be casted to. It must be an interface. {@link Recorded} is a safe choice.
     * @return A proxy instance bounding <i>toInstance</i>, it's implemented interfaces along with the passed <i>record</i> one.
     * @throws IllegalArgumentException if <i>&lt;T&gt;</i> is not an interface
     * @throws NullPointerException if <i>toInstance</i> or <i>record</i> are null
     */
    @SuppressWarnings("unchecked")
    public static <T> T attachRecordedAnnotation(@NonNull Supplier<Object> instanceSupplier, Class<?> instanceClass, @NonNull Recorded recorded) {
        Class<?>[] interfaces = instanceClass.getInterfaces();
        Class<?>[] recordInterfaces = recorded.getClass().getInterfaces();
        Class<?>[] allImplementedInterfaces = new Class<?>[interfaces.length + recordInterfaces.length];

        System.arraycopy(interfaces, 0, allImplementedInterfaces, 0, interfaces.length);
        System.arraycopy(recordInterfaces, 0, allImplementedInterfaces, interfaces.length, recordInterfaces.length);
        //allImplementedInterfaces[allImplementedInterfaces.length - 1] = Supplier.class;

        return (T) Proxy.newProxyInstance(
                TinyDynamicDI.class.getClassLoader(), //instanceClass.getClassLoader(),
                allImplementedInterfaces,
                new TransparentInvocationHandler(instanceSupplier, instanceClass, recorded)
        );
    }

    /**
     * Obtains the class behind a proxy object with TinyDI's {@link TransparentInvocationHandler}.
     * @param instance The instance to work with.
     * @return The class behind the proxy object, or the <i>instance</i>'s class if the latter is not a proxy.
     * @throws ClassCastException if the proxy object's handler is not of {@link TransparentInvocationHandler} type.
     */
    public static Class<?> realClass(@NonNull Object instance) {
        Class<?> clazz = instance.getClass();

        while (clazz != null && Proxy.isProxyClass(clazz)
                && Proxy.getInvocationHandler(instance).getClass().isAssignableFrom(TransparentInvocationHandler.class)) {

            instance = Proxy.getInvocationHandler(instance);
            clazz = ((TransparentInvocationHandler) instance).instanceClass;
        }

        return clazz;
    }

    /**
     * Obtains the instance behind a proxy object with TinyDI's {@link TransparentInvocationHandler}.
     * @param instance The instance to work with.
     * @return The instance behind the proxy object, or the actual <i>instance</i> if the latter is not a proxy.
     * @throws ClassCastException if the proxy object's handler is not of {@link TransparentInvocationHandler} type.
     */
    public static Object realInstance(Object instance) {
        while (instance != null && Proxy.isProxyClass(instance.getClass())
                && Proxy.getInvocationHandler(instance).getClass().isAssignableFrom(TransparentInvocationHandler.class)) {

            instance = ((TransparentInvocationHandler) Proxy.getInvocationHandler(instance)).supplyInstance();
        }
        return instance;
    }

    /**
     * Returns the instantiation scope of a proxied via {@link #attachRecordedAnnotation} instance.
     * @param instance The instance to inspect
     * @return Nonnull value, or null if the instance is not proxied.
     */
    public static ScopeDI scopeOfProxy(Object instance) {
        if (instance != null && Proxy.isProxyClass(instance.getClass())
                && Proxy.getInvocationHandler(instance).getClass().isAssignableFrom(TransparentInvocationHandler.class)) {

            return ((TransparentInvocationHandler) Proxy.getInvocationHandler(instance)).recorded.scope();
        }

        return null;
    }
}
