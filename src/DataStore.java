import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.google.gson.TypeAdapter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DataStore {
    private static final String AUTHORS_FILE = "authors.json";
    private static final String BOOKS_FILE = "books.json";


    private IndexManager indexManager; // Ссылка на менеджер индексов

    public void setIndexManager(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    // Gson с адаптером для LocalDate
    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() { // Убрали com.google.gson
                @Override
                public void write(JsonWriter out, LocalDate value) throws IOException { // Убрали com.google.gson.stream
                    out.value(value == null ? null : value.toString());
                }
                @Override
                public LocalDate read(JsonReader in) throws IOException { // Убрали com.google.gson.stream
                    if (in.peek() == JsonToken.NULL) { // Убрали com.google.gson.stream
                        in.nextNull();
                        return null;
                    }
                    return LocalDate.parse(in.nextString());
                }
            }).setPrettyPrinting().create();

    // Хранилища в оперативной памяти (для GUI и выполнения запросов)
    private List<Author> authors = new ArrayList<>();
    private List<Book> books = new ArrayList<>();

    // Загрузка при запуске
    public void loadAll() {
        this.authors = loadData(AUTHORS_FILE, new TypeToken<List<Author>>(){}.getType());
        this.books = loadData(BOOKS_FILE, new TypeToken<List<Book>>(){}.getType());
        System.out.println("Загружено авторов: " + authors.size() + ", книг: " + books.size());
    }

    // Универсальный метод загрузки
    private <T> List<T> loadData(String fileName, Type type) {
        Path path = Paths.get(fileName);
        if (!Files.exists(path)) return new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(path)) {
            List<T> data = gson.fromJson(reader, type);
            return data != null ? data : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла " + fileName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // Сохранение книг (вызывать после любого изменения в списке books)
    public void saveBooks() {
        saveData(BOOKS_FILE, books);
    }

    // Сохранение авторов (вызывать после любого изменения в списке authors)
    public void saveAuthors() {
        saveData(AUTHORS_FILE, authors);
    }

    // Универсальный метод сохранения с защитой от сбоев (Atomic Move)
    private void saveData(String fileName, Object data) {
        Path path = Paths.get(fileName);
        Path tempPath = Paths.get(fileName + ".tmp");

        try (Writer writer = Files.newBufferedWriter(tempPath)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            System.err.println("Ошибка записи во временный файл: " + e.getMessage());
            return;
        }

        try {
            // Пытаемся сделать атомарную замену
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                // Если ОС не поддерживает атомарный move, делаем обычную замену
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("Критическая ошибка сохранения файла: " + ex.getMessage());
            }
        }
    }

    // --- ГЕТТЕРЫ ДЛЯ ДОСТУПА К ДАННЫМ (Будут использовать коллеги) ---
    public List<Author> getAuthors() { return authors; }
    public List<Book> getBooks() { return books; }

    // ==================== МЕТОДЫ ДЛЯ РАБОТЫ С КНИГАМИ ====================

    public void addBook(Book book) {
        this.books.add(book);
        saveBooks();

        // Если менеджер индексов подключен, обновляем его
        if (indexManager != null) {
            indexManager.updateOnInsert("books", book);
        }
    }

    public boolean deleteBookById(int id) {
        Book bookToDelete = findBookById(id); // Сначала находим книгу
        boolean removed = this.books.removeIf(b -> b.getId() == id);

        if (removed) {
            saveBooks();
            // Если менеджер индексов подключен, обновляем его
            if (indexManager != null && bookToDelete != null) {
                indexManager.updateOnDelete("books", bookToDelete);
            }
        }
        return removed;
    }

    public void updateBook(Book updatedBook) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).getId() == updatedBook.getId()) {
                Book oldBook = books.get(i); // Запоминаем старую версию до замены
                books.set(i, updatedBook);
                saveBooks();

                // Если менеджер индексов подключен, обновляем его
                if (indexManager != null) {
                    indexManager.updateOnUpdate("books", oldBook, updatedBook);
                }
                return;
            }
        }
    }

    public Book findBookById(int id) {
        for (Book b : books) {
            if (b.getId() == id) {
                return b;
            }
        }
        return null;
    }


    public void addAuthor(Author author) {
        this.authors.add(author);
        saveAuthors();
        if (indexManager != null) {
            indexManager.updateOnInsert("authors", author);
        }
    }

    public boolean deleteAuthorById(int id) {
        boolean hasBooks = books.stream().anyMatch(b -> b.getAuthorId() == id);
        if (hasBooks) {
            System.err.println("Нельзя удалить автора: у него есть книги!");
            return false;
        }

        Author authorToDelete = findAuthorById(id); // Находим перед удалением
        boolean removed = this.authors.removeIf(a -> a.getId() == id);
        if (removed) {
            saveAuthors();
            if (indexManager != null && authorToDelete != null) {
                indexManager.updateOnDelete("authors", authorToDelete);
            }
        }
        return removed;
    }

    // НОВЫЙ МЕТОД ДЛЯ ОБНОВЛЕНИЯ АВТОРА
    public void updateAuthor(Author updatedAuthor) {
        for (int i = 0; i < authors.size(); i++) {
            if (authors.get(i).getId() == updatedAuthor.getId()) {
                Author oldAuthor = authors.get(i); // Запоминаем старую версию
                authors.set(i, updatedAuthor);
                saveAuthors();

                if (indexManager != null) {
                    indexManager.updateOnUpdate("authors", oldAuthor, updatedAuthor);
                }
                return;
            }
        }
    }

    public Author findAuthorById(int id) {
        for (Author a : authors) {
            if (a.getId() == id) {
                return a;
            }
        }
        return null;
    }
}
