import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 Простой парсер SQL-подобного языка.
 Поддерживает INSERT, SELECT, UPDATE, DELETE, CREATE TABLE, DROP TABLE, CREATE INDEX.
 Все классы запросов — вложенные static-классы, чтобы держать всё в одном файле.

 */
/*
Основная идея- превратить запрос в типизированный объект
keywordIndex находит позицию ключевого слова, substring вырезает кусок между двумя ключевыми словами;
дальше каждый кусок разбирается своим методом
 */
public class SqlParser {

    private static final int MAX_IDENT = 128; //максимальная длина идентификатора, используется в requireIdentifier
    //Базовый абстрактный класс - основа всех запросов
    public abstract static class Query {
        public enum Type { INSERT, SELECT, UPDATE, DELETE, CREATE_TABLE, DROP_TABLE, CREATE_INDEX } //перечисление оббрабатываемых типов
        private final Type type; //хранит тип запроса, во избежание его изменения
        protected Query(Type type) { this.type = type; } //каждый объект будет иметь корректный тип
        public Type getType() { return type; } //это база
    }
    //Классы запросов переносят данные в исполнитель
    //InsertQuery=================================================================================================================================================
    //InsertQuery хранит три public final поля и в конструкторе передаёт Type.INSERT в super
    public static class InsertQuery extends Query {
        public final String table; //искомая таблица
        public final List<String> columns;
        public final List<List<Value>> rows;
        public InsertQuery(String table, List<String> columns, List<List<Value>> rows) {
            super(Type.INSERT); //вызов конструктора базового класса
            this.table = table;
            this.columns = columns;
            this.rows = rows;
        }
    }
    //SelectQuery===============================================================
    //where - для индексирования
    public static class SelectQuery extends Query {
        public final List<String> columns;
        public final String table;
        public final Condition where;
        public SelectQuery(List<String> columns, String table, Condition where) {
            super(Type.SELECT);
            this.columns = columns;
            this.table = table;
            this.where = where;
        }
    }
    //UpdateQuery===============================================================
    //класс описывает что изменить и где изменить, для этого Map и Condition
    public static class UpdateQuery extends Query {
        public final String table;
        public final Map<String, Value> assignments;
        public final Condition where;
        public UpdateQuery(String table, Map<String, Value> assignments, Condition where) {
            super(Type.UPDATE);
            this.table = table;
            this.assignments = assignments;
            this.where = where;
        }
    }
    //DeleteQuery===========================================================
    public static class DeleteQuery extends Query {
        public final String table;
        public final Condition where;
        public DeleteQuery(String table, Condition where) {
            super(Type.DELETE);
            this.table = table;
            this.where = where; }
    }
    //CreateTableQuery===========================================================
    public static class CreateTableQuery extends Query {
        public final String table;
        public final List<ColDefinition> columns;
        public CreateTableQuery(String table, List<ColDefinition> columns) {
            super(Type.CREATE_TABLE);
            this.table = table;
            this.columns = columns;
        }
    }
    //DropTableQuery=============================================================
    public static class DropTableQuery extends Query {
        public final String table;
        public DropTableQuery(String table) {
            super(Type.DROP_TABLE);
            this.table = table; }
    }
//==================================================================================
    public static class CreateIndexQuery extends Query {
        public final String indexName;
        public final String table;
        public final String column;
        public CreateIndexQuery(String indexName, String table, String column) {
            super(Type.CREATE_INDEX);
            this.indexName = indexName;
            this.table = table;
            this.column = column;
        }
    }

    //Типы===============================================================================
    //перечисление поддерживаемых типов данных в столбцах +
    // метод для превращения строки из
    // SQL-запроса в элемент этого перечисления.
    public enum ColType {
        INTEGER, DOUBLE, STRING, DATE, BOOLEAN;

