package org.jrd.backend.core;

import java.util.Objects;
import java.util.regex.Pattern;

public class ClassInfo {
    private String name;
    private String location;
    private String classLoader;

    private static final Pattern INFO_DELIMITER_PATTERN = Pattern.compile("\\|");

    public ClassInfo(String classString) {
        String[] splitClassString = INFO_DELIMITER_PATTERN.split(classString);
        this.name = splitClassString[0];

        if (splitClassString.length >= 2) {
            this.location = splitClassString[1];
        } else { // backwards compatibility
            this.location = "unknown";
        }

        if (splitClassString.length >= 3) {
            this.classLoader = splitClassString[2];
        } else { // backwards compatibility
            this.classLoader = "unknown";
        }
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getClassLoader() {
        return classLoader;
    }

    public String getSearchableString(boolean isLocationVisible) {
        return isLocationVisible ? (name + location + classLoader) : name;
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Location: " + location + ", Classloader: " + classLoader;
    }

    // only the name is used to keep track of a given class between class loads, because the additional info changes
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClassInfo classInfo = (ClassInfo) o;
        return name.equals(classInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
