package com.github.zhgzhg.tinydi;

import com.github.zhgzhg.tinydi.components.EntryPoint;
import com.github.zhgzhg.tinydi.components.Environment;
import com.github.zhgzhg.tinydi.dynamic.TinyDynamicDI;
import com.github.zhgzhg.tinydi.meta.annotations.KnownAs;
import com.github.zhgzhg.tinydi.meta.annotations.Recorded;
import com.github.zhgzhg.tinydi.meta.annotations.Registrar;
import com.github.zhgzhg.tinydi.meta.annotations.Supervised;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;
import io.github.classgraph.AnnotationEnumValue;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationInfoList;
import io.github.classgraph.AnnotationParameterValueList;
import io.github.classgraph.ArrayTypeSignature;
import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassRefOrTypeVariableSignature;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.ScanResult;
import io.github.classgraph.TypeSignature;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The context in which dependency injection will happen.
 * This class performs classpath scanning, annotation processing, and dependency injection.
 */
public class TinyDI implements Runnable {

    private static final String REGISTRAR_ANNOTATION_NAME = Registrar.class.getCanonicalName();
    private static final String RECORDED_ANNOTATION_NAME = Recorded.class.getCanonicalName();
    private static final String SUPERVISED_ANNOTATION_NAME = Supervised.class.getCanonicalName();
    private static final String KNOWN_AS_ANNOTATION_NAME = KnownAs.class.getCanonicalName();

    private static final List<String> CLASS_LVL_COMPONENT_ANNOTATIONS_FOR_REGISTRATION =
            Arrays.asList(REGISTRAR_ANNOTATION_NAME, SUPERVISED_ANNOTATION_NAME);

    private final ConcurrentMap<String, Class<?>> registry = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> instances = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Supplier<Object>> proxyInstances = new ConcurrentHashMap<>();

    private final Set<String> basePackages;
    private final Set<String> ignoredBasePackages;
    private final Set<String> ignoredClasses;
    private final List<Recorded> additionalRecords;
    private final Set<String> overridingClasspaths;
    private String staticClasspathScan;
    private boolean aggressiveEncapsulationCircumventing;

    /**
     * TinyDI's configuration helper.
     */
    public static class Config {
        private final TinyDI tinyDI;
        private boolean isLocked;

        private Config() {
            this.tinyDI = new TinyDI();
            this.isLocked = false;
        }

        private void checkLock() {
            if (this.isLocked) {
                throw new IllegalArgumentException("No further modifications allowed by the builder!");
            }
        }

