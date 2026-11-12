import java.util.Objects;

public class SqlParseException extends Exception {

    private final String rawMessage;
    private final String query;
    private final int position;

    public SqlParseException(String message) {
        this(message, null, -1, null);
    }

    public SqlParseException(String message, Throwable cause) {
        this(message, null, -1, cause);
    }

    public SqlParseException(String message, String query, int position) {
        this(message, query, position, null);
    }

    public SqlParseException(String message, String query, int position, Throwable cause) {
        super(build(message, query, position), cause);
        this.rawMessage = message == null ? "Ошибка разбора запроса" : message;
        this.query = query;
        this.position = (query == null || position < 0) ? -1
                : Math.min(position, Math.max(0, query.length() - 1));
    }

    public String getRawMessage() { return rawMessage; }
    public String getQuery()      { return query; }
    public int getPosition()      { return position; }
    public boolean hasPosition()  { return position >= 0 && query != null; }

    private static String build(String message, String query, int position) {
        String base = (message == null || message.isEmpty()) ? "Ошибка разбора запроса" : message;
        if (query == null || query.isEmpty()) return base;

        int pos = Math.max(0, Math.min(position, query.length() - 1));
        int left = Math.max(0, pos - 25);
        int right = Math.min(query.length(), pos + 25);
        String prefix = left > 0 ? "…" : "";
        String suffix = right < query.length() ? "…" : "";
        String fragment = prefix + query.substring(left, right) + suffix;

        int caretCol = prefix.length() + (pos - left);
        return base + System.lineSeparator()
                + "  Запрос: " + fragment + System.lineSeparator()
                + "  " + " ".repeat(caretCol) + "^ позиция " + pos;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof SqlParseException)) return false;
        SqlParseException e = (SqlParseException) o;
        return position == e.position && rawMessage.equals(e.rawMessage)
                && Objects.equals(query, e.query);
    }
    @Override public int hashCode() { return Objects.hash(rawMessage, query, position); }
    @Override public String toString() { return "SqlParseException: " + getMessage(); }
}
