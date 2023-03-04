open module com.github.zhgzhg.tinydi.tinydi {
    exports com.github.zhgzhg.tinydi;
    exports com.github.zhgzhg.tinydi.build;
    exports com.github.zhgzhg.tinydi.components;
    exports com.github.zhgzhg.tinydi.dynamic;
    exports com.github.zhgzhg.tinydi.meta;
    exports com.github.zhgzhg.tinydi.meta.annotations;
    exports com.github.zhgzhg.tinydi.meta.enums;

    requires java.base;
    requires static lombok;
    requires io.github.classgraph;
}