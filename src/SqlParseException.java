import java.util.Objects;

/**
 SqlParseException хранит: краткое описание (rawMessage), исходный запрос (query), позицию (position).
 getMessage() собирает из них многострочное сообщение; getRawMessage() — «чистый» текст.
 Длинные запросы обрезаются до окна ±25 символов вокруг позиции, чтобы сообщения оставались читаемыми.
 */
public class SqlParseException extends Exception {

    private static final long serialVersionUID = 1L;

    //Сколько символов слева/справа от позиции показывать в фрагменте.
    private static final int FRAGMENT_PADDING = 25;

    private final String rawMessage;
    private final String query;
    private final int position;

    //Создаёт исключение без контекста (позиция неизвестна).
    public SqlParseException(String message) {
        this(message, null, -1, null);
    }

    //Создаёт исключение с причиной, без контекста запроса.
    public SqlParseException(String message, Throwable cause) {
        this(message, null, -1, cause);
    }

    /**
     * Создаёт исключение с указанием исходного запроса и позиции.
     *
     * @param message  краткое описание ошибки
     * @param query    исходный запрос (может быть {@code null})
     * @param position позиция 0-based; {@code -1} если неизвестна
     */
    public SqlParseException(String message, String query, int position) {
        this(message, query, position, null);
    }

    /**
     * Полный конструктор.
     *
     * @param message  краткое описание ошибки
     * @param query    исходный запрос (может быть {@code null})
     * @param position позиция 0-based; {@code -1} если неизвестна
     * @param cause    причина (может быть {@code null})
     */
    public SqlParseException(String message, String query, int position, Throwable cause) {
        super(buildFullMessage(message, query, position), cause);
        this.rawMessage = message == null ? "Ошибка разбора запроса" : message;
        this.query = query;
        // Позиция либо -1, либо валидный индекс в query (если query задан)
        if (position < -1) {
            this.position = -1;
        } else if (query != null && position >= query.length()) {
            this.position = query.length() == 0 ? -1 : query.length() - 1;
        } else if (query == null && position >= 0) {
            this.position = -1;
        } else {
            this.position = position;
        }
    }

    // ================== ДОСТУП ==================

    //Краткое описание ошибки без фрагмента и позиции.
    public String getRawMessage() {
        return rawMessage;
    }

    //Исходный запрос или {@code null}.
    public String getQuery() {
        return query;
    }

    // Позиция 0-based или -1, если неизвестна.
    public int getPosition() {
        return position;
    }

    //Есть ли привязка к конкретной позиции запроса.
    public boolean hasPosition() {
        return position >= 0 && query != null;
    }

    // ================== ФОРМИРОВАНИЕ ==================

    //Строит итоговое сообщение: описание +  фрагмент запроса с кареткой.

    private static String buildFullMessage(String message, String query, int position) {
        String base = (message == null || message.isEmpty())
                ? "Ошибка разбора запроса"
                : message;

        if (query == null || query.isEmpty()) {
            return base;
        }

        // Обрезаем запрос до окна вокруг позиции
        int pos = clamp(position, 0, Math.max(0, query.length() - 1));
        int left  = Math.max(0, pos - FRAGMENT_PADDING);
        int right = Math.min(query.length(), pos + FRAGMENT_PADDING);

        String prefixEllipsis = left > 0 ? "…" : "";
        String suffixEllipsis = right < query.length() ? "…" : "";
        String fragment = prefixEllipsis + query.substring(left, right) + suffixEllipsis;

        // Однострочный вид (для логов)
        String oneLine = base
                + " (позиция " + pos + "): "
                + fragment;

        // Многострочный вид с кареткой под символом
        int caretCol = prefixEllipsis.length() + (pos - left);
        StringBuilder sb = new StringBuilder(base);
        sb.append(System.lineSeparator())
                .append("  Запрос: ").append(fragment)
                .append(System.lineSeparator())
                .append("  ");
        for (int i = 0; i < caretCol; i++) sb.append(' ');
        sb.append("^ позиция ").append(pos);

        // Возвращаем многострочный вид — он информативнее
        return sb.toString();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SqlParseException)) return false;
        SqlParseException other = (SqlParseException) o;
        return position == other.position
                && rawMessage.equals(other.rawMessage)
                && Objects.equals(query, other.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawMessage, query, position);
    }

    @Override
    public String toString() {
        return "SqlParseException: " + getMessage();
    }
}
