package me.egg82.tfaplus.enums;

public enum SQLType {
    MySQL("mysql"),
    SQLite("sqlite");

    private final String name;
    SQLType(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    public static SQLType getByName(String name) {
        for (SQLType value : values()) {
            if (value.name.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
