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

    /** Default constructor initializing the Environment with none CLI args */
    public Environment() {
        this(new String[0]);
    }

    /**
     * Constructor initializing the Environment with customised CLI args,
     * and the currently detected envrironment variables, and Java properties.
     * @param args The CLI args (typically) passed to the Java application.
     */
    public Environment(String[] args) {
        this(args, System.getenv(), System.getProperties());
    }

    /**
     * Constructor initializing the Environment with customised CLI args, envrironment variables and Java properties.
     * @param args The CLI args (typically) passed to the Java application.
     * @param environmentVars The environment variables (typically) available on the system.
     * @param envProps The Java environment properties that (typically) have been currently set.
     */
    public Environment(String[] args, Map<String, String> environmentVars, Properties envProps) {
        this.args = args;
        this.environmentVars = environmentVars;
        this.environmentProps = envProps;
    }
}
