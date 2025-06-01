package edu.progdist.connection;

public record Message(String type, String payload) {
    public Message(String raw) {
        this(getTypeFromRaw(raw), getPayloadFromRaw(raw));
    }

    public static String encode(String type, String payload) {
        return type + "|" + payload;
    }

    private static String getTypeFromRaw(String raw) {
        String[] parts = raw.split("\\|", 2);
        return parts.length > 0 ? parts[0] : "";
    }

    private static String getPayloadFromRaw(String raw) {
        String[] parts = raw.split("\\|", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    @Override
    public String toString() {
        return encode(type, payload);
    }
}
