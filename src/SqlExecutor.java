import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A simple executor for SQL-like queries on top of {@link DataStore} and {@link IndexManager}.
 * Supports INSERT, SELECT, UPDATE, DELETE, CREATE INDEX, CREATE TABLE and DROP TABLE.
 * <p>
 * The executor takes a ready-made {@link SqlParser.Query} (a typed object),
 * turns it into operations on {@code DataStore} and {@code IndexManager},
 * and returns the result as a {@code List<Map<String, Object>>}.
 */
public class QueryExecutor {

    private final DataStore dataStore;
    private final IndexManager indexManager;
    private final SqlParser parser = new SqlParser();

    /**
     * Creates an executor that works with the given data store and index manager.
     *
     * @param dataStore    the store that holds books and authors
     * @param indexManager the manager that keeps the indexes up to date
     */
    public QueryExecutor(DataStore dataStore, IndexManager indexManager) {
        this.dataStore = dataStore;
        this.indexManager = indexManager;
    }

    // ================== ENTRY POINT ==================

    /**
     * Executes one SQL query and returns the result as a list of rows.
     * <p>
     * Each row is a map of column name to value. For DDL-like queries
     * the result is a single informational row.
     *
     * @param sql the SQL query text
     * @return the list of result rows
     * @throws SqlParseException if the query cannot be parsed or executed
     */
    public List<Map<String, Object>> execute(String sql) throws SqlParseException {
        SqlParser.Query q = parser.parse(sql);
        switch (q.getType()) {
            case INSERT:       return doInsert((SqlParser.InsertQuery) q);
            case SELECT:       return doSelect((SqlParser.SelectQuery) q);
            case UPDATE:       return doUpdate((SqlParser.UpdateQuery) q);
            case DELETE:       return doDelete((SqlParser.DeleteQuery) q);
            case CREATE_INDEX: return doCreateIndex((SqlParser.CreateIndexQuery) q);
            case CREATE_TABLE: return doCreateTable((SqlParser.CreateTableQuery) q);
            case DROP_TABLE:   return doDropTable((SqlParser.DropTableQuery) q);
            default:
                throw new SqlParseException("Unknown query type: " + q.getType());
        }
    }

    // ================== INSERT ==================

    /**
     * Inserts all rows of an INSERT query into the target table.
     * <p>
     * Each row is first turned into a map of field to value, then the map
     * is converted into a {@link Book} or {@link Author} and added to the store.
     * A duplicate primary key is rejected.
     *
     * @param q the parsed INSERT query
     * @return the list of inserted rows as maps
     * @throws SqlParseException if the table is unknown, a value has the wrong type,
     *                           a required field is missing, or the primary key already exists
     */
    private List<Map<String, Object>> doInsert(SqlParser.InsertQuery q) throws SqlParseException {
        String table = q.table.toLowerCase();
        List<Map<String, Object>> inserted = new ArrayList<>();

        List<String> columns = q.columns; // may be empty -> use all columns

        for (List<SqlParser.Value> row : q.rows) {
            Map<String, Object> rec = buildRecord(table, columns, row);

            int id = ((Number) rec.get("id")).intValue();
            if (idExists(table, id)) {
                throw new SqlParseException("Duplicate primary key id=" + id + " in table " + table);
            }

            if (table.equals("books")) {
                dataStore.addBook(new Book(
                        id,
                        (String) rec.get("title"),
                        ((Number) rec.get("authorId")).intValue(),
                        ((Number) rec.get("price")).doubleValue(),
                        (LocalDate) rec.get("publishDate"),
                        (Boolean) rec.get("isAvailable")));
            } else if (table.equals("authors")) {
                dataStore.addAuthor(new Author(
                        id,
                        (String) rec.get("name"),
                        (LocalDate) rec.get("birthDate"),
                        (Boolean) rec.get("isAlive")));
            } else {
                throw new SqlParseException("Unknown table: " + table);
            }
            inserted.add(rec);
        }
        return inserted;
    }

