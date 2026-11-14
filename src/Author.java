import java.time.LocalDate;

/** Represents a {@link Author} object.
 * <p>
 * Initialized by int {@link id}, String {@link name},
 * {@link LocalDate} {@link birthDate}, boolean {@link isAlive}.
 *  <p>
 * Author supports constructors, getters and setters for each field
 * and Override method {@link toString}.
 * */
public class Author {

    /** id of author, primary key. */
    private int id;
    /** Name of the author. */
    private String name;
    /** {@link LocalDate} object to record author's date of birth. */
    private LocalDate birthDate;
    /** Check if the author is alive. */
    private boolean isAlive;

    /** Empty constructor is required when using Gson library. */
    public Author(){}

    /** Constructs Author object.
    *  @param id primary key
    *  @param name name of the author
    *  @param birthDate author's date of birth
    *  @param isAlive check for contemporary relevance
     *  */
    public Author(int id, String name, LocalDate birthDate, boolean isAlive){
        this.id = id;
        this.name = name;
        this.birthDate = birthDate;
        this.isAlive = isAlive;
    }

    /** Returns the id of the author. */
    public int getId() { return id; }
    /** Sets the id of author with given value.
     * @param id the given id
     * */
    public void setId(int id) { this.id = id; }

    /** Returns the name of the author. */
    public String getName() { return name; }
    /** Sets the name of the author with given value.
     * @param name the given name
     * */
    public void setName(String name) { this.name = name; }

    /** Returns the author's date of birth. */
    public LocalDate getBirthDate() { return birthDate; }
    /** Sets the author's date of birth with given value.
     * @param birthDate the given date of birth
     * */
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    /** Returns true if author is a contemporary. */
    public boolean isAlive() { return isAlive; }
    /** Sets the contemporary status of author.
     * @param isAlive the given author status
     * */
    public void setAlive(boolean isAlive) { this.isAlive = isAlive; }

    /** Returns stringified representation of this Author fields. */
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
