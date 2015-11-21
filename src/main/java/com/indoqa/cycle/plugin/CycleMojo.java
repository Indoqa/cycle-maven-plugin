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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import jdepend.framework.JDepend;
import jdepend.framework.JavaPackage;
import jdepend.framework.PackageFilter;

/**
 * @goal analyze
 * @phase test
 */
public class CycleMojo extends AbstractMojo {

    private static final String[] RELEVANT_PACKAGINGS = {"jar", "war"};

    /**
     * @parameter property="project.build.outputDirectory"
     */
    private File classesDirectory;

    /**
     * @parameter property="project"
     */
    private MavenProject mavenProject;

    /**
     * @parameter property="excludedPackages"
     */
    private String[] excludedPackages;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!this.shouldBeAnalyzed()) {
            this.getLog().info("Ignoring project with packaging '" + this.mavenProject.getPackaging() + "'");
            return;
        }

        this.getLog().info("Analyzing class files in " + this.classesDirectory.getAbsolutePath());

        if (!this.classesDirectory.exists()) {
            this.getLog().warn("Directory does not exist!");
            return;
        }

        try {
            Set<Cycle> cycles = this.getPackageCycles();
            if (cycles.isEmpty()) {
                return;
            }

            throw new MojoExecutionException(this.getFormattedCycles(cycles));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to detect package cycles", e);
        }
    }

    private String getFormattedCycles(Set<Cycle> cycles) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Cycle eachCycle : cycles) {
            stringBuilder.append("\n");

            stringBuilder.append("Detected cycle between packages:\n");
            for (String eachInvolvedPackage : eachCycle.getInvolvedPackages()) {
                stringBuilder.append("    ");
                stringBuilder.append(eachInvolvedPackage);
                stringBuilder.append("\n");
            }
        }

        return stringBuilder.toString();
    }

    @SuppressWarnings("unchecked")
    private Set<Cycle> getPackageCycles() throws IOException {
        Set<Cycle> result = new HashSet<Cycle>();

        JDepend depend = new JDepend();
        depend.addDirectory(this.classesDirectory.getAbsolutePath());
        depend.analyzeInnerClasses(true);

        if (this.excludedPackages != null) {
            depend.setFilter(new PackageFilter(Arrays.asList(this.excludedPackages)));
        }

        depend.analyze();

        Collection<JavaPackage> packages = depend.getPackages();
        for (JavaPackage eachPackage : packages) {
            if (!eachPackage.containsCycle()) {
                continue;
            }

            result.add(Cycle.fromPackage(eachPackage));
        }

        return result;
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