        /**
         * Specifies static, serialized in JSON format classpath scan to be used during the DI process
         * @param jsonSource Valid JSON file resource containing the serialized classpath scan result.
         * @param encoding The charset encoding of the JSON. See {@link StandardCharsets}.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        @SneakyThrows
        public Config staticScan(@NonNull InputStream jsonSource, @NonNull String encoding) {
            checkLock();

            if (!this.tinyDI.basePackages.isEmpty()) {
                throw new IllegalStateException("Cannot combine static json scanning with base packages!");
            }

            String json;
            try (ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];

                for (int length; (length = jsonSource.read(buffer)) != -1; ) {
                    result.write(buffer, 0, length);
                }
                json = result.toString(encoding);
            }
            this.tinyDI.staticClasspathScan = json;
            return this;
        }

        /**
         * Specifies static, serialized in JSON format classpath scan to be used during the DI process.
         * @param json A JSON string with the serialized classpath scan result.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        @SneakyThrows
        public Config staticScan(@NonNull String json) {
            checkLock();

            try (ByteArrayInputStream jsonSource = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                return this.staticScan(jsonSource, StandardCharsets.UTF_8.name());
            }
        }

        /**
         * Includes arbitrary, unannotated object instances for injection.
         * They have to be bound dynamically with @{@link Recorded} annotation though. To accomplish that see {@link TinyDynamicDI}.
         * @param records One or more records to add.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        public Config records(Recorded... records) {
            checkLock();

            if (records != null) {
                tinyDI.additionalRecords.clear();
                Collections.addAll(tinyDI.additionalRecords, records);
            }

            return this;
        }

        /**
         * Specifies 1 or more fully-qualified base packages for recursive scanning. Real JVM bytecode must be present.
         * @param basePackages One or more fully-qualified base package names.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        public Config basePackages(String... basePackages) {
            checkLock();

            if (this.tinyDI.staticClasspathScan != null) {
                throw new IllegalStateException("Cannot combine base packages with static json scanning!");
            }

            if (basePackages != null) {
                tinyDI.basePackages.clear();
                Collections.addAll(tinyDI.basePackages, basePackages);
            }

            return this;
        }

        /**
         * Specifies 1 or more fully-qualified base packages to be excluded during recursive scanning. Real JVM bytecode must be present.
         * @param ignoredBasePackages One or more fully-qualified base package names.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        public Config ignoredBasePackages(String... ignoredBasePackages) {
            checkLock();

            if (ignoredBasePackages != null) {
                tinyDI.ignoredBasePackages.clear();
                Collections.addAll(tinyDI.ignoredBasePackages, ignoredBasePackages);
            }

            return this;
        }

        /**
         * Specifies 1 or more fully-qualified classes to be excluded during recursive scanning. Real JVM bytecode must be present.
         * @param ignoredClasses One or more fully-qualified class names.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        public Config ignoredClasses(String... ignoredClasses) {
            checkLock();

            if (ignoredClasses != null) {
                tinyDI.ignoredClasses.clear();
                Collections.addAll(tinyDI.ignoredClasses, ignoredClasses);
            }

            return this;
        }

        /**
         * Specifies 1 or more fully-qualified classpaths to be used during recursive scanning. They override the automatically detected
         * classpath. The module path won't be scanned in this case.
         * @param overridingClasspaths One or more fully-qualified class paths.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        public Config overrideClasspath(String... overridingClasspaths) {
            checkLock();

            if (overridingClasspaths != null) {
                tinyDI.overridingClasspaths.clear();
                Collections.addAll(tinyDI.overridingClasspaths, overridingClasspaths);
            }

            return this;
        }

        /**
         * Registers custom command line arguments, environment parameters, and properties entirely substituting the detected.
         * @param args Command line arguments. By default an empty array.
         * @param environmentVars Environment variables. By default obtained via <i>System.getenv()</i>.
         * @param envProps Environment properties. By default obtained via <i>System.getProperties()</i>.
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        public Config withEnvironment(String[] args, Map<String, String> environmentVars, Properties envProps) {
            checkLock();
            this.tinyDI.instances.putIfAbsent(Environment.class.getSimpleName(), new Environment(args, environmentVars, envProps));
            this.tinyDI.registry.putIfAbsent(Environment.class.getSimpleName(), Environment.class);
            return this;
        }

        /**
         * Activates more aggressive encapsulation circumventing for Java version 16 or later. The hack relies on additional library which
         * must be manually added as a dependency - the @see <a href="https://github.com/toolfactory/narcissus">Narcissus</a>.
         * @param useEncapsulationCircumventing Set to true to activate the aggressive hack, and use false to deactivate it
         * @return The belonging instance for fluent config.
         * @throws IllegalArgumentException If {@link #configure()} has been called previously.
         */
        public Config aggressiveEncapsulationCircumventing(boolean useEncapsulationCircumventing) {
            checkLock();
            this.tinyDI.aggressiveEncapsulationCircumventing = useEncapsulationCircumventing;
            return this;
        }

        /**
         * Locks TinyDI's configuration and returns the configured instance.
         * @return Configured {@link TinyDI} instance.
         */
        public TinyDI configure() {
            this.isLocked = true;

            if (!this.tinyDI.registry.containsKey(Environment.class.getSimpleName())) {
                this.tinyDI.instances.putIfAbsent(Environment.class.getSimpleName(), new Environment());
                this.tinyDI.registry.putIfAbsent(Environment.class.getSimpleName(), Environment.class);
            }

            return this.tinyDI;
        }

