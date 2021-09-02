package org.jrd.backend.core;

import java.util.regex.Pattern;

public class ClassInfo {
    private String name;
    private String location;

    private static final Pattern INFO_DELIMITER_PATTERN = Pattern.compile("\\|");

    public ClassInfo(String classString) {
        String[] splitClassString = INFO_DELIMITER_PATTERN.split(classString);
        this.name = splitClassString[0];

        if (splitClassString.length >= 2) {
            this.location = splitClassString[1];
        } else { // backwards compatibility
            this.location = "unknown";
        }
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getSearchableString(boolean isLocationVisible) {
        return isLocationVisible ? (name + location) : name;
    }

    @Override
    public String toString() {
        return "Name: " + name + ", Location: " + location;
    }
}
