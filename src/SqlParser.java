import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple parser for SQL-like queries.
 * Understands INSERT, SELECT, UPDATE, DELETE, CREATE TABLE, DROP TABLE and CREATE INDEX.
 * The output is a ready-made query object that the executor can work with.
 */
public class SqlParser {

    private static final int MAX_IDENT = 128;

    /**
     Common base for all queries.
     Stores only the query type, so the executor knows what it is dealing with.
     */
    public abstract static class Query {
        public enum Type { INSERT, SELECT, UPDATE, DELETE, CREATE_TABLE, DROP_TABLE, CREATE_INDEX }

        private final Type type;

        /**
         * Creates a query of the given type.
         * Called from the constructors of concrete queries.
         */
        protected Query(Type type) { this.type = type; }

        /** Returns the type of this query. */
        public Type getType() { return type; }
    }

    /**
     * A parsed INSERT: which table, which columns, and which values to insert.
     */
    public static class InsertQuery extends Query {
        public final String table;
        public final List<String> columns;
        public final List<List<Value>> rows;

        /**
         * Creates an INSERT query.
         *
         * @param table   the table name
         * @param columns the column list (may be empty)
         * @param rows    the rows of values to insert
         */
        public InsertQuery(String table, List<String> columns, List<List<Value>> rows) {
            super(Type.INSERT);
            this.table = table;
            this.columns = columns;
            this.rows = rows;
        }
    }

    /**
     * A parsed SELECT: which columns, from which table, and with which condition.
     */
    public static class SelectQuery extends Query {
        public final List<String> columns;
        public final String table;
        public final Condition where;

        /**
         * Creates a SELECT query.
         *
         * @param columns which columns to select (empty means all)
         * @param table   which table to select from
         * @param where   the WHERE condition, or null if there is none
         */
        public SelectQuery(List<String> columns, String table, Condition where) {
            super(Type.SELECT);
            this.columns = columns;
            this.table = table;
            this.where = where;
        }
    }

    /**
     * A parsed UPDATE: what to change and where to change it.
     */
    public static class UpdateQuery extends Query {
        public final String table;
        public final Map<String, Value> assignments;
        public final Condition where;

        /**
         * Creates an UPDATE query.
         *
         * @param table       which table to change
         * @param assignments which columns to change and to what values
         * @param where       the WHERE condition, or null if there is none
         */
        public UpdateQuery(String table, Map<String, Value> assignments, Condition where) {
            super(Type.UPDATE);
            this.table = table;
            this.assignments = assignments;
            this.where = where;
        }
    }

    /**
     * A parsed DELETE: from which table and by which condition to delete.
     */
    public static class DeleteQuery extends Query {
        public final String table;
        public final Condition where;

        /**
         * Creates a DELETE query.
         *
         * @param table which table to delete from
         * @param where the WHERE condition, or null if there is none
         */
        public DeleteQuery(String table, Condition where) {
            super(Type.DELETE);
            this.table = table;
            this.where = where;
        }
    }

    /**
     * A parsed CREATE TABLE: the table name and its column list.
     */
    public static class CreateTableQuery extends Query {
        public final String table;
        public final List<ColDefinition> columns;

        /**
         * Creates a create-table query.
         *
         * @param table   the new table name
         * @param columns the description of its columns
         */
        public CreateTableQuery(String table, List<ColDefinition> columns) {
            super(Type.CREATE_TABLE);
            this.table = table;
            this.columns = columns;
        }
    }

    /**
     * A parsed DROP TABLE: which table to drop.
     */
    public static class DropTableQuery extends Query {
        public final String table;

        /**
         * Creates a drop-table query.
         *
         * @param table the table name
         */
        public DropTableQuery(String table) {
            super(Type.DROP_TABLE);
            this.table = table;
        }
    }

    /**
     * A parsed CREATE INDEX: the index name, the table and the column.
     */
    public static class CreateIndexQuery extends Query {
        public final String indexName;
        public final String table;
        public final String column;

        /**
         * Creates a create-index query.
         *
         * @param indexName the index name
         * @param table     the table
         * @param column    the column the index is built on
         */
        public CreateIndexQuery(String indexName, String table, String column) {
            super(Type.CREATE_INDEX);
            this.indexName = indexName;
            this.table = table;
            this.column = column;
        }
    }