        /**
         * Configures wrapped {@link TinyDI} instance for the only purpose of producing static JSON result of the scanned class path and
         * the found elements suitable for DI. Can be used to gather the needed for DI information during the build time which can speed up
         * the execution and allow TinyDI to work on environments with limited reflection capabilities like Android and GraalVM native
         * images. See <i>com.github.zhgzhg.tinydi.build.BuildTimeScan</i> utility for more information.
         * @return JSON string with the found elements of interest
         */
        public String configureForStaticScan() {
            this.configure();
            try (ScanResult scanResult = this.tinyDI.initiateNewScan()) {
                return scanResult.toJSON();
            }
        }
    }

    /**
     * Provides builder class through which {@link TinyDI} can be configured and instantiated.
     * @return New instance of {@link Config} eventually producing new {@link TinyDI} context.
     */
    public static Config config() {
        return new Config();
    }

    private TinyDI() {
        this.basePackages = new LinkedHashSet<>();
        this.ignoredBasePackages = new LinkedHashSet<>();
        this.ignoredClasses = new LinkedHashSet<>();
        this.additionalRecords = new LinkedList<>();
        this.overridingClasspaths = new LinkedHashSet<>();
        this.aggressiveEncapsulationCircumventing = false;
        registry.put(this.getClass().getSimpleName(), this.getClass());
        instances.put(this.getClass().getSimpleName(), this);
    }

    protected void registerProxiedRecords(List<Recorded> additionalRecords) {
        if (additionalRecords.isEmpty()) {
            return;
        }

        for (Recorded recd : additionalRecords) {
            String componentName = recd.value();
            if (componentName == null || componentName.isBlank()) { // null check IS recommended
                componentName = TinyDynamicDI.realClass(recd).getSimpleName();
            }

            this.registry.putIfAbsent(componentName, TinyDynamicDI.realClass(recd));

            if (recd.scope() == ScopeDI.SINGLETON) {
                this.instances.putIfAbsent(componentName, TinyDynamicDI.realInstance(recd));
            } else if (recd.scope() == ScopeDI.PROTOTYPE) { // may contain supplier<object> or supplier<proxy<object>>
                this.proxyInstances.putIfAbsent(componentName, () -> recd);
            }
        }
    }

    private ScanResult initiateNewScan() {
        ClassGraph.CIRCUMVENT_ENCAPSULATION = aggressiveEncapsulationCircumventing;

        ClassGraph classGraph = new ClassGraph()
                .rejectPackages(this.ignoredBasePackages.toArray(new String[0]))
                .rejectClasses(this.ignoredClasses.toArray(new String[0]))
                .acceptPackages(this.basePackages.toArray(new String[0]))
                .enableAllInfo();

        if (!this.overridingClasspaths.isEmpty()) {
            classGraph = classGraph.overrideClasspath(overridingClasspaths);
        }

        return classGraph.scan();
    }

    @SneakyThrows
    public void run() {
        this.registerProxiedRecords(this.additionalRecords);

        if (this.staticClasspathScan == null) {
            try (ScanResult scanResult = this.initiateNewScan()) {
                this.instantiateAllWithDI(scanResult, REGISTRAR_ANNOTATION_NAME, this::instantiateRecords);
                this.instantiateAllWithDI(scanResult, SUPERVISED_ANNOTATION_NAME, (classInfo, instance) -> { });
            }
        } else {
            try (ScanResult scanResult = ScanResult.fromJSON(this.staticClasspathScan)) {
                this.instantiateAllWithDI(scanResult, REGISTRAR_ANNOTATION_NAME, this::instantiateRecords);
                this.instantiateAllWithDI(scanResult, SUPERVISED_ANNOTATION_NAME, (classInfo, instance) -> { });
            }
        }

        this.executeEntryPoints();
    }

