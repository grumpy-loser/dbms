import java.io.IOException;
import java.io.RandomAccessFile;

/// Represents a book on bookshelf in collection.
///
/// Initialized by id in collection, title, author,
/// genre and year of publishing.
///
/// Provides methods to get each field.
///
/// Provides methods [read] and [write] to work with the file
/// and methodss [readFixedString] and [writeFixedString] to
/// transform string to byte format.
public class Book {

    /// ID of book in the collection.
    private int id;
    /// Book title.
    private String title;
    /// Book author.
    private String author;
    /// Book genre.
    private String genre;
    /// Year the book came out.
    private int year;

    /// Fixed lengths of string fields.
    public static final int TITLE_LEN = 100;
    public static final int AUTHOR_LEN = 50;
    public static final int GENRE_LEN = 30;

    /// 4 + (100*2) + (50*2) + (30*2) + 4 = 368 byte
    /// Constant that calculates the length of one book record.
    /// [id] and [year] take 4 as `int`, [title], [author] and [genre]
    /// get their fixed length multipled by 2 as `char` takes 2 bytes.
    public static final int RECORD_SIZE = 4 + (TITLE_LEN * 2) + (AUTHOR_LEN * 2) + (GENRE_LEN * 2) + 4;

    /// Constructs book record with input
    /// id, title, author, genre and year.
    ///
    /// @param id  id of record
    /// @param title  book title
    /// @param author  book author
    /// @param genre book genre
    /// @param year  book year
    public Book(int id, String title, String author, String genre, int year) {
        this.id = id;
        this.title = truncate(title, TITLE_LEN);
        this.author = truncate(author, AUTHOR_LEN);
        this.genre = truncate(genre, GENRE_LEN);
        this.year = year;
    }


    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getGenre() { return genre; }
    public int getYear() { return year; }

    public void setTitle(String title) { this.title = truncate(title, TITLE_LEN); }
    public void setAuthor(String author) { this.author = truncate(author, AUTHOR_LEN); }
    public void setGenre(String genre) { this.genre = truncate(genre, GENRE_LEN); }
    public void setYear(int year) { this.year = year; }

    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    public void write(RandomAccessFile raf) throws IOException {
        raf.writeInt(id);
        writeFixedString(raf, title, TITLE_LEN);
        writeFixedString(raf, author, AUTHOR_LEN);
        writeFixedString(raf, genre, GENRE_LEN);
        raf.writeInt(year);
    }

    public static Book read(RandomAccessFile raf) throws IOException {
        int id = raf.readInt();
        String title = readFixedString(raf, TITLE_LEN);
        String author = readFixedString(raf, AUTHOR_LEN);
        String genre = readFixedString(raf, GENRE_LEN);
        int year = raf.readInt();
        return new Book(id, title, author, genre, year);
    }

    private void writeFixedString(RandomAccessFile raf, String str, int length) throws IOException {
        StringBuilder sb = new StringBuilder(str);
        sb.setLength(length);
        raf.writeChars(sb.toString());
    }

    private static String readFixedString(RandomAccessFile raf, int length) throws IOException {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = raf.readChar();
        }
        return new String(chars).trim();
    }

    @Override
    public String toString() {
        return id + " | " + title + " | " + author + " | " + genre + " | " + year;
    }
}