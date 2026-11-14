import java.time.LocalDate;

/**
 * Represents a {@link Book} object.
 * <p>
 * Initialized by int {@link id}, String {@link title},
 * int {@link authorId}, double {@link price}, {@link LocalDate} {@link publishDate}
 * and boolean {@link isAvailable}.
 * <p>
 * Book supports constructors, getters and setters for each field
 and Override method {@link toString}.
 */
public class Book {

    /** id of book, primary key.*/
    private int id;
    /** Title of the book. */
    private String title;
    /** id of author of the book,
    * foreign key to connect with {@link Author}.*/
    private int authorId;
    /** Price of the book. */
    private double price;
    /** {@link LocalDate} object to record
    * date of book publishing. */
    private LocalDate publishDate;
    /** Check if the book is available. */
    private boolean isAvailable;

    /** Empty constructor is required when using Gson library. */
    public Book() {}

    /** Constructs Book object.
    * @param id primary key
    * @param title title of the book
    * @param authorId foreign key to connect with author
    * @param price price of the book
    * @param publishDate date of the book publication
    * @param isAvailable check for availability
    * */
    public Book(int id, String title, int authorId, double price,
                LocalDate publishDate, boolean isAvailable) {
        this.id = id;
        this.title = title;
        this.authorId = authorId;
        this.price = price;
        this.publishDate = publishDate;
        this.isAvailable = isAvailable;
    }

    /** Returns the id of the book. */
    public int getId() { return id; }
    /** Sets the book id with given value.
    * @param id the given id */
    public void setId(int id) { this.id = id; }

    /** Returns the title of the book. */
    public String getTitle() { return title; }
    /** Sets the book title with given value.
    * @param title the given title */
    public void setTitle(String title) { this.title = title; }

    /** Returns id of author of the book. */
    public int getAuthorId() { return authorId; }
    /** Sets the id of author with given value.
    * @param authorId the given author id
     * */
    public void setAuthorId(int authorId) { this.authorId = authorId; }

    /** Returns the price of the book. */
    public double getPrice() { return price; }
    /** Sets the book price with given value.
    * @param price the given price */
    public void setPrice(double price) { this.price = price; }

    /** Returns the publication date of the book. */
    public LocalDate getPublishDate() { return publishDate; }
    /** Sets the book publication date with given value.
    * @param publishDate the given date of publication */
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }

    /** Returns true if the book is available. */
    public boolean isAvailable() { return isAvailable; }
    /** Sets the book availability status with given value.
    * @param available the given book status */
    public void setAvailable(boolean available) { isAvailable = available; }

    /** Returns stringified representation of this Book fields. */
    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", authorId=" + authorId +
                ", price=" + price +
                ", publishDate=" + publishDate +
                ", isAvailable=" + isAvailable +
                '}';
    }
}
