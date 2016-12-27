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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Cycle {

    public static final Comparator<? super Cycle> LONGEST_FIRST = new Comparator<Cycle>() {

        @Override
        public int compare(Cycle c1, Cycle c2) {
            if (c1.getLength() > c2.getLength()) {
                return -1;
            }

            if (c1.getLength() < c2.getLength()) {
                return 1;
            }

            return 0;
        }
    };

    private final List<String> involvedPackages = new ArrayList<String>();

    public void addInvolvedPackage(String involvedPackage) {
        this.involvedPackages.add(involvedPackage);
    }

    public boolean contains(Cycle other) {
        return this.involvedPackages.containsAll(other.involvedPackages);
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

    public List<String> getInvolvedPackages() {
        return this.involvedPackages;
    }

    public int getLength() {
        return this.involvedPackages.size();
    }

    @Override
    public int hashCode() {
        return this.involvedPackages.hashCode();
    }

    @Override
    public String toString() {
        return this.involvedPackages.toString();
    }
}
