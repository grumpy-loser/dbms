import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class IndexManager {
    private static final String INDEX_FILE = "indexes.json";
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Индексы: Имя таблицы -> (Имя колонки -> (Значение -> Список ID))
    private Map<String, Map<String, Map<String, List<Integer>>>> indexes = new HashMap<>();

    // Загрузка индексов при старте
    public void loadIndexes() {
        Path path = Paths.get(INDEX_FILE);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                Map<String, Map<String, Map<String, List<Integer>>>> loaded =
                        gson.fromJson(reader, indexes.getClass());
                if (loaded != null) this.indexes = loaded;
            } catch (IOException e) {
                System.err.println("Ошибка загрузки индексов: " + e.getMessage());
            }
        }
    }

    // Сохранение индексов на диск
    private void saveIndexes() {
        try (Writer writer = Files.newBufferedWriter(Paths.get(INDEX_FILE))) {
            gson.toJson(indexes, writer);
        } catch (IOException e) {
            System.err.println("Ошибка сохранения индексов: " + e.getMessage());
        }
    }

    // Создание индекса
    public void createIndex(String tableName, String columnName, List<?> data) {
        Map<String, List<Integer>> columnIndex = new HashMap<>();

        // Строим индекс в зависимости от того, к какой таблице он применяется
        if (tableName.equalsIgnoreCase("books")) {
            for (Book b : (List<Book>) data) {
                String value = getBookFieldValue(b, columnName);
                if (value != null) {
                    columnIndex.computeIfAbsent(value, k -> new ArrayList<>()).add(b.getId());
                }
            }
        } else if (tableName.equalsIgnoreCase("authors")) {
            for (Author a : (List<Author>) data) {
                String value = getAuthorFieldValue(a, columnName);
                if (value != null) {
                    columnIndex.computeIfAbsent(value, k -> new ArrayList<>()).add(a.getId());
                }
            }
        } else {
            throw new IllegalArgumentException("Неизвестная таблица: " + tableName);
        }

        // Сохраняем в структуру и на диск
        indexes.computeIfAbsent(tableName, k -> new HashMap<>()).put(columnName, columnIndex);
        saveIndexes();
    }

    // Вспомогательные методы для получения значений полей (приводят всё к строке)
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

    private String getAuthorFieldValue(Author a, String column) {
        switch (column.toLowerCase()) {
            case "name": return a.getName();
            case "isalive": return String.valueOf(a.isAlive());
            case "birthdate": return a.getBirthDate() != null ? a.getBirthDate().toString() : null;
            default: return null;
        }
    }

    // Получение списка ID по индексу
    public List<Integer> searchByIndex(String tableName, String columnName, String value) {
        if (!indexes.containsKey(tableName)) return null;
        if (!indexes.get(tableName).containsKey(columnName)) return null;

        return indexes.get(tableName).get(columnName).getOrDefault(value, new ArrayList<>());
    }

    // Методы для получения ID (нужны для обновления)
    private int getEntityId(String tableName, Object entity) {
        if (tableName.equalsIgnoreCase("books")) return ((Book) entity).getId();
        if (tableName.equalsIgnoreCase("authors")) return ((Author) entity).getId();
        return -1;
    }

    // Получение значения поля в виде строки (для универсальности)
    private String getFieldValue(String tableName, Object entity, String columnName) {
        if (tableName.equalsIgnoreCase("books")) return getBookFieldValue((Book) entity, columnName);
        if (tableName.equalsIgnoreCase("authors")) return getAuthorFieldValue((Author) entity, columnName);
        return null;
    }

    // ВЫЗЫВАТЬ ПРИ INSERT
    public void updateOnInsert(String tableName, Object entity) {
        if (!indexes.containsKey(tableName)) return; // Если индексов для этой таблицы нет - выходим

        int id = getEntityId(tableName, entity);
        for (String column : indexes.get(tableName).keySet()) {
            String value = getFieldValue(tableName, entity, column);
            if (value != null) {
                indexes.get(tableName).get(column)
                        .computeIfAbsent(value, k -> new ArrayList<>()).add(id);
            }
        }
        saveIndexes(); // Сохраняем изменения на диск
    }

    // ВЫЗЫВАТЬ ПРИ DELETE
    public void updateOnDelete(String tableName, Object entity) {
        if (!indexes.containsKey(tableName)) return;

        int id = getEntityId(tableName, entity);
        for (String column : indexes.get(tableName).keySet()) {
            String value = getFieldValue(tableName, entity, column);
            if (value != null) {
                List<Integer> ids = indexes.get(tableName).get(column).get(value);
                if (ids != null) {
                    ids.remove(Integer.valueOf(id)); // Удаляем именно объект Integer, а не по индексу
                    if (ids.isEmpty()) {
                        indexes.get(tableName).get(column).remove(value); // Чистим пустой список
                    }
                }
            }
        }
        saveIndexes();
    }

    // ВЫЗЫВАТЬ ПРИ UPDATE
    public void updateOnUpdate(String tableName, Object oldEntity, Object newEntity) {
        // Самый простой способ: удалить старое значение из индекса и добавить новое
        updateOnDelete(tableName, oldEntity);
        updateOnInsert(tableName, newEntity);
    }
}
