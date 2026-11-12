import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Простой исполнитель SQL-подобных запросов поверх DataStore и IndexManager.
 * Поддерживает: INSERT, SELECT, UPDATE, DELETE, CREATE INDEX, CREATE TABLE, DROP TABLE.
 * Результат каждого запроса — список строк (каждая строка — Map "колонка -> значение"),
 * который удобно выводить в табличном виде.
 */
public class QueryExecutor {

    private final DataStore dataStore;
    private final IndexManager indexManager;
    private final SqlParser parser = new SqlParser();

    public QueryExecutor(DataStore dataStore, IndexManager indexManager) {
        this.dataStore = dataStore;
        this.indexManager = indexManager;
    }

    // ================== ТОЧКА ВХОДА ==================

    /** Выполнить один запрос и вернуть результат в виде списка строк. */
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
                throw new SqlParseException("Неизвестный тип запроса: " + q.getType());
        }
    }

    // ================== INSERT ==================

    private List<Map<String, Object>> doInsert(SqlParser.InsertQuery q) throws SqlParseException {
        String table = q.table.toLowerCase();
        List<Map<String, Object>> inserted = new ArrayList<>();

        List<String> columns = q.columns; // может быть пустым -> подставим все колонки

        for (List<SqlParser.Value> row : q.rows) {
            Map<String, Object> rec = buildRecord(table, columns, row);

            int id = ((Number) rec.get("id")).intValue();
            if (idExists(table, id)) {
                throw new SqlParseException("Дубликат первичного ключа id=" + id + " в таблице " + table);
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
                throw new SqlParseException("Неизвестная таблица: " + table);
            }
            inserted.add(rec);
        }
        return inserted;
    }

    /** Строит Map "поле -> значение" для одной вставляемой строки. */
    private Map<String, Object> buildRecord(String table, List<String> columns,
                                            List<SqlParser.Value> values)
            throws SqlParseException {
        Map<String, Object> rec = new LinkedHashMap<>();

        if (table.equals("books")) {
            // порядок по умолчанию — как в Book
            List<String> all = List.of("id", "title", "authorId", "price", "publishDate", "isAvailable");
            fillRecord(rec, all, columns, values);
            // проверка на пропущенные обязательные поля
            requireFields(rec, "id", "title", "authorId", "price", "publishDate", "isAvailable");
        } else if (table.equals("authors")) {
            List<String> all = List.of("id", "name", "birthDate", "isAlive");
            fillRecord(rec, all, columns, values);
            requireFields(rec, "id", "name", "birthDate", "isAlive");
        } else {
            throw new SqlParseException("Неизвестная таблица: " + table);
        }
        return rec;
    }

    private void fillRecord(Map<String, Object> rec, List<String> allColumns,
                            List<String> specified, List<SqlParser.Value> values)
            throws SqlParseException {
        List<String> effective = specified.isEmpty() ? allColumns : specified;
        if (effective.size() != values.size()) {
            throw new SqlParseException("Число значений не совпадает с числом столбцов");
        }
        for (int i = 0; i < effective.size(); i++) {
            String col = effective.get(i);
            rec.put(col, convert(values.get(i), col));
        }
    }

    private void requireFields(Map<String, Object> rec, String... fields)
            throws SqlParseException {
        for (String f : fields) {
            if (!rec.containsKey(f)) {
                throw new SqlParseException("Не задано поле '" + f + "'");
            }
        }
    }

    /**
     * Приводит SqlParser.Value к Java-типу, ожидаемому полем.
     * Просто: смотрим на поле и кастуем.
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
                throw new SqlParseException("Неизвестное поле: " + column);
        }
        throw new SqlParseException("Тип значения не соответствует полю '" + column + "'");
    }

    private boolean idExists(String table, int id) {
        if (table.equals("books"))   return dataStore.findBookById(id) != null;
        if (table.equals("authors")) return dataStore.findAuthorById(id) != null;
        return false;
    }

    // ================== SELECT ==================

    private List<Map<String, Object>> doSelect(SqlParser.SelectQuery q) throws SqlParseException {
        String table = q.table.toLowerCase();
        List<Map<String, Object>> result = new ArrayList<>();

        // Оптимизация: если WHERE — это одиночное `= ` по индексированному столбцу,
        // берём список ID из индекса.
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
            throw new SqlParseException("Неизвестная таблица: " + table);
        }

        return project(result, q.columns);
    }

    /** Если WHERE — одиночное `col = value` по индексированному столбцу, вернуть список ID. */
    private List<Integer> tryGetIdsFromIndex(String table, SqlParser.Condition where) {
        if (where == null || where.next != null) return null;
        if (where.operator != SqlParser.Condition.Operator.EQ) return null;

        String col = where.column.toLowerCase();
        String value = String.valueOf(where.value.obj);
        return indexManager.searchByIndex(table, col, value);
    }

    /** Оставить только запрошенные колонки (если columns пуст — вернуть как есть). */
    private List<Map<String, Object>> project(List<Map<String, Object>> rows, List<String> columns)
            throws SqlParseException {
        if (columns.isEmpty()) return rows;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> p = new LinkedHashMap<>();
            for (String col : columns) {
                String c = col.toLowerCase();
                if (!row.containsKey(c)) {
                    throw new SqlParseException("Неизвестная колонка в SELECT: " + col);
                }
                p.put(c, row.get(c));
            }
            out.add(p);
        }
        return out;
    }

    // ================== UPDATE ==================

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
            throw new SqlParseException("Неизвестная таблица: " + table);
        }
        return updated;
    }

    // ================== DELETE ==================

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
            throw new SqlParseException("Неизвестная таблица: " + table);
        }
        return deleted;
    }

    // ================== CREATE INDEX ==================

    private List<Map<String, Object>> doCreateIndex(SqlParser.CreateIndexQuery q)
            throws SqlParseException {
        String table = q.table.toLowerCase();
        String col = q.column.toLowerCase();

        if (table.equals("books")) {
            if (!Set.of("id", "title", "authorid", "price", "publishdate", "isavailable").contains(col))
                throw new SqlParseException("Неизвестная колонка для индекса: " + q.column);
            indexManager.createIndex(table, col, dataStore.getBooks());
        } else if (table.equals("authors")) {
            if (!Set.of("id", "name", "birthdate", "isalive").contains(col))
                throw new SqlParseException("Неизвестная колонка для индекса: " + q.column);
            indexManager.createIndex(table, col, dataStore.getAuthors());
        } else {
            throw new SqlParseException("Неизвестная таблица: " + table);
        }
        return List.of(Map.of("result", "Индекс создан"));
    }

    // ================== CREATE TABLE / DROP TABLE ==================

    private List<Map<String, Object>> doCreateTable(SqlParser.CreateTableQuery q)
            throws SqlParseException {
        // В этой простой версии поддерживаются только books и authors.
        String table = q.table.toLowerCase();
        if (!table.equals("books") && !table.equals("authors")) {
            throw new SqlParseException("CREATE TABLE поддерживается только для books/authors");
        }
        return List.of(Map.of("result", "Таблица уже существует: " + table));
    }

    private List<Map<String, Object>> doDropTable(SqlParser.DropTableQuery q)
            throws SqlParseException {
        throw new SqlParseException("DROP TABLE не поддерживается (системная таблица)");
    }

    // ================== УСЛОВИЯ WHERE ==================

    private boolean matches(Map<String, Object> row, SqlParser.Condition cond) {
        if (cond == null) return true;
        boolean cur = evaluate(row, cond);
        if (cond.next == null) return cur;
        return cond.andNext ? (cur && matches(row, cond.next))
                : (cur || matches(row, cond.next));
    }

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compare(Object a, Object b) {
        if (a instanceof Number && b instanceof Number)
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        if (a instanceof Comparable && a.getClass().isInstance(b))
            return ((Comparable) a).compareTo(b);
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    // ================== Book/Author <-> Map ==================

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

    private Book mapToBook(Map<String, Object> m) {
        return new Book(
                ((Number) m.get("id")).intValue(),
                (String) m.get("title"),
                ((Number) m.get("authorId")).intValue(),
                ((Number) m.get("price")).doubleValue(),
                (LocalDate) m.get("publishDate"),
                (Boolean) m.get("isAvailable"));
    }

    private Map<String, Object> authorToMap(Author a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("name", a.getName());
        m.put("birthDate", a.getBirthDate());
        m.put("isAlive", a.isAlive());
        return m;
    }

    private Author mapToAuthor(Map<String, Object> m) {
        return new Author(
                ((Number) m.get("id")).intValue(),
                (String) m.get("name"),
                (LocalDate) m.get("birthDate"),
                (Boolean) m.get("isAlive"));
    }
}
