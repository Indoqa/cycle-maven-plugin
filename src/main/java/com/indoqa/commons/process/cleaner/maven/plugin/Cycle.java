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
package com.indoqa.commons.process.cleaner.maven.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jdepend.framework.JavaPackage;

public class Cycle {

    private final Set<String> involvedPackages = new TreeSet<String>();

    public static Cycle fromPackage(JavaPackage javaPackage) {
        Cycle cycle = new Cycle();

        List<JavaPackage> involvedPackages = new ArrayList<JavaPackage>();
        javaPackage.collectCycle(involvedPackages);

        for (JavaPackage eachInvolvedPackage : involvedPackages) {
            cycle.addInvolvedPackage(eachInvolvedPackage.getName());
        }

        return cycle;
    }

    public void addInvolvedPackage(String involvedPackage) {
        this.involvedPackages.add(involvedPackage);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Cycle)) {
            return false;
        }

        Cycle other = (Cycle) obj;
        return this.involvedPackages.equals(other.involvedPackages);
    }

    public Set<String> getInvolvedPackages() {
        return this.involvedPackages;
    }

    @Override
    public int hashCode() {
        return this.involvedPackages.hashCode();
    }
}