    @SneakyThrows
    private void instantiateAllWithDI(
            ScanResult scanResult, String annotationCanonicalName, BiConsumer<ClassInfo, Object> proceedOnInstance) {

        ClassInfoList initialList = scanResult.getClassesWithAnnotation(annotationCanonicalName);
        Map<ClassInfo, Object> instantiated = new HashMap<>(initialList.size());

        Set<ClassInfo> ignoredElements = new HashSet<>();

        for (int i = 0; instantiated.size() != (initialList.size() - ignoredElements.size())
                && i < (initialList.size() - ignoredElements.size()) * 4; ++i) {

            for (ClassInfo classInfo : initialList) {
                if (instantiated.containsKey(classInfo)) {
                    continue;
                }

                if (!this.ignoredClasses.isEmpty() && this.ignoredClasses.contains(classInfo.loadClass().getCanonicalName())) {
                    ignoredElements.add(classInfo);
                    continue;
                }

                if (!this.ignoredBasePackages.isEmpty()
                        && (this.ignoredBasePackages.contains(classInfo.getPackageName())
                        || this.ignoredBasePackages.stream().anyMatch(pkgName -> pkgName.startsWith(classInfo.getPackageName())))) {

                    ignoredElements.add(classInfo);
                    continue;
                }

                MethodInfo methodInfo = selectTheEasiestConstructor(classInfo);
                Object registrarInstance = this.call(scanResult, methodInfo);
                if (registrarInstance != null) {
                    proceedOnInstance.accept(classInfo, registrarInstance);
                    instantiated.put(classInfo, registrarInstance);
                }
            }
        }

        if (instantiated.size() != (initialList.size() - ignoredElements.size())) {
            initialList.removeAll(instantiated.keySet());
            throw new IllegalStateException("Couldn't instantiate the @" + annotationCanonicalName + " class(es) : "
                    + initialList.stream().map(ClassInfo::getName).collect(Collectors.joining(", ")));
        }
    }

    @SneakyThrows
    private void instantiateRecords(ClassInfo classInfo, Object registrarInstance) {
        if (classInfo == null || registrarInstance == null) return;

        for (MethodInfo methodInfo : classInfo.getMethodInfo()) {

            AnnotationInfo annotationInfo = methodInfo.getAnnotationInfo(RECORDED_ANNOTATION_NAME);

            if (annotationInfo == null) {
                continue;
            }

            if (methodInfo.isStatic()) {
                throw new IllegalArgumentException("@Recorded annotating static method: "
                        + classInfo.getName() + "#" + methodInfo.getName() + "()");
            }

            if ("void".equals(methodInfo.getTypeSignatureOrTypeDescriptor().getResultType().toStringWithSimpleNames())) {
                throw new IllegalArgumentException("@Recorded annotating method with void return type: "
                        + classInfo.getName() + "#" + methodInfo.getName() + "()");
            }

            MethodParameterInfo[] parameterInfo = methodInfo.getParameterInfo();
            if (parameterInfo != null && parameterInfo.length > 0) {
                throw new IllegalArgumentException("@Recorded " + classInfo.getName() + "#" + methodInfo.getName()
                        + "(...) is accepting parameters");
            }

            this.call(null, methodInfo, registrarInstance);
        }
    }

    private void executeEntryPoints() {
        for (Map.Entry<String, Class<?>> component : this.registry.entrySet()) {
            if (EntryPoint.class.isAssignableFrom(component.getValue())) {
                ((EntryPoint) this.componentFor(component.getKey())).run();
            }
        }
    }

    private Object call(ScanResult scanResult, MethodInfo methodInfo) {
        return this.call(scanResult, methodInfo, null);
    }

