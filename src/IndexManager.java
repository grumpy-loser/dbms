import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

/** IndexManager is realization of indexation algorithm,
 *  used for faster search and optimization of queries.
 *  <p>
 *  Handles the creation, persistence and maintenance of indexes.
 *  <p>
 *  It stores indexes in memory using nested HashMaps to provide fast O(1) lookups
 *  for queries with WHERE conditions on indexed columns.
 *  <p>
 *  The indexes are
 *  automatically updated on INSERT, UPDATE, and DELETE operations and are
 *  persisted to a JSON file on disk to survive application restarts.
 * */
public class IndexManager {

    /** Filename for storing all indexes.*/
    private static final String INDEX_FILE = "indexes.json";

    /** Configures Gson instance for index structures.
     *  */
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Storage structure for indexes.
     * <p>
     * Structure: Table Name -> Column Name -> Value -> {@link List} of Record IDs.
     * <p>
     * Example: "books" -> "title" -> "Java" -> [1, 5, 10]
     */
    private Map<String, Map<String, Map<String, List<Integer>>>> indexes = new HashMap<>();

    /** Loads all indexes from their JSON files into memory.
     * <p>
     * Called only once at the start.
     * */
    public void loadIndexes() {
        Path path = Paths.get(INDEX_FILE);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                /** TypeToken is used to let Gson know about input structures.*/
                Type type = new TypeToken<Map<String, Map<String, Map<String, List<Integer>>>>>(){}.getType();
                Map<String, Map<String, Map<String, List<Integer>>>> loaded = gson.fromJson(reader, type);
                if (loaded != null) this.indexes = loaded;
            } catch (IOException e) {
                System.err.println("Ошибка загрузки индексов: " + e.getMessage());
            }
        }
    }

    /** Saves the current indexes to the disc as a JSON file.*/
    private void saveIndexes() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(INDEX_FILE))) {
            gson.toJson(indexes, writer);
        } catch (IOException e) {
            System.err.println("Ошибка сохранения индексов: " + e.getMessage());
        }
    }

    /**
     * Creates a new index for a specific column in a table by iterating over the provided data.
     * The resulting index is stored in memory and saved to disk.
     *
     * @param tableName the name of the table
     * @param columnName the name of the column to index
     * @param data the list of entities to build the index from
     * @throws IllegalArgumentException if the table name is unknown
     */
    public void createIndex(String tableName, String columnName, List<?> data) {
        Map<String, List<Integer>> columnIndex = new HashMap<>();

        if (tableName.equalsIgnoreCase("books")) {
            for (Book b : (List<Book>) data) {
                String value = getBookFieldValue(b, columnName);
                if (value != null) {
                    columnIndex.computeIfAbsent(value, k -> new ArrayList<>()).add(b.getId());
                }
            }
        }
        else if (tableName.equalsIgnoreCase("authors")) {
            for (Author a : (List<Author>) data) {
                String value = getAuthorFieldValue(a, columnName);
                if (value != null) {
                    columnIndex.computeIfAbsent(value, k -> new ArrayList<>()).add(a.getId());
                }
            }
        }
        else {
            throw new IllegalArgumentException("Неизвестная таблица: " + tableName);
        }

        indexes.computeIfAbsent(tableName, k -> new HashMap<>()).put(columnName, columnIndex);
        saveIndexes();
    }

    /** Helper method to get value of a specific field
     *  from a {@link Book} object as a {@link String}
     *  @param b the book object
     *  @param column column name to get
     *  @return the {@link String} representation of field value,
     *  null if it is unknown
     *  */
    private String getBookFieldValue(Book b, String column) {
        switch (column.toLowerCase()) {
            case "title": return b.getTitle();
            case "price": return String.valueOf(b.getPrice());
            case "authorid": return String.valueOf(b.getAuthorId());
            case "isavailable": return String.valueOf(b.isAvailable());
            case "publishdate": return b.getPublishDate() != null ? b.getPublishDate().toString() : null;
            default: return null;
        }
    }

    /** Helper method to get value of a specific field
     *  from a {@link Author} object as a {@link String}
     *  @param a the author object
     *  @param column column name to get
     *  @return the String representation of field value,
     *  null if it is unknown
     *  */
    private String getAuthorFieldValue(Author a, String column) {
        switch (column.toLowerCase()) {
            case "name": return a.getName();
            case "isalive": return String.valueOf(a.isAlive());
            case "birthdate": return a.getBirthDate() != null ? a.getBirthDate().toString() : null;
            default: return null;
        }
    }

    /** Searches the index for a specific value
     * and returns list of matching record ids.
     * <p>
     * Allows to bypass full table scan.
     * @param tableName the name of the table
     * @param columnName the name of the index column
     * @param value the value to search for
     * @return a list of matchung ids, or null if no indexes exist
     * */
    public List<Integer> searchByIndex(String tableName, String columnName, String value) {
        if (!indexes.containsKey(tableName)) return null;
        if (!indexes.get(tableName).containsKey(columnName)) return null;
        return indexes.get(tableName).get(columnName).getOrDefault(value, new ArrayList<>());
    }

    /**
     * Helper method to get primary key for entity of any type.
     * */
    private int getEntityId(String tableName, Object entity) {
        if (tableName.equalsIgnoreCase("books")) return ((Book) entity).getId();
        if (tableName.equalsIgnoreCase("authors")) return ((Author) entity).getId();
        return -1;
    }

    /**
     *  Helper method to get String value of a field from entity based on table type.
     * */
    private String getFieldValue(String tableName, Object entity, String columnName) {
        if (tableName.equalsIgnoreCase("books")) return getBookFieldValue((Book) entity, columnName);
        if (tableName.equalsIgnoreCase("authors")) return getAuthorFieldValue((Author) entity, columnName);
        return null;
    }

    /**
     *  Updates all existing indexes when a new record is inserted.
     *  <p>
     *  Adds the new record's id to the corresponding lists in each index.
     *  @param tableName the name of table where record was inserted
     *  @param entity the new inserted entity
     * */
    public void updateOnInsert(String tableName, Object entity) {
        if (!indexes.containsKey(tableName)) return;

        int id = getEntityId(tableName, entity);
        for (String column : indexes.get(tableName).keySet()) {
            String value = getFieldValue(tableName, entity, column);
            if (value != null) {
                indexes.get(tableName).get(column)
                        .computeIfAbsent(value, k -> new ArrayList<>()).add(id);
            }
        }
        saveIndexes();
    }

    /**
     *  Updates all existing indexes when a record is deleted.
     *  <p>
     *  Removes the record's id from the corresponding lists in each index.
     *  @param tableName the name of table where record was deleted
     *  @param entity the deleted entity
     * */
    public void updateOnDelete(String tableName, Object entity) {
        if (!indexes.containsKey(tableName)) return;

        int id = getEntityId(tableName, entity);
        for (String column : indexes.get(tableName).keySet()) {
            String value = getFieldValue(tableName, entity, column);
            if (value != null) {
                List<Integer> ids = indexes.get(tableName).get(column).get(value);
                if (ids != null) {
                    ids.remove(Integer.valueOf(id)); // Удаляем сам объект Integer
                    if (ids.isEmpty()) {
                        indexes.get(tableName).get(column).remove(value);
                    }
                }
            }
        }
        saveIndexes();
    }

    /**
     *  Updates all existing indexes when a record is updated.
     *  <p>
     *  Compares old and new values to efficiently update only the changed columns.
     *  @param tableName the name of table where record was updated
     *  @param oldEntity the entity before update
     *  @param newEntity the entity aftere update
     * */
    public void updateOnUpdate(String tableName, Object oldEntity, Object newEntity) {
        if (!indexes.containsKey(tableName)) return;

        int id = getEntityId(tableName, oldEntity);

        /** Go through all indexisized columns. */
        for (String column : indexes.get(tableName).keySet()) {
            String oldValue = getFieldValue(tableName, oldEntity, column);
            String newValue = getFieldValue(tableName, newEntity, column);

            /** If nothing changed do not do anything.*/
            if (Objects.equals(oldValue, newValue)) continue;

            /** Delete old value. */
            if (oldValue != null) {
                List<Integer> oldIds = indexes.get(tableName).get(column).get(oldValue);
                if (oldIds != null) {
                    oldIds.remove(Integer.valueOf(id));
                    if (oldIds.isEmpty()) indexes.get(tableName).get(column).remove(oldValue);
                }
            }

            /** Add new value. */
            if (newValue != null) {
                indexes.get(tableName).get(column)
                        .computeIfAbsent(newValue, k -> new ArrayList<>()).add(id);
            }
        }
        saveIndexes();
    }

    /**
     *  Removes all indexes associated with the table from memory and disc.
     *  <p>
     *  Used when a table is dropped or cleared.
     *  @param tableName the name of table where record was updated
     * */
    public void clearTableIndexes(String tableName) {
        if (indexes.containsKey(tableName)) {
            indexes.remove(tableName);
            saveIndexes();
        }
    }
}