    /**
     * Builds a map of field to value for one row being inserted.
     * <p>
     * If the column list is empty, all columns of the table are used in
     * their natural order. After filling the map, the method checks that
     * all required fields are present.
     *
     * @param table   the table name
     * @param columns the column list from the query (may be empty)
     * @param values  the values for this row
     * @return the filled map of field to value
     * @throws SqlParseException if the table is unknown, a value has the wrong type,
     * or a required field is missing
     */
    private Map<String, Object> buildRecord(String table, List<String> columns,
                                            List<SqlParser.Value> values)
            throws SqlParseException {
        Map<String, Object> rec = new LinkedHashMap<>();

        if (table.equals("books")) {
            List<String> all = List.of("id", "title", "authorId", "price", "publishDate", "isAvailable");
            fillRecord(rec, all, columns, values);
            requireFields(rec, "id", "title", "authorId", "price", "publishDate", "isAvailable");
        } else if (table.equals("authors")) {
            List<String> all = List.of("id", "name", "birthDate", "isAlive");
            fillRecord(rec, all, columns, values);
            requireFields(rec, "id", "name", "birthDate", "isAlive");
        } else {
            throw new SqlParseException("Unknown table: " + table);
        }
        return rec;
    }

    /**
     * Fills the record map with converted values, matching each value to its column.
     * <p>
     * If no columns were specified, the default column order of the table is used.
     *
     * @param rec        the map to fill
     * @param allColumns the default column order
     * @param specified  the column list from the query (may be empty)
     * @param values     the values to put into the map
     * @throws SqlParseException if the number of values does not match the number of columns
     *                           or a value has the wrong type
     */
    private void fillRecord(Map<String, Object> rec, List<String> allColumns,
                            List<String> specified, List<SqlParser.Value> values)
            throws SqlParseException {
        List<String> effective = specified.isEmpty() ? allColumns : specified;
        if (effective.size() != values.size()) {
            throw new SqlParseException("Value count does not match column count");
        }
        for (int i = 0; i < effective.size(); i++) {
            String col = effective.get(i);
            rec.put(col, convert(values.get(i), col));
        }
    }

    /**
     * Checks that all the given fields are present in the record.
     *
     * @param rec    the record map
     * @param fields the required field names
     * @throws SqlParseException if any required field is missing
     */
    private void requireFields(Map<String, Object> rec, String... fields)
            throws SqlParseException {
        for (String f : fields) {
            if (!rec.containsKey(f)) {
                throw new SqlParseException("Missing field '" + f + "'");
            }
        }
    }

    /**
     * Converts a {@link SqlParser.Value} to the Java type expected by the field.
     * <p>
     * The conversion is based on the column name: ids become {@code int},
     * price becomes {@code double}, dates become {@code LocalDate},
     * booleans stay {@code Boolean}, and strings stay {@code String}.
     *
     * @param v      the value to convert
     * @param column the column name the value belongs to
     * @return the converted Java value, or {@code null} if the value is SQL NULL
     * @throws SqlParseException if the column is unknown or the value type does not match
     */
    private Object convert(SqlParser.Value v, String column) throws SqlParseException {
        if (v.isNull) return null;

        switch (column) {
            case "id":
            case "authorId":
                if (v.type == SqlParser.ColType.INTEGER) return ((Number) v.obj).intValue();
                if (v.type == SqlParser.ColType.DOUBLE)  return ((Number) v.obj).intValue();
                break;
            case "price":
                if (v.type == SqlParser.ColType.INTEGER || v.type == SqlParser.ColType.DOUBLE)
                    return ((Number) v.obj).doubleValue();
                break;
            case "title":
            case "name":
                if (v.type == SqlParser.ColType.STRING) return v.obj;
                break;
            case "publishDate":
            case "birthDate":
                if (v.type == SqlParser.ColType.DATE) return v.obj;
                break;
            case "isAvailable":
            case "isAlive":
                if (v.type == SqlParser.ColType.BOOLEAN) return v.obj;
                break;
            default:
                throw new SqlParseException("Unknown field: " + column);
        }
        throw new SqlParseException("Value type does not match field '" + column + "'");
    }