    @SneakyThrows
    private Object call(ScanResult scanResult, MethodInfo methodInfo, Object instanceOfOrigin) {
        if (methodInfo == null) {
            return null;
        }

        ScopeDI instantiationMode = this.obtainComponentInstantiationMode(methodInfo);
        String componentName = obtainComponentName(methodInfo);

        Object instance = null;
        if (instantiationMode == ScopeDI.SINGLETON) {
            instance = this.instances.get(componentName);
        } else if (instantiationMode == ScopeDI.PROTOTYPE) {
            instance = nestedSupplierResolver(this.proxyInstances.get(componentName));
        }

        if (instance != null) {
            if (!this.obtainMethodOrCtorReturnType(methodInfo).isAssignableFrom(this.registry.get(componentName))) {
                throw new IllegalStateException("Already registered component with name " + componentName);
            }
            return instance;
        }

        List<MethodParameterInfo> parametersInfo = this.obtainParameterInfoFromMethodInfo(methodInfo);

        List<Object> parameterInstances = new LinkedList<>();
        if (instanceOfOrigin != null) {
            parameterInstances.add(instanceOfOrigin);
        }

        for (MethodParameterInfo param : parametersInfo) {
            Class<?> parameterTypeClass = null;
            String recordName = null;

            // find type class based on string alias from @KnownAs
            AnnotationInfoList typeAnnotationInfo = param.getAnnotationInfo();
            if (typeAnnotationInfo == null) {
                typeAnnotationInfo = new AnnotationInfoList(0);
            }

            AnnotationInfoList preferredComponentNameList = typeAnnotationInfo
                    .filter(annotationInfo -> KNOWN_AS_ANNOTATION_NAME.equals(annotationInfo.getClassInfo().getName()));

            if (!preferredComponentNameList.isEmpty()) {
                recordName = (String) preferredComponentNameList.get(0)
                        .getParameterValues(false)
                        .getValue("value");

                parameterTypeClass = this.registry.get(recordName);
            } else {
                // find the parameter type via name deduced by its class name
                TypeSignature typeSignature = param.getTypeSignatureOrTypeDescriptor();

                if (typeSignature instanceof BaseTypeSignature) {
                    parameterTypeClass = MethodType.methodType(((BaseTypeSignature) typeSignature).getType()).wrap().returnType();
                    parameterTypeClass = this.reloadWithLocalClassLoader(parameterTypeClass);
                } else if (typeSignature instanceof ClassRefOrTypeVariableSignature) {
                    parameterTypeClass = this.reloadWithLocalClassLoader(((ClassRefTypeSignature) typeSignature).loadClass());
                } else if (typeSignature instanceof ArrayTypeSignature) {
                    parameterTypeClass = this.reloadWithLocalClassLoader(((ArrayTypeSignature) typeSignature).loadClass());
                }

                if (parameterTypeClass != null) {

                    if (parameterTypeClass.isInterface() || Modifier.isAbstract(parameterTypeClass.getModifiers())) {
                        final Class<?> paramTypeClass = parameterTypeClass;

                        recordName = this.registry.entrySet().stream()
                                .filter(entry -> {
                                    Class<?> clazz = entry.getValue();
                                    return (!clazz.isInterface()) && (!Modifier.isAbstract(clazz.getModifiers())) && paramTypeClass.isAssignableFrom(clazz);
                                })
                                .map(Map.Entry::getKey)
                                .findFirst()
                                .orElse(null);

                    } else {
                        String potentialRecordName = parameterTypeClass.getSimpleName();
                        if (this.registry.get(potentialRecordName) != null
                                && parameterTypeClass.isAssignableFrom(this.registry.get(potentialRecordName))) {
                            recordName = potentialRecordName;
                        } else {
                            // attempt deducing by class value, but it has to be a registered only once

                            final Class<?> paramTypeClass = parameterTypeClass;

                            List<Map.Entry<String, Class<?>>> candidates = this.registry.entrySet()
                                    .stream()
                                    .filter(entry -> entry.getValue() == paramTypeClass)
                                    .limit(2)
                                    .collect(Collectors.toList());

                            if (candidates.size() == 1) {
                                recordName = candidates.get(0).getKey();
                            } else if (candidates.size() > 1) {
                                throw new IllegalStateException("Too many candidates for unnamed constructor parameter of type "
                                        + parameterTypeClass.getCanonicalName() + " in method: " + methodInfo
                                        + ", class " + methodInfo.getClassInfo().getName());
                            }
                        }
                    }

                }
            }

            if (parameterTypeClass == null || recordName == null || recordName.isBlank()) {
                continue;
            }

            // decide how to instantiate the found parameter

            Object o = this.instances.get(recordName);
            if (o != null) {
                parameterInstances.add(o);
            } else {
                o = this.proxyInstances.get(recordName);
                if (o != null) {
                    o = nestedSupplierResolver(o);
                } else {
                    ClassInfo classInfo = scanResult.getClassInfo(parameterTypeClass.getName());
                    if (classInfo != null) {
                        MethodInfo paramsMethodInfo = selectTheEasiestConstructor(classInfo);
                        o = call(scanResult, paramsMethodInfo);
                    }
                }
                if (o != null) {
                    parameterInstances.add(o);
                }
            }

        }


        Object invocationResult = null;

        if (parametersInfo.size() == (parameterInstances.size() - (instanceOfOrigin == null ? 0 : 1))) {

            if (methodInfo.isConstructor()) {
                Constructor<?> constructor = this.constructorWithLocalClassLoader(methodInfo.loadClassAndGetConstructor());
                constructor.setAccessible(true);
                Object[] params = parameterInstances.toArray();
                invocationResult = constructor.newInstance(params);

                if (instantiationMode == ScopeDI.PROTOTYPE && !this.proxyInstances.containsKey(componentName)) {
                    Supplier<Object> sup = new Supplier<>() {
                        @SneakyThrows
                        @Override
                        public Object get() {
                            return constructor.newInstance(params);
                        }
                    };
                    this.proxyInstances.putIfAbsent(componentName, sup);
                }
            } else {
                Method method = this.methodWithLocalClassLoader(methodInfo.loadClassAndGetMethod());
                method.setAccessible(true);
                Object obj = (parameterInstances.isEmpty() ? null : parameterInstances.remove(0));
                Object[] params = parameterInstances.toArray();
                invocationResult = method.invoke(obj, params);

                if (instantiationMode == ScopeDI.PROTOTYPE && !this.proxyInstances.containsKey(componentName)) {
                    Supplier<Object> sup = new Supplier<>() {
                        @SneakyThrows
                        @Override
                        public Object get() {
                            return method.invoke(obj, params);
                        }
                    };
                    this.proxyInstances.putIfAbsent(componentName, sup);
                }

            }

            if (invocationResult != null) {
                this.registry.putIfAbsent(componentName, invocationResult.getClass());
                if (instantiationMode == ScopeDI.SINGLETON) {
                    this.instances.putIfAbsent(componentName, invocationResult);
                }
            }
        }

        return invocationResult;
    }

