package com.github.zhgzhg.tinydi.build.android;

import com.android.build.gradle.tasks.MergeSourceSetFolders;
import com.github.zhgzhg.tinydi.build.BuildTimeScan;
import com.github.zhgzhg.tinydi.meta.MetaBaseTinyDI;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

/**
 * Helper action executing the actual classpath scanning, JSON file as an asset writing, and possibly cleaning it afterwards.
 */
@RequiredArgsConstructor
public class WriteTemporaryScanAsset implements Action<Task> {

    private final MergeSourceSetFolders mergeSourceSetFolders;
    private final List<String> scanArgs;
    private final boolean cleanProducedAssets;
    private final boolean removeClassPathDataFromJSON;
    private final Task latePostexecTaskToHookAfter;

    public static String computeScanFileName(List<String> args) {
        String outputFile = "tinydi-scanresult.json";
        if (args == null || args.isEmpty()) {
            return outputFile;
        }

        for (ListIterator<String> it = args.listIterator(); it.hasPrevious(); ) {
            String arg = it.previous();
            if (arg.startsWith("-of")) {
                outputFile = arg.substring(3);
                break;
            }
        }

        return outputFile;
    }

    private File extractLowestExistingDirectory(File file) {
        while (file != null && !file.exists() && !file.isDirectory()) {
            file = file.getParentFile();
        }
        return file;
    }

    private File extractFirstDirChild(File base, File subdir) {
        if (base == null) return subdir;

        File prevDir = subdir;
        while (!Objects.equals(base, subdir)) {
            prevDir = subdir;
            subdir = subdir.getParentFile();
            if (subdir == null) {
                return null;
            }
        }
        return prevDir;
    }

    @SneakyThrows
    private void removeClassPathDataFromJSONFile(File jsonFile) {
        if (!jsonFile.exists()) return;

        JsonSlurper slurper = new JsonSlurper();

        Map<String, Object> parsed = (Map<String, Object>) slurper.parse(jsonFile, StandardCharsets.UTF_8.name());
        parsed.remove("classpath");
        parsed.put("classpath", Collections.emptyList());

        Map<String, Object> scanSpec = (Map<String, Object>) parsed.get("scanSpec");
        if (scanSpec != null) {
            scanSpec.remove("overrideClasspath");
            scanSpec.put("overrideClasspath", Collections.emptyList());
        }

        String jsonResult = JsonOutput.toJson(parsed);
        try (PrintWriter pr = new PrintWriter(jsonFile, StandardCharsets.UTF_8)) {
            pr.println(jsonResult);
        }
    }

    @Override
    public void execute(Task task) {
        System.out.println("Generating static classpath scan as a temporary JSON file asset...");

        File assetDirectory = mergeSourceSetFolders.getSourceFolderInputs().getFiles().iterator().next();
        File staticScanLocation = new File(assetDirectory, computeScanFileName(this.scanArgs));

        File realBase = extractLowestExistingDirectory(assetDirectory);
        File forDeletingDirectory = extractFirstDirChild(realBase, assetDirectory);

        if (cleanProducedAssets) {
            if (forDeletingDirectory != null && !forDeletingDirectory.exists()) {
                latePostexecTaskToHookAfter.doLast(s -> {
                    System.out.println("Removing TinyDI's temp assets...");
                    staticScanLocation.delete();
                    forDeletingDirectory.delete();
                });
            } else {
                latePostexecTaskToHookAfter.doLast(s -> {
                    System.out.println("Removing TinyDI's temp assets...");
                    staticScanLocation.delete();
                });
            }
        }
        assetDirectory.mkdirs();

        List<String> cliArgs = new LinkedList<>();
        cliArgs.add("-od" + assetDirectory.getAbsolutePath());
        cliArgs.add("-oc" + ((JavaCompile) task).getDestinationDirectory().get().getAsFile().getAbsolutePath());
        cliArgs.add("-oc" + BuildTimeScan.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        cliArgs.add("-bp" + MetaBaseTinyDI.class.getPackageName());
        cliArgs.addAll(this.scanArgs);

        BuildTimeScan.main(cliArgs.toArray(new String[0]));

        if (removeClassPathDataFromJSON) {
            this.removeClassPathDataFromJSONFile(staticScanLocation);
        }
    }
}