    /**
     * Checks whether a row with the given primary key already exists in the table.
     *
     * @param table the table name
     * @param id    the primary key to look for
     * @return {@code true} if such a row exists, {@code false} otherwise
     */
    private boolean idExists(String table, int id) {
        if (table.equals("books"))   return dataStore.findBookById(id) != null;
        if (table.equals("authors")) return dataStore.findAuthorById(id) != null;
        return false;
    }

    // ================== SELECT ==================

    /**
     * Runs a SELECT query and returns the matching rows.
     * <p>
     * If the WHERE clause is a single {@code =} on an indexed column,
     * the list of matching IDs is taken from the index instead of scanning
     * the whole table. The remaining filter is still applied on each row.
     *
     * @param q the parsed SELECT query
     * @return the list of matching rows as maps
     * @throws SqlParseException if the table is unknown or a requested column does not exist
     */
    private List<Map<String, Object>> doSelect(SqlParser.SelectQuery q) throws SqlParseException {
        String table = q.table.toLowerCase();
        List<Map<String, Object>> result = new ArrayList<>();

        List<Integer> indexIds = tryGetIdsFromIndex(table, q.where);

        if (table.equals("books")) {
            for (Book b : dataStore.getBooks()) {
                if (indexIds != null && !indexIds.contains(b.getId())) continue;
                Map<String, Object> m = bookToMap(b);
                if (matches(m, q.where)) result.add(m);
            }
        } else if (table.equals("authors")) {
            for (Author a : dataStore.getAuthors()) {
                if (indexIds != null && !indexIds.contains(a.getId())) continue;
                Map<String, Object> m = authorToMap(a);
                if (matches(m, q.where)) result.add(m);
            }
        } else {
            throw new SqlParseException("Unknown table: " + table);
        }

        return project(result, q.columns);
    }

    /**
     * Tries to get the list of matching row IDs from the index.
     * <p>
     * This only works when the WHERE clause is a single {@code column = value}
     * on an indexed column. In all other cases it returns {@code null},
     * which means "no index shortcut, scan the table".
     *
     * @param table the table name
     * @param where the WHERE condition (may be {@code null})
     * @return the list of matching IDs, or {@code null} if the index cannot be used
     */
    private List<Integer> tryGetIdsFromIndex(String table, SqlParser.Condition where) {
        if (where == null || where.next != null) return null;
        if (where.operator != SqlParser.Condition.Operator.EQ) return null;

        String col = where.column.toLowerCase();
        String value = String.valueOf(where.value.obj);
        return indexManager.searchByIndex(table, col, value);
    }

