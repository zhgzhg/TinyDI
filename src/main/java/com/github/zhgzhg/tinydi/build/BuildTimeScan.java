package com.github.zhgzhg.tinydi.build;

import com.github.zhgzhg.tinydi.TinyDI;
import lombok.SneakyThrows;

import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * CLI utility which may be executed during build time to produce JSON file with classpath scan information.
 * Useful for platforms with limited reflection capabilities (Android, GraalVM native images, etc.).
 * To see the supported parameters execute it without any or only with '-h' parameter.
 */
public class BuildTimeScan implements Consumer<String[]> {

    static String OUT_FILE = "-of";
    static String OUT_DIR = "-od";
    static String BASE_PKG = "-bp";
    static String BASE_PKG_IGNORED = "-ibp";
    static String CLASS_IGNORED = "-ic";

    String outFile = "tinydi-scanresult.json";
    String outDir;
    Set<String> basePackages = new LinkedHashSet<>();
    Set<String> ignoredBasePackages = new LinkedHashSet<>();
    Set<String> ignoredClasses = new LinkedHashSet<>();

    private static void printHelp() {
        System.out.println("Build Time Scanner - a DI helper utility");
        System.out.println();
        System.out.println("Format:");
        System.out.println("  -od<output_directory> -bp<fqdn_base_package>");
        System.out.println("  [-ibp<fqdn_base_package_to_ignore>] [-ic<fqdn_class_to_ignore>]");
        System.out.println("  [-of<output_file_name>]");
        System.out.println();
        System.out.println("The parameter values must follow without spaces. Any parameter may be repeated more than once.");
        System.out.println("Only the last value of parameters -of and -od will be respected.");
    }

    private void parseArgs(String[] args) {
        if (args.length < 2 || args[0].startsWith("-h")) {
            printHelp();
        }

        for (String arg : args) {
            if (arg.startsWith(OUT_FILE)) {
                outFile = arg.substring(OUT_FILE.length());
            } else if (arg.startsWith(OUT_DIR)) {
                outDir = arg.substring(OUT_DIR.length());
            } else if (arg.startsWith(BASE_PKG)) {
                basePackages.add(arg.substring(BASE_PKG.length()));
            } else if (arg.startsWith(BASE_PKG_IGNORED)) {
                ignoredBasePackages.add(arg.substring(BASE_PKG_IGNORED.length()));
            } else if (arg.startsWith(CLASS_IGNORED)) {
                ignoredClasses.add(arg.substring(CLASS_IGNORED.length()));
            }
        }

        if ((outDir == null || outDir.isBlank()) && (basePackages.isEmpty())) {
            throw new RuntimeException("Output directory and base package name is needed at least");
        }
    }

    @SneakyThrows
    @Override
    public void accept(String[] args) {

        this.parseArgs(args);

        File jsonFile = new File(this.outDir, this.outFile);

        String json = TinyDI.config()
                .basePackages(this.basePackages.toArray(new String[0]))
                .ignoredBasePackages(this.ignoredBasePackages.toArray(new String[0]))
                .ignoredClasses(this.ignoredClasses.toArray(new String[0]))
                .configureForStaticScan();

        try (PrintWriter writer = new PrintWriter(jsonFile)) {
            writer.print(json);
        }
    }

    public static void main(String[] args) {
        new BuildTimeScan().accept(args);
    }
}
