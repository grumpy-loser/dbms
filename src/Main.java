import javax.swing.*;

public class Main {
    // Укажи свой путь
    private static final String DB_PATH = "C:\\Projects\\Mariia\\Java\\Data Management\\database_data\\library0.txt";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Передаем путь в конструктор, чтобы БД открылась сразу при запуске
            LibraryGUI gui = new LibraryGUI(DB_PATH);
            gui.setVisible(true);
        });
    }
}