    /**
     * The data types the parser supports: integer, double, string, date, boolean.
     */
    public enum ColType {
        INTEGER, DOUBLE, STRING, DATE, BOOLEAN;

        /**
         * Turns an SQL type name (INT, TEXT, BOOL, etc.) into a ColType.
         *
         * @param s the type name
         * @return the matching ColType
         * @throws SqlParseException if the type is not recognized
         */
        public static ColType fromString(String s) throws SqlParseException {
            switch (s.trim().toUpperCase()) {
                case "INT": case "INTEGER": return INTEGER;
                case "DOUBLE": case "FLOAT": return DOUBLE;
                case "STRING": case "TEXT": return STRING;
                case "DATE": return DATE;
                case "BOOLEAN": case "BOOL": return BOOLEAN;
                default: throw new SqlParseException("Unknown column type: " + s);
            }
        }
    }

    /**
     * The description of one column in CREATE TABLE: name, type, and whether it is the primary key.
     */
    public static class ColDefinition {
        public final String name;
        public final ColType type;
        public final boolean primaryKey;

        /**
         * Creates a column description.
         *
         * @param name       the column name
         * @param type       the data type
         * @param primarykey whether the column is the primary key
         */
        public ColDefinition(String name, ColType type, boolean primarykey) {
            this.name = name;
            this.type = type;
            this.primaryKey = primarykey;
        }
    }

    /**
     * A single SQL value: a number, a string, a date, true/false, or NULL.
     * Stores the value itself, its type, and the NULL flag.
     */
    public static class Value {

        public final Object obj;
        public final ColType type;
        public final boolean isNull;

        /** A ready-made object for SQL NULL. */
        public static final Value NULL = new Value(null, null, true);

        private Value(Object obj, ColType type) { this(obj, type, false); }
        private Value(Object obj, ColType type, boolean isNull) {
            this.obj = obj;
            this.type = type;
            this.isNull = isNull;
        }

        /**
         * Parses one text literal from SQL into a ready-made value.
         * Understands quoted strings, dates like '2024-01-01', TRUE/FALSE,
         * NULL, and numbers (both integer and double).
         *
         * @param token the literal text
         * @return the parsed value
         * @throws SqlParseException if the literal is empty or unclear
         */
        public static Value parse(String token) throws SqlParseException {
            String t = token.trim();
            if (t.isEmpty()) throw new SqlParseException("Empty literal");
            if (t.length() >= 2
                    && ((t.charAt(0) == '\'' && t.charAt(t.length() - 1) == '\'')
                    || (t.charAt(0) == '"'  && t.charAt(t.length() - 1) == '"'))) {
                char q = t.charAt(0);
                String inner = unescape(t.substring(1, t.length() - 1), q);
                if (inner.length() == 10 && inner.charAt(4) == '-' && inner.charAt(7) == '-') {
                    try { return new Value(LocalDate.parse(inner), ColType.DATE); }
                    catch (DateTimeParseException ignored) { }
                }
                return new Value(inner, ColType.STRING);
            }
            String l = t.toUpperCase();
            if ("TRUE".equals(l))  return new Value(Boolean.TRUE,  ColType.BOOLEAN);
            if ("FALSE".equals(l)) return new Value(Boolean.FALSE, ColType.BOOLEAN);
            if ("NULL".equals(l))  return NULL;
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E"))
                    return new Value(Double.parseDouble(t), ColType.DOUBLE);
                return new Value(Long.parseLong(t), ColType.INTEGER);
            } catch (NumberFormatException ignored) { }

            throw new SqlParseException("Could not recognize value: " + token);
        }

        /**
         * Removes escaping inside a string: doubled quotes ('' or "")
         * become a single quote, and \" and \\ become plain characters.
         *
         * @param s the text inside the quotes
         * @param q which quote character was used
         * @return the text without escaping
         */
        private static String unescape(String s, char q) {
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);