        public static ColType fromString(String s) throws SqlParseException {
            switch (s.trim().toUpperCase()) {
                case "INT": case "INTEGER": return INTEGER;
                case "DOUBLE": case "FLOAT": return DOUBLE;
                case "STRING": case "TEXT": return STRING;
                case "DATE": return DATE;
                case "BOOLEAN": case "BOOL": return BOOLEAN;
                default: throw new SqlParseException("Неизвестный тип столбца: " + s);
            }
        }
    }
    //собираем три свойства в один ColDefinition - для удобства
    public static class ColDefinition {
        public final String name;
        public final ColType type;
        public final boolean primaryKey;
        public ColDefinition(String name, ColType type, boolean primarykey) {
            this.name = name;
            this.type = type;
            this.primaryKey = primarykey;
        }
    }
    //будем представлять литералы как единые типизированные объекты,
    //исполнителю предоставим поля
    public static class Value {

        public final Object obj;
        public final ColType type;
        public final boolean isNull;

        //Специальный NULL, а не строка, чтобы не было путаницы
        public static final Value NULL = new Value(null, null, true);

        private Value(Object obj, ColType type) { this(obj, type, false); } //конструктор для внутренних вызовов
        private Value(Object obj, ColType type, boolean isNull) {
            this.obj = obj;
            this.type = type;
            this.isNull = isNull;
        }
        //основной метод превращает строковый литерал из SQL в типизированный Value
        public static Value parse(String token) throws SqlParseException {
            String t = token.trim();
            if (t.isEmpty()) throw new SqlParseException("Пустой литерал"); //если распознать не удалось, вызов parse прямо из parseInsert/parseUpdate/parseCondition
            //Строки и даты в кавычках
            if (t.length() >= 2
                    && ((t.charAt(0) == '\'' && t.charAt(t.length() - 1) == '\'')
                    || (t.charAt(0) == '"'  && t.charAt(t.length() - 1) == '"'))) {
                char q = t.charAt(0); //для кавычек
                String inner = unescape(t.substring(1, t.length() - 1), q);
                if (inner.length() == 10 && inner.charAt(4) == '-' && inner.charAt(7) == '-') {
                    try { return new Value(LocalDate.parse(inner), ColType.DATE); }
                    catch (DateTimeParseException ignored) { } //если дата невалидна
                }
                return new Value(inner, ColType.STRING);
            }
            //Логические и ноль
            String l = t.toUpperCase();
            if ("TRUE".equals(l))  return new Value(Boolean.TRUE,  ColType.BOOLEAN);
            if ("FALSE".equals(l)) return new Value(Boolean.FALSE, ColType.BOOLEAN);
            if ("NULL".equals(l))  return NULL;
            //Числа
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E"))
                    return new Value(Double.parseDouble(t), ColType.DOUBLE);
                return new Value(Long.parseLong(t), ColType.INTEGER);
            } catch (NumberFormatException ignored) { }

            throw new SqlParseException("Не удалось распознать значение: " + token);
        }
//Это SQL-стандарт для кавычек внутри строк. Без удвоения парсер
// не смог бы отличить «кавычку внутри текста» от «конца литерала».
        //метод разворачивает экранирование
        private static String unescape(String s, char q) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);

                if (c == q && i + 1 < s.length() && s.charAt(i + 1) == q) { sb.append(q); i++; } //удвоение кавычки
                else if (c == '\\' && i + 1 < s.length()
                        && (s.charAt(i + 1) == '\'' || s.charAt(i + 1) == '"' || s.charAt(i + 1) == '\\')) {
                    sb.append(s.charAt(i + 1)); i++;
                } else sb.append(c);
            }
            return sb.toString();
        }
        //обратно в sql
        @Override public String toString() {
            return isNull ? "NULL" : (obj instanceof String ? "'" + obj + "'" : String.valueOf(obj));
        }
    }
    //Condition — узел связного списка условий WHERE
    public static class Condition {
        public enum Operator {
            EQ("="), NEQ("<>"), GT(">"), LT("<"), GTE(">="), LTE("<=");
            public final String symbol;
            Operator(String s) { symbol = s; }
            public static Operator fromString(String s) throws SqlParseException {
                for (Operator o : values()) if (o.symbol.equals(s)) return o;
                if ("!=".equals(s)) return NEQ;
                throw new SqlParseException("Неизвестный оператор: " + s);
            }
        }
        public final String column;
        public final Operator operator;
        public final Value value;
        public final boolean andNext;
        public final Condition next;
        public Condition(String column, Operator operator, Value value, boolean andNext, Condition next) {
            this.column = column;
            this.operator = operator;
            this.value = value;
            this.andNext = andNext;
            this.next = next;
        }
    }

    // ================== ПАРСИНГ ==================
