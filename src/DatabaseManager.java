import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private RandomAccessFile raf;
    private String currentPath;

    public void open(String path) throws IOException {
        close();
        this.raf = new RandomAccessFile(path, "rw");
        this.currentPath = path;
    }

    public void close() throws IOException {
        if (raf != null) {
            raf.close();
            raf = null;
            currentPath = null;
        }
    }

    public boolean isOpen() {
        return raf != null;
    }

    public void forceSave() throws IOException {
        if (isOpen()) raf.getFD().sync();
    }

    public void deleteDatabaseFile(String path) throws IOException {
        close();
        Files.deleteIfExists(Paths.get(path));
    }

    private long binarySearch(int id) throws IOException {
        long low = 0;
        long high = raf.length() / Book.RECORD_SIZE - 1;
        while (low <= high) {
            long mid = (low + high) / 2;
            raf.seek(mid * Book.RECORD_SIZE);
            int midId = raf.readInt();
            if (midId < id) low = mid + 1;
            else if (midId > id) high = mid - 1;
            else return mid;
        }
        return -(low + 1);
    }

    public void addBook(Book book) throws IOException, IllegalArgumentException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        long index = binarySearch(book.getId());
        if (index >= 0) throw new IllegalArgumentException("Книга с таким ID уже существует!");

        long insertPos = (-index - 1) * Book.RECORD_SIZE;
        long fileLength = raf.length();

        if (insertPos < fileLength) {
            byte[] buffer = new byte[1024];
            long readPos = fileLength - Book.RECORD_SIZE;
            long writePos = fileLength;
            while (readPos >= insertPos) {
                int bytesToRead = (int) Math.min(buffer.length, readPos - insertPos + Book.RECORD_SIZE);
                raf.seek(readPos);
                raf.readFully(buffer, 0, bytesToRead);
                raf.seek(writePos);
                raf.write(buffer, 0, bytesToRead);
                readPos -= bytesToRead;
                writePos -= bytesToRead;
            }
        }
        raf.seek(insertPos);
        book.write(raf);
    }

    public boolean deleteById(int id) throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        long index = binarySearch(id);
        if (index < 0) return false;

        long deletePos = index * Book.RECORD_SIZE;
        long fileLength = raf.length();

        if (deletePos < fileLength - Book.RECORD_SIZE) {
            byte[] buffer = new byte[1024];
            long readPos = deletePos + Book.RECORD_SIZE;
            long writePos = deletePos;
            while (readPos < fileLength) {
                int bytesToRead = (int) Math.min(buffer.length, fileLength - readPos);
                raf.seek(readPos);
                raf.readFully(buffer, 0, bytesToRead);
                raf.seek(writePos);
                raf.write(buffer, 0, bytesToRead);
                readPos += bytesToRead;
                writePos += bytesToRead;
            }
        }
        raf.setLength(fileLength - Book.RECORD_SIZE);
        return true;
    }

    public Book findById(int id) throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        long index = binarySearch(id);
        if (index >= 0) {
            raf.seek(index * Book.RECORD_SIZE);
            return Book.read(raf);
        }
        return null;
    }

    public List<Book> findByField(String field, String value) throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        List<Book> results = new ArrayList<>();
        long records = raf.length() / Book.RECORD_SIZE;
        for (int i = 0; i < records; i++) {
            raf.seek(i * Book.RECORD_SIZE);
            Book b = Book.read(raf);
            if (field.equalsIgnoreCase("автор") && b.getAuthor().equalsIgnoreCase(value)) results.add(b);
            else if (field.equalsIgnoreCase("название") && b.getTitle().equalsIgnoreCase(value)) results.add(b);
            else if (field.equalsIgnoreCase("жанр") && b.getGenre().equalsIgnoreCase(value)) results.add(b);
        }
        return results;
    }

    public void updateBook(Book updatedBook) throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        long index = binarySearch(updatedBook.getId());
        if (index >= 0) {
            raf.seek(index * Book.RECORD_SIZE);
            updatedBook.write(raf);
        }
    }

    public List<Book> loadAllForGUI() throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        List<Book> books = new ArrayList<>();
        long records = raf.length() / Book.RECORD_SIZE;
        for (int i = 0; i < records; i++) {
            raf.seek(i * Book.RECORD_SIZE);
            books.add(Book.read(raf));
        }
        return books;
    }

    public int deleteByField(String field, String value) throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");

        long readPos = 0;
        long writePos = 0;
        long records = raf.length() / Book.RECORD_SIZE;
        int deletedCount = 0;

        for (int i = 0; i < records; i++) {
            raf.seek(readPos);
            Book b = Book.read(raf);

            boolean isMatch = false;
            if (field.equals("автор") && b.getAuthor().equalsIgnoreCase(value)) isMatch = true;
            else if (field.equals("название") && b.getTitle().equalsIgnoreCase(value)) isMatch = true;
            else if (field.equals("жанр") && b.getGenre().equalsIgnoreCase(value)) isMatch = true;

            if (isMatch) {
                deletedCount++;
            } else {
                if (readPos != writePos) {
                    raf.seek(writePos);
                    b.write(raf);
                }
                writePos += Book.RECORD_SIZE;
            }
            readPos += Book.RECORD_SIZE;
        }

        if (deletedCount > 0) {
            raf.setLength(writePos);
        }
        return deletedCount;
    }

    public void rewriteAll(List<Book> books) throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        raf.setLength(0);
        for (Book b : books) {
            b.write(raf);
        }
    }

    public void clearAll() throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        raf.setLength(0);
    }

    public static void createEmptyDB(String path) throws IOException {
        Files.write(Paths.get(path), new byte[0]);
    }

    public static void backup(String path, String backupPath) throws IOException {
        Files.copy(Paths.get(path), Paths.get(backupPath), StandardCopyOption.REPLACE_EXISTING);
    }

    public static void restore(String backupPath, String path) throws IOException {
        Files.copy(Paths.get(backupPath), Paths.get(path), StandardCopyOption.REPLACE_EXISTING);
    }

    public void migrateOldTextFile(String oldTextPath) throws IOException {
        if (!isOpen()) throw new IllegalStateException("БД не открыта!");
        List<Book> oldBooks = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Paths.get(oldTextPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length == 5) {
                    try {
                        int id = Integer.parseInt(parts[0].trim());
                        String title = parts[1].trim();
                        String author = parts[2].trim();
                        String genre = parts[3].trim();
                        int year = Integer.parseInt(parts[4].trim());
                        oldBooks.add(new Book(id, title, author, genre, year));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        oldBooks.sort((b1, b2) -> Integer.compare(b1.getId(), b2.getId()));
        rewriteAll(oldBooks);
    }
}