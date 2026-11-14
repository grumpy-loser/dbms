import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatabaseGUI extends JFrame {

    private final QueryExecutor executor;
    /// link on backend
    private JTextArea queryInput; ///multiline input field
    private JTable resultTable; /// table for result output
    private DefaultTableModel tableModel; ///
    private JTextArea statusArea; ///print status

    public DatabaseGUI() {
        setTitle("Упрощённая СУБД (Лабораторная работа 2)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null); ///places window in center
        setLayout(new BorderLayout(10, 10));

        // 1. Инициализация бэкенда (как в Main)
        try {
            IndexManager im = new IndexManager();
            im.loadIndexes(); ///Reads the indexes.json from disk using Gson and restores hash tables to RAM.

            DataStore ds = new DataStore(); ///A repository that stores List<Book> and List<Author> in memory and syncs them with books.json and authors.json.
            ds.setIndexManager(im); /// passing the link to the IndexManager to the DataStore. DataStore adds data using insert and runs the IndexManager
            ds.loadAll(); ///Reads JSON files of authors and books when the program starts

            this.executor = new QueryExecutor(ds, im);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Ошибка инициализации БД: " + e.getMessage(), "Критическая ошибка", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        // 2. Верхняя панель: Ввод запроса и кнопка
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("SQL-запрос"));

        queryInput = new JTextArea(5, 50);
        queryInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        queryInput.setText("SELECT * FROM books;");
        JScrollPane queryScroll = new JScrollPane(queryInput); ///adds scroll bars
        topPanel.add(queryScroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton executeBtn = new JButton("Выполнить (F5)");
        executeBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        executeBtn.addActionListener(e -> executeQuery()); /// launching the {executeQuery()} method"

        // Горячая клавиша F5
        executeBtn.registerKeyboardAction(e -> executeQuery(),
                KeyStroke.getKeyStroke("F5"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        buttonPanel.add(executeBtn);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);


        tableModel = new DefaultTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        resultTable.setRowHeight(25);
        resultTable.getTableHeader().setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        JScrollPane tableScroll = new JScrollPane(resultTable); /// // Wrapping the table in a JScrollPane so that scrolling appears
        tableScroll.setBorder(BorderFactory.createTitledBorder("Результат"));
        add(tableScroll, BorderLayout.CENTER);


        statusArea = new JTextArea(3, 50);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        statusArea.setEditable(false); /// WE FORBID the user to type in this field
        statusArea.setBackground(new Color(240, 240, 240));
        statusArea.setBorder(BorderFactory.createTitledBorder("Статус"));
        add(new JScrollPane(statusArea), BorderLayout.SOUTH);

        /// calling up Query examples for convenience
        addExampleQueries();
    }

    private void addExampleQueries() {
        String examples = " Примеры запросов:\n" +
                " INSERT INTO authors (id, name, birthDate, isAlive) VALUES (1, 'Толстой', '1828-09-09', FALSE);\n" +
                " INSERT INTO books (id, title, authorId, price, publishDate, isAvailable) VALUES (1, 'Война и мир', 1, 1500.0, '1869-01-01', TRUE);\n" +
                " CREATE INDEX idx_books_title ON books (title);\n" +
                " SELECT title, price FROM books WHERE price > 1000;\n" +
                " UPDATE books SET price = 2000 WHERE id = 1;\n" +
                " DELETE FROM books WHERE id = 1;";
        queryInput.setText(examples + "\n\nSELECT * FROM books;");
    }

    private void executeQuery() { ///We take the text from the input field and remove the spaces around the edges using trim()
        String sql = queryInput.getText().trim();
        if (sql.isEmpty()) {
            setStatus("Введите запрос!", Color.RED);
            return;
        }

        setStatus("Выполнение...", Color.BLUE);

        try {
            List<Map<String, Object>> results = executor.execute(sql);
            updateTable(results);
            setStatus("Запрос выполнен успешно. Строк затрагивает/возвращено: " + results.size(), Color.GREEN.darker());
        } catch (SqlParseException e) {
            setStatus("Ошибка парсинга SQL:\n" + e.getMessage(), Color.RED); /// error output in red
            clearTable();
        } catch (Exception e) {
            setStatus("Ошибка выполнения:\n" + e.getMessage(), Color.RED); /// error output in red
            clearTable();
        }
    }

    private void updateTable(List<Map<String, Object>> results) { /// {results} is the list of rows returned by [QueryExecutor]. Each row is a [Map], where the keys are the column names and the values are the data.
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0); ///completely cleans up the old table.

        if (results == null || results.isEmpty()) {
            setStatus("Запрос выполнен, но данных нет (или операция не возвращает строки).", Color.ORANGE);
            return;
        }

        // Формируем заголовки колонок из ключей первой Map
        /// Takes the first row (results.get(0)), gets its keys (keySet())
        Map<String, Object> firstRow = results.get(0);
        List<String> columns = new ArrayList<>(firstRow.keySet());

        for (String col : columns) {
            tableModel.addColumn(col); ///adds keys as column headers of the table (TableModel.addColumn).
        }

        // Заполняем данные
        for (Map<String, Object> row : results) { /// Looping through all the rows in results. For each row, an array of Object[] [rowData].
            Object[] rowData = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                Object val = row.get(columns.get(i));
                // Преобразуем LocalDate в строку для красивого отображения в таблице

                if (val instanceof java.time.LocalDate) {
                    val = val.toString(); ///converting the date to a string to make it easier to read
                }
                rowData[i] = val;
            }
            tableModel.addRow(rowData);
        }
    }
    /// clears the table
    private void clearTable() {
        tableModel.setRowCount(0);
        tableModel.setColumnCount(0);
    }
    /// for forwarding the status
    private void setStatus(String message, Color color) {
        statusArea.setForeground(color);
        statusArea.setText(message);
    }

    public static void main(String[] args) { /// всё то же самое как и для main
        // Установка системного вида (look and feel) для красоты
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            DatabaseGUI gui = new DatabaseGUI();
            gui.setVisible(true);
        });
    }
}
