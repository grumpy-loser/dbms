import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Запускаем GUI в потоке диспетчеризации событий Swing
        SwingUtilities.invokeLater(() -> {
            try {
                // Установка нативного стиля интерфейса (Windows/macOS/Linux)
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            DatabaseGUI gui = new DatabaseGUI();
            gui.setVisible(true);
        });
    }
}
