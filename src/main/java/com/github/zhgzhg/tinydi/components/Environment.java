package com.github.zhgzhg.tinydi.components;

import com.github.zhgzhg.tinydi.meta.annotations.Supervised;
import lombok.Getter;

import java.util.Map;
import java.util.Properties;

/**
 * Holder of environment variables and system properties, which can be injected as a dependency.
 */
@Supervised
@Getter
public final class Environment {

    private final String[] args;
    private final Map<String, String> environmentVars;
    private final Properties environmentProps;

    public Environment() {
        this(new String[0]);
    }

    public Environment(String[] args) {
        this(args, System.getenv(), System.getProperties());
    }

    public Environment(String[] args, Map<String, String> environmentVars, Properties envProps) {
        this.args = args;
        this.environmentVars = environmentVars;
        this.environmentProps = envProps;
    }
}