    private List<MethodParameterInfo> obtainParameterInfoFromMethodInfo(MethodInfo methodInfo) {
        return Arrays.stream(methodInfo.getParameterInfo())
                .filter(paramInfo -> !paramInfo.getTypeDescriptor().toStringWithSimpleNames().equals(methodInfo.getClassName()))
                .collect(Collectors.toUnmodifiableList());
    }

    @SuppressWarnings("unchecked")
    private static Object nestedSupplierResolver(Object supplier) {
        Object result = supplier;
        while (result instanceof Supplier) {
            result = ((Supplier<Object>) result).get();
        }
        return TinyDynamicDI.realInstance(result);
    }

    @SneakyThrows
    protected Constructor<?> constructorWithLocalClassLoader(Constructor<?> ctor) {
        Class<?> myClass = this.reloadWithLocalClassLoader(ctor.getDeclaringClass());
        Class<?>[] parameters = Stream.of(ctor.getParameterTypes()).map(this::reloadWithLocalClassLoader).toArray(Class<?>[]::new);
        return myClass.getDeclaredConstructor(parameters);
    }

    @SneakyThrows
    protected Method methodWithLocalClassLoader(Method method) {
        Class<?> myClass = this.reloadWithLocalClassLoader(method.getDeclaringClass());
        Class<?>[] parameters = Stream.of(method.getParameterTypes()).map(this::reloadWithLocalClassLoader).toArray(Class<?>[]::new);
        return myClass.getDeclaredMethod(method.getName(), parameters);
    }

    @SneakyThrows
    protected Class<?> reloadWithLocalClassLoader(Class<?> foreignClass) {
        ClassLoader myClassLoader = this.getClass().getClassLoader();

        if (foreignClass.getClassLoader() != myClassLoader && !foreignClass.isPrimitive()) {
            return Class.forName(foreignClass.getCanonicalName(), true, myClassLoader);
        }

        return foreignClass;
    }