                if (c == q && i + 1 < s.length() && s.charAt(i + 1) == q) { sb.append(q); i++; }
                else if (c == '\\' && i + 1 < s.length()
                        && (s.charAt(i + 1) == '\'' || s.charAt(i + 1) == '"' || s.charAt(i + 1) == '\\')) {
                    sb.append(s.charAt(i + 1)); i++;
                } else sb.append(c);
            }
            return sb.toString();
        }

        /** Returns the value as SQL text (for debugging and display). */
        @Override public String toString() {
            return isNull ? "NULL" : (obj instanceof String ? "'" + obj + "'" : String.valueOf(obj));
        }
    }

    /**
     * A single WHERE condition: a column, an operator, and a value.
     * Conditions are linked into a chain via next, and andNext says
     * what joins the current one to the next: AND or OR.
     */
    public static class Condition {

        /** Comparison operators used in WHERE. */
        public enum Operator {
            EQ("="), NEQ("<>"), GT(">"), LT("<"), GTE(">="), LTE("<=");

            public final String symbol;
            Operator(String s) { symbol = s; }

            /**
             * Turns an operator text (=, !=, >=, etc.) into an Operator.
             *
             * @param s the operator text
             * @return the matching operator
             * @throws SqlParseException if the operator is unknown
             */
            public static Operator fromString(String s) throws SqlParseException {
                for (Operator o : values()) if (o.symbol.equals(s)) return o;
                if ("!=".equals(s)) return NEQ;
                throw new SqlParseException("Unknown operator: " + s);
            }
        }

        public final String column;
        public final Operator operator;
        public final Value value;
        public final boolean andNext;
        public final Condition next;

        /**
         * Creates a single condition.
         *
         * @param column   the column name
         * @param operator the comparison operator
         * @param value    what to compare with
         * @param andNext  true — the next condition is joined with AND, false — with OR
         * @param next     the next condition, or null
         */
        public Condition(String column, Operator operator, Value value, boolean andNext, Condition next) {
            this.column = column;
            this.operator = operator;
            this.value = value;
            this.andNext = andNext;
            this.next = next;
        }
    }

    /**
     * Parses one SQL query and returns a ready-made object.
     * This is the only method that needs to be called from outside.
     *
     * @param sql the query text
     * @return the parsed query
     * @throws SqlParseException if the query is empty, unknown, or malformed
     */
    public Query parse(String sql) throws SqlParseException {
        if (sql == null || sql.trim().isEmpty()) throw new SqlParseException("Empty query");
        String s = sql.trim();
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();

        String[] w = splitWords(s);
        if (w.length == 0) throw new SqlParseException("Empty query");
        switch (w[0].toUpperCase()) {
            case "INSERT": return parseInsert(s);
            case "SELECT": return parseSelect(s);
            case "UPDATE": return parseUpdate(s);
            case "DELETE": return parseDelete(s);
            case "CREATE": return parseCreate(s);
            case "DROP":   return parseDrop(s);
            default: throw new SqlParseException("Unknown command: " + w[0], s, 0);
        }
    }

    /**
     * Parses INSERT INTO ... VALUES ...
     * Finds INTO and VALUES, cuts the string at them, and parses each part separately.
     *
     * @param s the query text
     * @return a ready-made InsertQuery
     * @throws SqlParseException if INTO/VALUES is missing or the values are incorrect
     */
    private InsertQuery parseInsert(String s) throws SqlParseException {
        int into = keywordIndex(s, "INTO", 0);
        if (into < 0) throw new SqlParseException("INSERT without INTO", s, 0);
        int values = keywordIndex(s, "VALUES", into + 4);
        if (values < 0) throw new SqlParseException("INSERT without VALUES", s, into);

        String header = s.substring(into + 4, values).trim();
        String table;
        List<String> columns = new ArrayList<>();
        int pOpen = header.indexOf('(');
        if (pOpen >= 0) {
            table = header.substring(0, pOpen).trim();
            requireIdentifier(table, "table name", s, into + 4);
            int pClose = matchParen(header, pOpen, s);
            columns = splitIdentifiers(header.substring(pOpen + 1, pClose), s);
        } else {
            table = header;
            requireIdentifier(table, "table name", s, into + 4);
        }

        String valuesPart = s.substring(values + "VALUES".length()).trim();
        List<List<Value>> rows = new ArrayList<>();
        int i = 0;
        while (i < valuesPart.length()) {
            char c = valuesPart.charAt(i);
            if (Character.isWhitespace(c) || c == ',') { i++; continue; }
            if (c != '(') throw new SqlParseException("Expected '(' in VALUES", s, values + i);
            int close = matchParen(valuesPart, i, s);
            List<Value> row = new ArrayList<>();
            for (String v : splitTopLevel(valuesPart.substring(i + 1, close), ',', s)) {
                if (!v.trim().isEmpty()) row.add(Value.parse(v));
            }
            rows.add(row);
            i = close + 1;
        }
        if (rows.isEmpty()) throw new SqlParseException("INSERT without values", s, values);
        if (!columns.isEmpty()) {
            for (List<Value> r : rows) {
                if (r.size() != columns.size())
                    throw new SqlParseException("Value count does not match column count", s, values);
            }
        }
        return new InsertQuery(table, columns, rows);
    }

    /**
     * Parses SELECT columns FROM table [WHERE ...].
     *
     * @param s the query text
     * @return a ready-made SelectQuery
     * @throws SqlParseException if FROM is missing or the condition is malformed
     */
    private SelectQuery parseSelect(String s) throws SqlParseException {
        int from = keywordIndex(s, "FROM", "SELECT".length());
        if (from < 0) throw new SqlParseException("SELECT without FROM", s, 0);

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
        requireIdentifier(table, "table name", s, from + 4);
        return new SelectQuery(columns, table, cond);
    }

    /**
     * Parses UPDATE table SET column = value [, ...] [WHERE ...].
     *
     * @param s the query text
     * @return a ready-made UpdateQuery
     * @throws SqlParseException if SET is missing, there are no assignments, or they are incorrect
     */
    private UpdateQuery parseUpdate(String s) throws SqlParseException {
        int set = keywordIndex(s, "SET", "UPDATE".length());
        if (set < 0) throw new SqlParseException("UPDATE without SET", s, 0);
        String table = s.substring("UPDATE".length(), set).trim();
        requireIdentifier(table, "table name", s, "UPDATE".length());

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
            if (eq < 0) throw new SqlParseException("Expected '=' in SET: " + a, s, set);
            String col = a.substring(0, eq).trim();
            String val = a.substring(eq + 1).trim();
            requireIdentifier(col, "column name", s, set);
            if (val.isEmpty()) throw new SqlParseException("Empty value in SET: " + col, s, set);
            map.put(col, Value.parse(val));
        }
        if (map.isEmpty()) throw new SqlParseException("UPDATE without assignments", s, set);
        return new UpdateQuery(table, map, cond);
    }

    /**
     * Parses DELETE FROM table [WHERE ...].
     *
     * @param s the query text
     * @return a ready-made DeleteQuery
     * @throws SqlParseException if FROM is missing or the condition is malformed
     */
    private DeleteQuery parseDelete(String s) throws SqlParseException {
        int from = keywordIndex(s, "FROM", "DELETE".length());
        if (from < 0) throw new SqlParseException("DELETE without FROM", s, 0);
        int where = keywordIndex(s, "WHERE", from + 4);
        String table;
        Condition cond = null;
        if (where < 0) {
            table = s.substring(from + 4).trim();
        } else {
            table = s.substring(from + 4, where).trim();
            cond = parseCondition(s.substring(where + "WHERE".length()).trim(), s);
        }
        requireIdentifier(table, "table name", s, from + 4);
        return new DeleteQuery(table, cond);
    }

    /**
     * Figures out whether this is CREATE TABLE or CREATE INDEX
     * and hands the parsing over to the right method.
     *
     * @param s the query text
     * @return a ready-made query
     * @throws SqlParseException if it is neither CREATE TABLE nor CREATE INDEX
     */
    private Query parseCreate(String s) throws SqlParseException {
        String rest = s.substring("CREATE".length()).trim();
        if (keywordIndex(rest, "TABLE", 0) == 0)
            return parseCreateTable(rest.substring("TABLE".length()).trim(), s);
        if (keywordIndex(rest, "INDEX", 0) == 0)
            return parseCreateIndex(rest.substring("INDEX".length()).trim(), s);
        throw new SqlParseException("CREATE supports only TABLE and INDEX", s, 0);
    }

    /**
     * Parses CREATE TABLE name (column type [PRIMARY KEY], ...).
     *
     * @param s       the text after CREATE TABLE
     * @param context the original query — for error messages
     * @return a ready-made CreateTableQuery
     * @throws SqlParseException if the opening parenthesis is missing, the columns are incorrect, or there are none
     */
    private CreateTableQuery parseCreateTable(String s, String context) throws SqlParseException {
        int pOpen = s.indexOf('(');
        if (pOpen < 0) throw new SqlParseException("CREATE TABLE without '('", context, 0);
        int pClose = matchParen(s, pOpen, context);
        String table = s.substring(0, pOpen).trim();
        requireIdentifier(table, "table name", context, 0);

        List<ColDefinition> cols = new ArrayList<>();
        for (String part : splitTopLevel(s.substring(pOpen + 1, pClose), ',', context)) {
            part = part.trim();
            if (part.isEmpty()) continue;
            String[] w = splitWords(part);
            if (w.length < 2) throw new SqlParseException("Invalid column: " + part, context, 0);
            requireIdentifier(w[0], "column name", context, 0);
            boolean pk = false;
            for (int i = 2; i < w.length - 1; i++)
                if ("PRIMARY".equalsIgnoreCase(w[i]) && "KEY".equalsIgnoreCase(w[i + 1])) pk = true;
            cols.add(new ColDefinition(w[0], ColType.fromString(w[1]), pk));
        }
        if (cols.isEmpty()) throw new SqlParseException("CREATE TABLE without columns", context, 0);
        return new CreateTableQuery(table, cols);
    }

    /**
     * Parses CREATE INDEX name ON table (column).
     *
     * @param s       the text after CREATE INDEX
     * @param context the original query — for error messages
     * @return a ready-made CreateIndexQuery
     * @throws SqlParseException if ON or the parenthesis is missing, or the names are incorrect
     */
    private CreateIndexQuery parseCreateIndex(String s, String context) throws SqlParseException {
        int on = keywordIndex(s, "ON", 0);
        if (on < 0) throw new SqlParseException("CREATE INDEX without ON", context, 0);
        String indexName = s.substring(0, on).trim();
        requireIdentifier(indexName, "index name", context, 0);
        String tail = s.substring(on + 2).trim();
        int pOpen = tail.indexOf('(');
        if (pOpen < 0) throw new SqlParseException("CREATE INDEX without '('", context, 0);
        int pClose = matchParen(tail, pOpen, context);
        String table = tail.substring(0, pOpen).trim();
        String col = tail.substring(pOpen + 1, pClose).trim();
        requireIdentifier(table, "table name", context, 0);
        requireIdentifier(col, "column name", context, 0);
        return new CreateIndexQuery(indexName, table, col);
    }

    /**
     * Parses DROP TABLE name.
     *
     * @param s the query text
     * @return a ready-made DropTableQuery
     * @throws SqlParseException if it is not DROP TABLE or the table name is bad
     */
    private DropTableQuery parseDrop(String s) throws SqlParseException {
        String rest = s.substring("DROP".length()).trim();
        if (keywordIndex(rest, "TABLE", 0) != 0)
            throw new SqlParseException("DROP supports only TABLE", s, 0);
        String table = rest.substring("TABLE".length()).trim();
        requireIdentifier(table, "table name", s, 0);
        return new DropTableQuery(table);
    }

    /**
     * Parses a WHERE clause into a chain of Condition objects.
     * First it splits by AND/OR (without touching what is inside quotes),
     * then it builds the chain from the end — that makes linking the nodes easier.
     *
     * @param s       the condition text after WHERE
     * @param context the original query — for error messages
     * @return the first condition of the chain, or null if there are none
     * @throws SqlParseException if one of the conditions is incorrect
     */
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

    /**
     * Parses one condition of the form "column operator value",
     * for example price > 100 or name = 'Ann'.
     *
     * @param s       the text of a single condition
     * @param andNext true — the next condition is joined with AND
     * @param next    the already-built tail of the chain, or null
     * @param context the original query — for error messages
     * @return a ready-made condition
     * @throws SqlParseException if no operator is found or the value is empty
     */
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
        if (idx < 0) throw new SqlParseException("Invalid condition: " + s, context, 0);
        String col = s.substring(0, idx).trim();
        String val = s.substring(idx + op.length()).trim();
        requireIdentifier(col, "column name in condition", context, 0);
        if (val.isEmpty()) throw new SqlParseException("Empty value in condition: " + s, context, 0);
        return new Condition(col, Condition.Operator.fromString(op), Value.parse(val), andNext, next);
    }

    /**
     * Finds the = character at the top level — that is, not inside parentheses
     * and not inside a quoted string. Returns -1 if not found.
     *
     * @param s the text to search
     * @return the position of = or -1
     * @throws SqlParseException if an unterminated quote is found
     */
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

    /**
     * Checks that a string is a valid name (of a table, column, or index):
     * only letters, digits, and _, does not start with a digit, length at most MAX_IDENT.
     *
     * @param name    the name to check
     * @param what    what kind of name this is — for the error message
     * @param context the original query — for the error message
     * @param pos     the position — for the error message
     * @return the same name if it is valid
     * @throws SqlParseException if the name is empty, too long, or contains bad characters
     */
    private static String requireIdentifier(String name, String what, String context, int pos)
            throws SqlParseException {
        if (name == null || name.isEmpty())
            throw new SqlParseException("Empty " + what, context, pos);
        if (name.length() > MAX_IDENT)
            throw new SqlParseException(what + " is too long (>" + MAX_IDENT + ")", context, pos);
        char c0 = name.charAt(0);
        if (!Character.isLetter(c0) && c0 != '_')
            throw new SqlParseException(what + " must start with a letter or '_': " + name, context, pos);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_')
                throw new SqlParseException(what + " contains an invalid character '" + c
                        + "': " + name, context, pos);
        }
        return name;
    }

    /**
     * If a quoted string starts at position i, returns the position right after
     * its closing quote. If there is no quote, returns i unchanged.
     * Understands both doubled quotes ('') and backslash escaping (\).
     *
     * @param s       the text
     * @param i       the position to check
     * @param context the original query — for the error message
     * @return the position after the literal, or i if there is no literal
     * @throws SqlParseException if the quote is never closed
     */
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
        throw new SqlParseException("Unterminated quote " + quote
                + " (position " + start + ")", context, start);
    }

    /**
     * A simple tokenizer: splits the string into words by whitespace and ( ) , ;,
     * but keeps quoted strings intact.
     *
     * @param s the query text
     * @return the array of tokens
     * @throws SqlParseException if an unterminated quote is found
     */
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

    /**
     * Searches for a keyword in the string, case-insensitive, outside quotes,
     * and so that the neighbours are not letters/digits/_ (i.e. it is a whole word).
     *
     * @param s    where to search
     * @param kw   what to search for
     * @param from from which position to start
     * @return the word position, or -1 if not found
     * @throws SqlParseException if an unterminated quote is found
     */
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

    /** Checks whether a character can be part of a name (a letter, a digit, or _). */
    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Finds the closing parenthesis for the opening one at position open,
     * taking nested parentheses and quoted strings into account.
     *
     * @param s       the text
     * @param open    the position of the opening parenthesis (
     * @param context the original query — for the error message
     * @return the position of the closing parenthesis
     * @throws SqlParseException if there is no opening parenthesis or the closing one is not found
     */
    private static int matchParen(String s, int open, String context) throws SqlParseException {
        if (open < 0 || open >= s.length() || s.charAt(open) != '(')
            throw new SqlParseException("Expected '(' at position " + open, context, open);
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
        throw new SqlParseException("Closing ')' not found for '(' at position " + open,
                context, open);
    }

    /**
     * Splits the string by a separator at the top level — without touching
     * what is inside parentheses or quotes.
     *
     * @param s       the text
     * @param sep     the separator (for example, a comma)
     * @param context the original query — for the error message
     * @return the list of pieces
     * @throws SqlParseException if an unterminated quote is found
     */
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

    /**
     * Splits the string by commas at the top level and checks that
     * each piece is a valid column name.
     *
     * @param s       a comma-separated list
     * @param context the original query — for the error message
     * @return the list of column names
     * @throws SqlParseException if any name is invalid
     */
    private static List<String> splitIdentifiers(String s, String context) throws SqlParseException {
        List<String> out = new ArrayList<>();
        for (String p : splitTopLevel(s, ',', context)) {
            String t = p.trim();
            if (t.isEmpty()) continue;
            requireIdentifier(t, "column name", context, 0);
            out.add(t);
        }
        return out;
    }
}