    /**
     * Leaves only the requested columns in each row.
     * If the column list is empty, the rows are returned unchanged.
     *
     * @param rows    the rows to project
     * @param columns the column names to keep
     * @return the projected rows
     * @throws SqlParseException if a requested column does not exist in the row
     */
    private List<Map<String, Object>> project(List<Map<String, Object>> rows, List<String> columns)
            throws SqlParseException {
        if (columns.isEmpty()) return rows;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> p = new LinkedHashMap<>();
            for (String col : columns) {
                String c = col.toLowerCase();
                if (!row.containsKey(c)) {
                    throw new SqlParseException("Unknown column in SELECT: " + col);
                }
                p.put(c, row.get(c));
            }
            out.add(p);
        }
        return out;
    }

    // ================== UPDATE ==================

    /**
     * Runs an UPDATE query and returns the updated rows.
     * <p>
     * For every matching row, the assignments are applied, the entity is
     * converted back and passed to {@code DataStore.update*}, which also
     * refreshes the indexes.
     *
     * @param q the parsed UPDATE query
     * @return the list of updated rows as maps
     * @throws SqlParseException if the table is unknown, a value has the wrong type,
     *                           or a column does not exist
     */
    private List<Map<String, Object>> doUpdate(SqlParser.UpdateQuery q) throws SqlParseException {
        String table = q.table.toLowerCase();
        List<Map<String, Object>> updated = new ArrayList<>();

        if (table.equals("books")) {
            for (Book b : new ArrayList<>(dataStore.getBooks())) {
                Map<String, Object> m = bookToMap(b);
                if (!matches(m, q.where)) continue;

                for (Map.Entry<String, SqlParser.Value> e : q.assignments.entrySet()) {
                    m.put(e.getKey().toLowerCase(), convert(e.getValue(), e.getKey()));
                }
                dataStore.updateBook(mapToBook(m));
                updated.add(m);
            }
        } else if (table.equals("authors")) {
            for (Author a : new ArrayList<>(dataStore.getAuthors())) {
                Map<String, Object> m = authorToMap(a);
                if (!matches(m, q.where)) continue;

                for (Map.Entry<String, SqlParser.Value> e : q.assignments.entrySet()) {
                    m.put(e.getKey().toLowerCase(), convert(e.getValue(), e.getKey()));
                }
                dataStore.updateAuthor(mapToAuthor(m));
                updated.add(m);
            }
        } else {
            throw new SqlParseException("Unknown table: " + table);
        }
        return updated;
    }

    // ================== DELETE ==================

    /**
     * Runs a DELETE query and returns the deleted rows.
     * <p>
     * Deleting an author is refused by {@code DataStore} if the author still
     * has books; such a row is simply skipped in the result.
     *
     * @param q the parsed DELETE query
     * @return the list of deleted rows as maps
     * @throws SqlParseException if the table is unknown
     */
    private List<Map<String, Object>> doDelete(SqlParser.DeleteQuery q) throws SqlParseException {
        String table = q.table.toLowerCase();
        List<Map<String, Object>> deleted = new ArrayList<>();

        if (table.equals("books")) {
            for (Book b : new ArrayList<>(dataStore.getBooks())) {
                Map<String, Object> m = bookToMap(b);
                if (matches(m, q.where)) {
                    dataStore.deleteBookById(b.getId());
                    deleted.add(m);
                }
            }
        } else if (table.equals("authors")) {
            for (Author a : new ArrayList<>(dataStore.getAuthors())) {
                Map<String, Object> m = authorToMap(a);
                if (matches(m, q.where)) {
                    if (dataStore.deleteAuthorById(a.getId())) deleted.add(m);
                }
            }
        } else {
            throw new SqlParseException("Unknown table: " + table);
        }
        return deleted;
    }

    // ================== CREATE INDEX ==================

    /**
     * Creates an index on the given column of the given table.
     * <p>
     * The column name is validated against the known columns of the table
     * before the index is built.
     *
     * @param q the parsed CREATE INDEX query
     * @return a single informational row saying the index was created
     * @throws SqlParseException if the table is unknown or the column does not exist
     */
    private List<Map<String, Object>> doCreateIndex(SqlParser.CreateIndexQuery q)
            throws SqlParseException {
        String table = q.table.toLowerCase();
        String col = q.column.toLowerCase();

        if (table.equals("books")) {
            if (!Set.of("id", "title", "authorid", "price", "publishdate", "isavailable").contains(col))
                throw new SqlParseException("Unknown column for index: " + q.column);
            indexManager.createIndex(table, col, dataStore.getBooks());
        } else if (table.equals("authors")) {
            if (!Set.of("id", "name", "birthdate", "isalive").contains(col))
                throw new SqlParseException("Unknown column for index: " + q.column);
            indexManager.createIndex(table, col, dataStore.getAuthors());
        } else {
            throw new SqlParseException("Unknown table: " + table);
        }
        return List.of(Map.of("result", "Index created"));
    }

    // ================== CREATE TABLE / DROP TABLE ==================

    /**
     * Handles CREATE TABLE.
     * <p>
     * In this simple version only {@code books} and {@code authors} are supported,
     * and both already exist, so the method just reports that.
     *
     * @param q the parsed CREATE TABLE query
     * @return a single informational row
     * @throws SqlParseException if the table is not {@code books} or {@code authors}
     */
    private List<Map<String, Object>> doCreateTable(SqlParser.CreateTableQuery q)
            throws SqlParseException {
        String table = q.table.toLowerCase();
        if (!table.equals("books") && !table.equals("authors")) {
            throw new SqlParseException("CREATE TABLE is supported only for books/authors");
        }
        return List.of(Map.of("result", "Table already exists: " + table));
    }

    /**
     * Handles DROP TABLE.
     * <p>
     * Dropping is not supported in this simple version, because the tables
     * are system tables.
     *
     * @param q the parsed DROP TABLE query
     * @return never returns normally
     * @throws SqlParseException always, because DROP TABLE is not supported
     */
    private List<Map<String, Object>> doDropTable(SqlParser.DropTableQuery q)
            throws SqlParseException {
        throw new SqlParseException("DROP TABLE is not supported (system table)");
    }

    // ================== WHERE CONDITIONS ==================

    /**
     * Checks whether a row satisfies the WHERE condition.
     * <p>
     * The condition is a chain of nodes; {@code andNext} of each node says
     * whether the next one is joined with AND or OR. A {@code null} condition
     * matches every row.
     *
     * @param row  the row to check
     * @param cond the condition chain (may be {@code null})
     * @return {@code true} if the row matches, {@code false} otherwise
     */
    private boolean matches(Map<String, Object> row, SqlParser.Condition cond) {
        if (cond == null) return true;
        boolean cur = evaluate(row, cond);
        if (cond.next == null) return cur;
        return cond.andNext ? (cur && matches(row, cond.next))
                : (cur || matches(row, cond.next));
    }

    /**
     * Evaluates a single condition node against a row.
     * <p>
     * Handles SQL {@code NULL} specially: {@code = NULL} matches only null
     * values, and any other operator matches only non-null values.
     *
     * @param row the row to check
     * @param c   the condition node
     * @return {@code true} if the node matches, {@code false} otherwise
     */
    private boolean evaluate(Map<String, Object> row, SqlParser.Condition c) {
        Object actual = row.get(c.column.toLowerCase());
        if (c.value.isNull) {
            return c.operator == SqlParser.Condition.Operator.EQ ? actual == null
                    : actual != null;
        }
        if (actual == null) return false;

        int cmp = compare(actual, c.value.obj);
        switch (c.operator) {
            case EQ:  return cmp == 0;
            case NEQ: return cmp != 0;
            case GT:  return cmp > 0;
            case LT:  return cmp < 0;
            case GTE: return cmp >= 0;
            case LTE: return cmp <= 0;
            default:  return false;
        }
    }

    /**
     * Compares two values.
     * <p>
     * Numbers are compared numerically, values of the same comparable type
     * are compared directly, and everything else is compared as strings.
     *
     * @param a the first value
     * @param b the second value
     * @return a negative number if {@code a < b}, zero if equal, a positive number if {@code a > b}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        if (a instanceof Comparable && a.getClass().isInstance(b))
            return ((Comparable) a).compareTo(b);
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    // ================== Book/Author <-> Map ==================

    /**
     * Converts a {@link Book} to a map of field to value.
     *
     * @param b the book to convert
     * @return the map of fields
     */
    private Map<String, Object> bookToMap(Book b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("title", b.getTitle());
        m.put("authorId", b.getAuthorId());
        m.put("price", b.getPrice());
        m.put("publishDate", b.getPublishDate());
        m.put("isAvailable", b.isAvailable());
        return m;
    }

    /**
     * Converts a map of fields back to a {@link Book}.
     *
     * @param m the map of fields
     * @return the restored book
     */
    private Book mapToBook(Map<String, Object> m) {
        return new Book(
                ((Number) m.get("id")).intValue(),
                (String) m.get("title"),
                ((Number) m.get("authorId")).intValue(),
                ((Number) m.get("price")).doubleValue(),
                (LocalDate) m.get("publishDate"),
                (Boolean) m.get("isAvailable"));
    }

    /**
     * Converts an {@link Author} to a map of field to value.
     *
     * @param a the author to convert
     * @return the map of fields
     */
    private Map<String, Object> authorToMap(Author a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("birthDate", a.getBirthDate());
        m.put("isAlive", a.isAlive());
        return m;
    }

    /**
     * Converts a map of fields back to an {@link Author}.
     *
     * @param m the map of fields
     * @return the restored author
     */
    private Author mapToAuthor(Map<String, Object> m) {
        return new Author(
                ((Number) m.get("id")).intValue(),
                (String) m.get("name"),
                (LocalDate) m.get("birthDate"),
                (Boolean) m.get("isAlive"));
    }
}