//единственная публичная точка входа в парсер:
    public Query parse(String sql) throws SqlParseException {
        if (sql == null || sql.trim().isEmpty()) throw new SqlParseException("Пустой запрос");
        String s = sql.trim();
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();

        //разбиение на слова
        String[] w = splitWords(s);
        if (w.length == 0) throw new SqlParseException("Пустой запрос");
        switch (w[0].toUpperCase()) {
            case "INSERT": return parseInsert(s);
            case "SELECT": return parseSelect(s);
            case "UPDATE": return parseUpdate(s);
            case "DELETE": return parseDelete(s);
            case "CREATE": return parseCreate(s);
            case "DROP":   return parseDrop(s);
            default: throw new SqlParseException("Неизвестная команда: " + w[0], s, 0);
        }
    }

    //INSERT======================================================================
    //parseInsert находит INTO и VALUES, режет строку по ним, каждую часть разбирает отдельно
    private InsertQuery parseInsert(String s) throws SqlParseException {
        int into = keywordIndex(s, "INTO", 0);
        if (into < 0) throw new SqlParseException("INSERT без INTO", s, 0);
        int values = keywordIndex(s, "VALUES", into + 4);
        if (values < 0) throw new SqlParseException("INSERT без VALUES", s, into);

        String header = s.substring(into + 4, values).trim();
        String table;
        List<String> columns = new ArrayList<>();
        int pOpen = header.indexOf('(');
        if (pOpen >= 0) {
            table = header.substring(0, pOpen).trim();
            requireIdentifier(table, "имя таблицы", s, into + 4);
            int pClose = matchParen(header, pOpen, s);
            columns = splitIdentifiers(header.substring(pOpen + 1, pClose), s);
        } else {
            table = header;
            requireIdentifier(table, "имя таблицы", s, into + 4);
        }

        String valuesPart = s.substring(values + "VALUES".length()).trim();
        List<List<Value>> rows = new ArrayList<>();
        int i = 0;
        while (i < valuesPart.length()) {
            char c = valuesPart.charAt(i);
            if (Character.isWhitespace(c) || c == ',') { i++; continue; }
            if (c != '(') throw new SqlParseException("Ожидалось '(' в VALUES", s, values + i);
            int close = matchParen(valuesPart, i, s);
            List<Value> row = new ArrayList<>();
            for (String v : splitTopLevel(valuesPart.substring(i + 1, close), ',', s)) {
                if (!v.trim().isEmpty()) row.add(Value.parse(v));
            }
            rows.add(row);
            i = close + 1;
        }
        if (rows.isEmpty()) throw new SqlParseException("INSERT без значений", s, values);
        if (!columns.isEmpty()) {
            for (List<Value> r : rows) {
                if (r.size() != columns.size())
                    throw new SqlParseException("Число значений не совпадает с числом столбцов", s, values);
            }
        }
        return new InsertQuery(table, columns, rows);
    }

    //SELECT===================================================
    private SelectQuery parseSelect(String s) throws SqlParseException {
        int from = keywordIndex(s, "FROM", "SELECT".length());
        if (from < 0) throw new SqlParseException("SELECT без FROM", s, 0);

        String colsPart = s.substring("SELECT".length(), from).trim();
        List<String> columns = colsPart.equals("*") ? new ArrayList<>()
                : splitIdentifiers(colsPart, s);

        int where = keywordIndex(s, "WHERE", from + 4);
        String table;
        Condition cond = null;
        if (where < 0) {
            table = s.substring(from + 4).trim();
        } else {
            table = s.substring(from + 4, where).trim();
            cond = parseCondition(s.substring(where + "WHERE".length()).trim(), s);
        }
        requireIdentifier(table, "имя таблицы", s, from + 4);
        return new SelectQuery(columns, table, cond);
    }

    // ---------- UPDATE ----------
    private UpdateQuery parseUpdate(String s) throws SqlParseException {
        int set = keywordIndex(s, "SET", "UPDATE".length());
        if (set < 0) throw new SqlParseException("UPDATE без SET", s, 0);
        String table = s.substring("UPDATE".length(), set).trim();
        requireIdentifier(table, "имя таблицы", s, "UPDATE".length());

        int where = keywordIndex(s, "WHERE", set + 3);
        String assignPart;
        Condition cond = null;
        if (where < 0) {
            assignPart = s.substring(set + 3).trim();
        } else {
            assignPart = s.substring(set + 3, where).trim();
            cond = parseCondition(s.substring(where + "WHERE".length()).trim(), s);
        }
        Map<String, Value> map = new LinkedHashMap<>();
        for (String a : splitTopLevel(assignPart, ',', s)) {
            a = a.trim();
            if (a.isEmpty()) continue;
            int eq = indexOfOperatorTopLevel(a);
            if (eq < 0) throw new SqlParseException("Ожидалось '=' в SET: " + a, s, set);
            String col = a.substring(0, eq).trim();
            String val = a.substring(eq + 1).trim();
            requireIdentifier(col, "имя столбца", s, set);
            if (val.isEmpty()) throw new SqlParseException("Пустое значение в SET: " + col, s, set);
            map.put(col, Value.parse(val));
        }
        if (map.isEmpty()) throw new SqlParseException("UPDATE без присваиваний", s, set);
        return new UpdateQuery(table, map, cond);
    }

    // ---------- DELETE ----------
    private DeleteQuery parseDelete(String s) throws SqlParseException {
        int from = keywordIndex(s, "FROM", "DELETE".length());
        if (from < 0) throw new SqlParseException("DELETE без FROM", s, 0);
        int where = keywordIndex(s, "WHERE", from + 4);
        String table;
        Condition cond = null;
        if (where < 0) {
            table = s.substring(from + 4).trim();
        } else {
            table = s.substring(from + 4, where).trim();
            cond = parseCondition(s.substring(where + "WHERE".length()).trim(), s);
        }
        requireIdentifier(table, "имя таблицы", s, from + 4);
        return new DeleteQuery(table, cond);
    }

    // ---------- CREATE ----------
    private Query parseCreate(String s) throws SqlParseException {
        String rest = s.substring("CREATE".length()).trim();
        if (keywordIndex(rest, "TABLE", 0) == 0)
            return parseCreateTable(rest.substring("TABLE".length()).trim(), s);
        if (keywordIndex(rest, "INDEX", 0) == 0)
            return parseCreateIndex(rest.substring("INDEX".length()).trim(), s);
        throw new SqlParseException("CREATE поддерживает только TABLE и INDEX", s, 0);
    }

    private CreateTableQuery parseCreateTable(String s, String context) throws SqlParseException {
        int pOpen = s.indexOf('(');
        if (pOpen < 0) throw new SqlParseException("CREATE TABLE без '('", context, 0);
        int pClose = matchParen(s, pOpen, context);
        String table = s.substring(0, pOpen).trim();
        requireIdentifier(table, "имя таблицы", context, 0);

        List<ColDefinition> cols = new ArrayList<>();
        for (String part : splitTopLevel(s.substring(pOpen + 1, pClose), ',', context)) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] w = splitWords(part);
            if (w.length < 2) throw new SqlParseException("Некорректный столбец: " + part, context, 0);
            requireIdentifier(w[0], "имя столбца", context, 0);
            boolean pk = false;
            for (int i = 2; i < w.length - 1; i++)
                if ("PRIMARY".equalsIgnoreCase(w[i]) && "KEY".equalsIgnoreCase(w[i + 1])) pk = true;
            cols.add(new ColDefinition(w[0], ColType.fromString(w[1]), pk));
        }
        if (cols.isEmpty()) throw new SqlParseException("CREATE TABLE без столбцов", context, 0);
        return new CreateTableQuery(table, cols);
    }

    private CreateIndexQuery parseCreateIndex(String s, String context) throws SqlParseException {
        int on = keywordIndex(s, "ON", 0);
        if (on < 0) throw new SqlParseException("CREATE INDEX без ON", context, 0);
        String indexName = s.substring(0, on).trim();
        requireIdentifier(indexName, "имя индекса", context, 0);
        String tail = s.substring(on + 2).trim();
        int pOpen = tail.indexOf('(');
        if (pOpen < 0) throw new SqlParseException("CREATE INDEX без '('", context, 0);
        int pClose = matchParen(tail, pOpen, context);
        String table = tail.substring(0, pOpen).trim();
        String col = tail.substring(pOpen + 1, pClose).trim();
        requireIdentifier(table, "имя таблицы", context, 0);
        requireIdentifier(col, "имя столбца", context, 0);
        return new CreateIndexQuery(indexName, table, col);
    }

    // ---------- DROP ----------
    private DropTableQuery parseDrop(String s) throws SqlParseException {
        String rest = s.substring("DROP".length()).trim();
        if (keywordIndex(rest, "TABLE", 0) != 0)
            throw new SqlParseException("DROP поддерживает только TABLE", s, 0);
        String table = rest.substring("TABLE".length()).trim();
        requireIdentifier(table, "имя таблицы", s, 0);
        return new DropTableQuery(table);
    }

    // ---------- WHERE ----------
    private Condition parseCondition(String s, String context) throws SqlParseException {
        List<String> parts = new ArrayList<>();
        List<Boolean> andFlags = new ArrayList<>();
        int start = 0, i = 0;
        while (i < s.length()) {
            int q = skipQuotedStrict(s, i, context);
            if (q != i) { i = q; continue; }

            int and = keywordIndex(s, "AND", i);
            int or  = keywordIndex(s, "OR", i);
            int next = -1;
            boolean isAnd = true;
            if (and >= 0 && (or < 0 || and < or)) { next = and; isAnd = true; }
            else if (or >= 0) { next = or; isAnd = false; }
            if (next < 0) break;
            parts.add(s.substring(start, next).trim());
            andFlags.add(isAnd);
            start = next + (isAnd ? 3 : 2);
            i = start;
        }
        parts.add(s.substring(start).trim());

        Condition next = null;
        for (int k = parts.size() - 1; k >= 0; k--) {
            boolean andNext = k < andFlags.size() ? andFlags.get(k) : true;
            next = parseSingleCondition(parts.get(k), andNext, next, context);
        }
        return next;
    }

    //Метод разбирает одну строку-условие вида age > 25 или name = 'Ann' в готовый объект Condition
    private Condition parseSingleCondition(String s, boolean andNext, Condition next, String context)
            throws SqlParseException {
        int idx = -1;
        String op = null;
        String[] ops = {">=", "<=", "<>", "!=", "=", ">", "<"};
        for (int i = 0; i < s.length(); i++) {
            int q = skipQuotedStrict(s, i, context);
            if (q != i) { i = q - 1; continue; }
            for (String o : ops) if (s.startsWith(o, i)) { idx = i; op = o; break; }
            if (idx >= 0) break;
        }
        if (idx < 0) throw new SqlParseException("Некорректное условие: " + s, context, 0);
        String col = s.substring(0, idx).trim();
        String val = s.substring(idx + op.length()).trim();
        requireIdentifier(col, "имя столбца в условии", context, 0);
        if (val.isEmpty()) throw new SqlParseException("Пустое значение в условии: " + s, context, 0);
        return new Condition(col, Condition.Operator.fromString(op), Value.parse(val), andNext, next);
    }

    //Метод ищет позицию символа =, который находится на верхнем уровне
    // вложенности — вне скобок и вне строковых литералов. Если такого нет, возвращает -1
    private static int indexOfOperatorTopLevel(String s) throws SqlParseException {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            int q = skipQuotedStrict(s, i, s);
            if (q != i) { i = q - 1; continue; }
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '=' && depth == 0) return i;
        }
        return -1;
    }

    // ================== ВСПОМОГАТЕЛЬНЫЕ ==================

    /** Валидация идентификатора: буквы/digit/`_`, не с цифры, длина ≤ MAX_IDENT. */
    //проверяет, что строка — корректный идентификатор (имя столбца, таблицы), и либо возвращает её, либо бросает исключение
    private static String requireIdentifier(String name, String what, String context, int pos)
            throws SqlParseException {
        if (name == null || name.isEmpty())
            throw new SqlParseException("Пустое " + what, context, pos);
        if (name.length() > MAX_IDENT)
            throw new SqlParseException(what + " слишком длинное (>" + MAX_IDENT + ")", context, pos);
        char c0 = name.charAt(0);
        if (!Character.isLetter(c0) && c0 != '_')
            throw new SqlParseException(what + " должно начинаться с буквы или '_': " + name, context, pos);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_')
                throw new SqlParseException(what + " содержит недопустимый символ '" + c
                        + "': " + name, context, pos);
        }
        return name;
    }


    //определяет, начинается ли строка s с позиции i на строковый литерал, и если да — вычисляет, где он заканчивается.
    private static int skipQuotedStrict(String s, int i, String context) throws SqlParseException {
        if (i >= s.length()) return i;
        char c = s.charAt(i);
        if (c != '\'' && c != '"') return i;
        int start = i;
        char quote = c;
        int j = i + 1;
        while (j < s.length()) {
            char x = s.charAt(j);
            if (x == '\\' && j + 1 < s.length()
                    && (s.charAt(j + 1) == quote || s.charAt(j + 1) == '\\')) {
                j += 2; continue;
            }
            if (x == quote) {
                if (j + 1 < s.length() && s.charAt(j + 1) == quote) { j += 2; continue; }
                return j + 1;
            }
            j++;
        }
        throw new SqlParseException("Незакрытая кавычка " + quote
                + " (позиция " + start + ")", context, start);
    }


    //простейший токенизатор: метод режет строку на токены, сохраняя строковые литералы целиком
    private static String[] splitWords(String s) throws SqlParseException {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                int end = skipQuotedStrict(s, i, s);
                cur.append(s, i, end);
                i = end;
                continue;
            }
            if (Character.isWhitespace(c) || c == '(' || c == ')' || c == ',' || c == ';') {
                if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
            i++;
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    //Поиск ключевого слова без учёта регистра, вне кавычек, с границами идентификатора.
    private static int keywordIndex(String s, String kw, int from) throws SqlParseException {
        int i = from;
        int n = s.length(), m = kw.length();
        while (i <= n - m) {
            int q = skipQuotedStrict(s, i, s);
            if (q != i) { i = q; continue; }
            boolean leftOk  = i == 0 || !isIdentifierChar(s.charAt(i - 1));
            boolean rightOk = i + m == n || !isIdentifierChar(s.charAt(i + m));
            if (leftOk && rightOk && s.regionMatches(true, i, kw, 0, m)) return i;
            i++;
        }
        return -1;
    }

    private static boolean isIdentifierChar(char c) {

        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     Метод ищет парную закрывающую скобку для открывающей (, стоящей в позиции open.
     Найдёт — вернёт её индекс; не найдёт или на входе не скобка — исключение.
     */
    private static int matchParen(String s, int open, String context) throws SqlParseException {
        if (open < 0 || open >= s.length() || s.charAt(open) != '(')
            throw new SqlParseException("Ожидалось '(' на позиции " + open, context, open);
        int depth = 0;
        int i = open;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') {
                i = skipQuotedStrict(s, i, context);
                continue;
            }
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
            i++;
        }
        throw new SqlParseException("Не найдена закрывающая ')' для '(' на позиции " + open,
                context, open);
    }

    //Делит строку по разделителю на верхнем уровне
    private static List<String> splitTopLevel(String s, char sep, String context)
            throws SqlParseException {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0, i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"') { i = skipQuotedStrict(s, i, context); continue; }
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == sep && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
            i++;
        }
        out.add(s.substring(start));
        return out;
    }

    private static List<String> splitIdentifiers(String s, String context) throws SqlParseException {
        List<String> out = new ArrayList<>();
        for (String p : splitTopLevel(s, ',', context)) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            requireIdentifier(t, "имя столбца", context, 0);
            out.add(t);
        }
        return out;
    }
}
