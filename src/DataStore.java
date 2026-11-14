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

/** DataStore stores the data about Book and Author objects.
 *  <p>
 *  DataStore turns Book and Author objects into JSON objects
 *  to store and load data.
 *  <p>
 *  Configures Gson instance to save data as JSON objects.
 *  <p>
 *  For {@link Book} and {@link Author} creates lists that contain them,
 *  has methods to add, delete by id and find by id, update, save them
 *  to the disc and clear the lists.
 *  <p>
 *  Uses {@link IndexManager} to keep indexes synchronized with data updates.
 * */
public class DataStore {

    /** Filename for storing collection of authors. */
    private static final String AUTHORS_FILE = "authors.json";
    /** Filename for storing collection of books. */
    private static final String BOOKS_FILE = "books.json";

    /** IndexManager is used to keep indexes synchronized with data changes */
    private IndexManager indexManager;

    /** Sets IndexManager with given one to keep indexes synchronuzed.
     * @param indexManager the given index manager
     * */
    public void setIndexManager(IndexManager indexManager) {
        this.indexManager = indexManager;
    }

    /**
     * Configures Gson instance.
     * <p>
     * Has a custom TypeAdapter to properly serialize {@link LocalDate} objects
     * (format: YYYY-MM-DD).
     * */
    private Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() {

                /** Override function to write LocalDate as JSON encoded object.*/
                @Override
                public void write(JsonWriter out, LocalDate value) throws IOException {
                    out.value(value == null ? null : value.toString());
                }
                /** Override function to write LocalDate as JSON encoded object.*/
                @Override
                public LocalDate read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return LocalDate.parse(in.nextString());
                }
            }).setPrettyPrinting().create();

    /** Storage {@link List} for authors.*/
    private List<Author> authors = new ArrayList<>();
    /** Storage {@link List} for books.*/
    private List<Book> books = new ArrayList<>();

    /** Loads all authors and books from their JSON files into memory.
     * <p>
     * Called only once at the start.
     * */
    public void loadAll() {
        this.authors = loadData(AUTHORS_FILE, new TypeToken<List<Author>>(){}.getType());
        this.books = loadData(BOOKS_FILE, new TypeToken<List<Book>>(){}.getType());
        System.out.println("Загружено авторов: " + authors.size() + ", книг: " + books.size());
    }

    /**
     * Helper method to load data from a JSON file.
     * <p>
     * @param <T> the type of objects in the List
     * @param fileName name of file to read
     * @param type type of data in the list
     * */
    private <T> List<T> loadData(String fileName, Type type) {
        Path path = Paths.get(fileName);
        if (!Files.exists(path)) return new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(path)) {
            List<T> data = gson.fromJson(reader, type);
            return data != null ? data : new ArrayList<>();
        }
        catch (IOException e) {
            System.err.println("Ошибка чтения файла " + fileName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Saves the current authors list to the disk.
     * */
    public void saveAuthors() {
        saveData(AUTHORS_FILE, authors);
    }

    /** Saves the current books list to the disk.
     * */
    public void saveBooks() {
        saveData(BOOKS_FILE, books);
    }

   /**
    * Helper method to save data to the disk.
    * <p>
    * It writes to a temporary file first, then attempts atomic move
    * to prevent data corruption.
    *
    * @param fileName the destination file name
    * @param data the object to serialize and save.
    * */
    private void saveData(String fileName, Object data) {
        Path path = Paths.get(fileName);
        Path tempPath = Paths.get(fileName + ".tmp");

        try (Writer writer = Files.newBufferedWriter(tempPath)) {
            gson.toJson(data, writer);
        }
        catch (IOException e) {
            System.err.println("Ошибка записи во временный файл: " + e.getMessage());
            return;
        }

        try {
            /** Attempts atomic move. */
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e) {
            try {
                /** Fallback for OS tha does not support atomic move. */
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("Критическая ошибка сохранения файла: " + ex.getMessage());
            }
        }
    }

    /** Returns list of authors. */
    public List<Author> getAuthors() { return authors; }
    /** Returns list of books. */
    public List<Book> getBooks() { return books; }


    // Working with Authors.

    /** Adds new author to the list and saves it to the disc.
     * <p>
     * Updates indexes if {@link IndexManager} is set.
     * @param author the author to add
     * */
    public void addAuthor(Author author) {
        this.authors.add(author);
        saveAuthors();
        if (indexManager != null) {
            indexManager.updateOnInsert("authors", author);
        }
    }

    /** Deletes author by id.
     * <p>
     *  Uses {@link findAuthorById} to find author, deletes them,
     *  saves to the disc.
     *  <p>
     *  Checks if the author has book - then can not delete them.
     *  <p>
     *  Updates indexes if {@link IndexManager} is set.
     *  @param id id of author to delete
     *  @return true if the author was successfully deleted
     *  */
    public boolean deleteAuthorById(int id) {
        boolean hasBooks = books.stream().anyMatch(b -> b.getAuthorId() == id);
        if (hasBooks) {
            System.err.println("Нельзя удалить автора: у него есть книги!");
            return false;
        }

        Author authorToDelete = findAuthorById(id);
        boolean removed = this.authors.removeIf(a -> a.getId() == id);
        if (removed) {
            saveAuthors();
            if (indexManager != null && authorToDelete != null) {
                indexManager.updateOnDelete("authors", authorToDelete);
            }
        }
        return removed;
    }

    /** Updates an existing author, saves changes to the disc
     *  and updates indexes if {@link IndexManager} is set.
     *  @param updatedAuthor the author with updated fields (matched by id)
     * */
    public void updateAuthor(Author updatedAuthor) {
        for (int i = 0; i < authors.size(); i++) {
            if (authors.get(i).getId() == updatedAuthor.getId()) {
                Author oldAuthor = authors.get(i);
                authors.set(i, updatedAuthor);
                saveAuthors();

                if (indexManager != null) {
                    indexManager.updateOnUpdate("authors", oldAuthor, updatedAuthor);
                }
                return;
            }
        }
    }


    /** Finds author by id.
     * @param id the id of author to find.
     * */
    public Author findAuthorById(int id) {
        for (Author a : authors) {
            if (a.getId() == id) {
                return a;
            }
        }
        return null;
    }

    /**
     * Clears all authors from the storage and saves the empty state to disk.
     * Used primarily by the DROP TABLE or CREATE TABLE commands.
     */
    public void clearAuthors() {
        this.authors.clear();
        saveAuthors();
    }

    // Working with Books

    /** Adds new book to the list and saves it to the disc.
     * <p>
     * Updates indexes if {@link IndexManager} is set.
     * @param book the book to add
     * */
    public void addBook(Book book) {
        this.books.add(book);
        saveBooks();

        if (indexManager != null) {
            indexManager.updateOnInsert("books", book);
        }
    }

    /** Deletes book by id.
     * <p>
     *  Uses {@link findBookById} to find book, deletes it,
     *  saves to the disc.
     *  <p>
     *  Updates indexes if {@link IndexManager} is set.
     *  @param id id of book to delete
     *  @return true if the book was successfully deleted
     *  */
    public boolean deleteBookById(int id) {
        Book bookToDelete = findBookById(id);
        boolean removed = this.books.removeIf(b -> b.getId() == id);

        if (removed) {
            saveBooks();
            if (indexManager != null && bookToDelete != null) {
                indexManager.updateOnDelete("books", bookToDelete);
            }
        }
        return removed;
    }

    /** Updates an existing book, saves changes to the disc
     *  and updates indexes if {@link IndexManager} is set.
     *  @param updatedBook the book with updated fields (matched by id)
     * */
    public void updateBook(Book updatedBook) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).getId() == updatedBook.getId()) {
                Book oldBook = books.get(i);
                books.set(i, updatedBook);
                saveBooks();

                if (indexManager != null) {
                    indexManager.updateOnUpdate("books", oldBook, updatedBook);
                }
                return;
            }
        }
    }

    /** Finds book by id.
     * @param id the id of book to find.
     * */
    public Book findBookById(int id) {
        for (Book b : books) {
            if (b.getId() == id) {
                return b;
            }
        }
        return null;
    }

    /**
     * Clears all books from the storage and saves the empty state to disk.
     * Used primarily by the DROP TABLE or CREATE TABLE commands.
     */
    public void clearBooks() {
        this.books.clear();
        saveBooks();
    }

}
