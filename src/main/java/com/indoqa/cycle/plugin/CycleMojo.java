/*
 * Licensed to the Indoqa Software Design und Beratung GmbH (Indoqa) under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Indoqa licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.indoqa.cycle.plugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "analyze", defaultPhase = LifecyclePhase.TEST)
public class CycleMojo extends AbstractMojo {

    private static final String SYSTEM_PROPERTY_SKIP = "skipCycles";

    private static final String[] RELEVANT_PACKAGINGS = {"jar", "war", "bundle"};

    @Parameter(property = "project.build.outputDirectory")
    private File classesDirectory;

    @Parameter(property = "project.build.directory")
    private File targetDirectory;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(property = "excludedPackages")
    private String[] excludedPackages;

    @Parameter(defaultValue = "false", property = "skip", required = false)
    private boolean skip;

    private static boolean isRedundant(Cycle cycle, List<Cycle> otherCycles) {
        for (Cycle eachOtherCycle : otherCycles) {
            if (cycle.contains(eachOtherCycle)) {
                return true;
            }
        }

        return false;
    }

    private static List<Cycle> removeRedundantCycles(List<Cycle> cycles) {
        Collections.sort(cycles, Cycle.LONGEST_FIRST);
        List<Cycle> result = new ArrayList<>();

        for (int i = 0; i < cycles.size(); i++) {
            Cycle cycle = cycles.get(i);
            if (isRedundant(cycle, cycles.subList(i + 1, cycles.size()))) {
                continue;
            }
            result.add(cycle);
        }

        return result;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.skip || System.getProperties().containsKey(SYSTEM_PROPERTY_SKIP)) {
            this.getLog().info("Package cycle test is skipped.");
            return;
        }

        if (!this.shouldBeAnalyzed()) {
            this.getLog().info("Ignoring project with packaging '" + this.mavenProject.getPackaging() + "'");
            return;
        }

        this.getLog().info("Analyzing class files in '" + this.classesDirectory.getAbsolutePath() + "'.");

        if (!this.classesDirectory.exists()) {
            this.getLog().warn("Directory does not exist!");
            return;
        }

        List<Cycle> cycles = this.getPackageCycles();
        if (cycles.isEmpty()) {
            return;
        }

        throw new MojoExecutionException(this.getFormattedCycles(cycles));
    }

    private Path getCycleFile() {
        return this.targetDirectory.toPath().resolve("cycles.txt");
    }

    private String getFormattedCycles(List<Cycle> cycles) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Cycle eachCycle : cycles) {
            stringBuilder.append("\n");

            stringBuilder.append("Detected package cycle:\n");
            for (String eachInvolvedPackage : eachCycle.getInvolvedPackages()) {
                stringBuilder.append("    ");
                stringBuilder.append(eachInvolvedPackage);
                stringBuilder.append("\n");
            }
        }
        stringBuilder.append("\n");
        stringBuilder.append("See ");
        stringBuilder.append(this.getCycleFile().toAbsolutePath());
        stringBuilder.append(" for involved classes.");

        return stringBuilder.toString();
    }

    private List<Cycle> getPackageCycles() throws MojoExecutionException {
        try {
            CycleDetector cycleDetector = new CycleDetector(this.classesDirectory, this.excludedPackages);

            List<Cycle> packageCycles = cycleDetector.getPackageCycles();
            packageCycles = removeRedundantCycles(packageCycles);
            if (!packageCycles.isEmpty()) {
                cycleDetector.writeCycleFile(packageCycles, this.getCycleFile());
            }

            return packageCycles;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to detect package cycles!", e);
        }
    }

    private boolean shouldBeAnalyzed() {
        for (String eachRelevantPackaging : RELEVANT_PACKAGINGS) {
            if (this.mavenProject.getPackaging().equals(eachRelevantPackaging)) {
                return true;
            }
        }

        return false;
    }
}