    private static MethodInfo selectTheEasiestConstructor(ClassInfo classInfo) {

        MethodInfoList methods = classInfo.getDeclaredConstructorInfo();

        if (methods == null || methods.isEmpty()) return null;

        return methods.stream()
                .filter(method -> {
                    // public or package private constructor

                    int modifiers = method.getModifiers();
                    return (Modifier.isPublic(modifiers)
                            || !(Modifier.isPublic(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isProtected(modifiers)));
                })
                .min(Comparator.comparingInt(methodInfo -> methodInfo.getParameterInfo().length))
                .orElse(null);
    }

    private ScopeDI obtainComponentInstantiationMode(MethodInfo methodInfo) {
        if (methodInfo.isConstructor()) {
            ClassInfo classInfo = methodInfo.getClassInfo();

            AnnotationInfo annotationInfo = classInfo.getAnnotationInfo(SUPERVISED_ANNOTATION_NAME);
            if (annotationInfo != null) {
                AnnotationParameterValueList parameterValues = annotationInfo.getParameterValues();
                return (ScopeDI) ((AnnotationEnumValue) parameterValues.getValue("scope")).loadClassAndReturnEnumValue();
            }

            return ScopeDI.SINGLETON;
        }

        AnnotationInfo annotationInfo = methodInfo.getAnnotationInfo(RECORDED_ANNOTATION_NAME);
        if (annotationInfo == null) {
            throw new IllegalStateException(methodInfo.toStringWithSimpleNames() + " not eligible for DI!");
        }

        return (ScopeDI) ((AnnotationEnumValue) annotationInfo.getParameterValues().getValue("scope"))
                .loadClassAndReturnEnumValue();
    }

    private String obtainComponentName(MethodInfo methodInfo) {

        if (methodInfo.isConstructor()) {
            ClassInfo classInfo = methodInfo.getClassInfo();

            String componentName = null;

            for (String annotationName : CLASS_LVL_COMPONENT_ANNOTATIONS_FOR_REGISTRATION) {
                AnnotationInfo annotationInfo = classInfo.getAnnotationInfo(annotationName);
                if (annotationInfo != null) {
                    AnnotationParameterValueList parameterValues = annotationInfo.getParameterValues();
                    componentName = (String) parameterValues.getValue("value");
                    break;
                }
            }

            return (componentName == null || componentName.isBlank() ? classInfo.getSimpleName() : componentName);
        }

        AnnotationInfo annotationInfo = methodInfo.getAnnotationInfo(RECORDED_ANNOTATION_NAME);
        if (annotationInfo == null) {
            throw new IllegalStateException(methodInfo.toStringWithSimpleNames() + " not eligible to register DI components!");
        }

        String componentName;
        AnnotationParameterValueList defaultParameterValues = annotationInfo.getParameterValues();
        componentName = (String) defaultParameterValues.getValue("value");

        return (componentName == null || componentName.isBlank() ? methodInfo.getName() : componentName);
    }

    @SneakyThrows
    private Class<?> obtainMethodOrCtorReturnType(MethodInfo methodInfo) {
        if (methodInfo.isConstructor()) {
            return this.reloadWithLocalClassLoader(methodInfo.getClassInfo().loadClass());
        }

        Class<?> result = null;

        TypeSignature resultType = methodInfo.getTypeSignatureOrTypeDescriptor().getResultType();
        if (resultType instanceof BaseTypeSignature) {
            result = MethodType.methodType(((BaseTypeSignature) resultType).getType()).wrap().returnType();
            result = this.reloadWithLocalClassLoader(result);
        } else if (resultType instanceof ClassRefTypeSignature) {
            result = this.reloadWithLocalClassLoader(((ClassRefTypeSignature) resultType).loadClass());
        } else if (resultType instanceof ArrayTypeSignature) {
            result = this.reloadWithLocalClassLoader(((ArrayTypeSignature) resultType).loadClass());
        }

        return result;
    }

    /**
     * Returns a collection of all the registered component names.
     * @return A set with the available component names.
     */
    public Set<String> registeredComponentNames() {
        return this.registry.keySet();
    }

    /**
     * Returns the class definition of an already registered component.
     * @param componentName The name the component was registered with.
     * @return The registered {@link Class} instance or null if such is not found.
     */
    public Class<?> registeredComponentClass(String componentName) {
        return this.registry.get(componentName);
    }

    /**
     * Returns the name of the first found registered component with a specific type.
     * @param componentClass The component type to search for.
     * @return The name the component or null.
     */
    public String registeredComponentName(Class<?> componentClass) {
        if (componentClass == null) return null;

        if (componentClass.isPrimitive()) {
            componentClass = MethodType.methodType(componentClass).wrap().returnType();
        }

        for (Map.Entry<String, Class<?>> entry : this.registry.entrySet()) {
            if (entry.getValue() == componentClass) {
                return entry.getKey();
            }
        }

        for (Map.Entry<String, Class<?>> entry : this.registry.entrySet()) {
            if (componentClass.isAssignableFrom(entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Returns the instantiation strategy recognized by TinyDI for a particular class.
     * @param componentClass The class to look into
     * @return Any member of {@link ScopeDI} or null if the strategy cannot be determined.
     */
    public ScopeDI instantiationStrategy(Class<?> componentClass) {
        if (componentClass == null) return null;

        if (componentClass.getAnnotation(Registrar.class) != null) {
            return ScopeDI.SINGLETON;
        }
        Supervised supervised = componentClass.getAnnotation(Supervised.class);
        if (supervised != null) {
            return supervised.scope();
        }
        Recorded recorded = componentClass.getAnnotation(Recorded.class);
        if (recorded != null) {
            return recorded.scope();
        }

        return null;
    }

    /**
     * Returns the instantiation strategy used by TinyDI to create an instance a the particular object.
     * @param instance The object to check.
     * @return Any member of {@link ScopeDI} or null if the strategy cannot be determined.
     */
    public ScopeDI instantiationStrategy(Object instance) {
        if (instance == null) return null;

        if (instance instanceof Recorded) {
            return ((Recorded) instance).scope();
        }
        if (instance instanceof Supervised) {
            return ((Supervised) instance).scope();
        }
        if (instance instanceof Registrar) {
            return ScopeDI.SINGLETON;
        }

        ScopeDI result = TinyDynamicDI.scopeOfProxy(instance);
        if (result == null) {
            result = this.instantiationStrategy(TinyDynamicDI.realClass(instance));
        }

        return result;
    }

    /**
     * Returns instance of an already registered component, taking into account its instantiation scope.
     * @param componentClass The class type of the component to search for and possibly instantiate
     * @return A nonnull instance if a class match's been found, otherwise null.
     */
    public Object componentFor(Class<?> componentClass) {
        if (componentClass.isPrimitive()) {
            componentClass = MethodType.methodType(componentClass).wrap().returnType();
        }

        if (!componentClass.isInterface() && !Modifier.isAbstract(componentClass.getModifiers())) {

            for (Map.Entry<String, Class<?>> entry : this.registry.entrySet()) {
                if (entry.getValue() == componentClass) {
                    Object instance = this.instances.get(entry.getKey());
                    if (instance == null) {
                        instance = nestedSupplierResolver(this.proxyInstances.get(entry.getKey()));
                    }
                    return instance;
                }
            }
        } else {

            for (Map.Entry<String, Class<?>> entry : this.registry.entrySet()) {
                if (componentClass.isAssignableFrom(entry.getValue())) {
                    Object instance = this.instances.get(entry.getKey());
                    if (instance == null) {
                        instance = nestedSupplierResolver(this.proxyInstances.get(entry.getKey()));
                    }
                    return instance;
                }
            }
        }

        return null;
    }

    /**
     * Returns instance of an already registered component, taking into account its instantiation scope.
     * @param componentName The name of the component to search for and possibly instantiate
     * @return A nonnull instance if a name match's been found, otherwise null.
     */
    public Object componentFor(String componentName) {
        Object instance = this.instances.get(componentName);
        if (instance == null) {
            instance = nestedSupplierResolver(this.proxyInstances.get(componentName));
        }
        return instance;
    }
}
