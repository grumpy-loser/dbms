import java.time.LocalDate;

public class Author {
    private int id;              // Целое (int)
    private String name;         // Строка
    private LocalDate birthDate; // Дата
    private boolean isAlive;

    public Author(){}

    public Author(int id, String name, LocalDate birthDate, boolean isAlive){
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.isAlive = isAlive;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean isAlive) { this.isAlive = isAlive; }

    // Метод toString для удобного вывода в консоль (и для отладки)
    @Override
    public String toString() {
        return "Author{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", birthDate=" + birthDate +
                ", isAlive=" + isAlive +
                '}';
    }


}
