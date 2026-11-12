import java.time.LocalDate;

public class Book {
    private int id;              // Целое число (первичный ключ)
    private String title;        // Строка
    private int authorId;        // Целое число (внешний ключ на таблицу authors)
    private double price;        // Число с плавающей точкой (double)
    private LocalDate publishDate; // Дата (используем LocalDate)
    private boolean isAvailable; // Логическое значение (true/false)

    // Пустой конструктор нужен для библиотеки Gson (она использует его при чтении из JSON)
    public Book() {}

    // Конструктор со всеми полями для удобства создания
    public Book(int id, String title, int authorId, double price,
                LocalDate publishDate, boolean isAvailable) {
        this.id = id;
        this.title = title;
        this.authorId = authorId;
        this.price = price;
        this.publishDate = publishDate;
        this.isAvailable = isAvailable;
    }

    // --- ГЕТТЕРЫ И СЕТТЕРЫ ---
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getAuthorId() { return authorId; }
    public void setAuthorId(int authorId) { this.authorId = authorId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public LocalDate getPublishDate() { return publishDate; }
    public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }

    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }

    // Метод toString для удобного вывода в консоль (и для отладки)
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
