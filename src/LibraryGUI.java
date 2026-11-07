import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class LibraryGUI extends JFrame {
    private DatabaseManager dbManager;
    private String dbPath;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField txtId, txtTitle, txtAuthor, txtGenre, txtYear, txtSearchValue;
    private JComboBox<String> cmbSearchField;
    private JLabel lblStatus;

    public LibraryGUI(String defaultDbPath) {
        this.dbManager = new DatabaseManager();
        initUI();
        if (defaultDbPath != null && !defaultDbPath.isEmpty()) {
            try {
                dbManager.open(defaultDbPath);
                this.dbPath = defaultDbPath;
                loadData();
                lblStatus.setText("Открыта БД: " + defaultDbPath);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Не удалось открыть файл по умолчанию: " + e.getMessage());
            }
        }
    }

    private void initUI() {
        setTitle("Файловая База Данных - Библиотека");
        setSize(1000, 650);
        setLocationRelativeTo(null);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                try {
                    dbManager.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                System.exit(0);
            }
        });

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("Файл");

        JMenuItem itemCreate = new JMenuItem("Создать новую БД...");
        JMenuItem itemDeleteDB = new JMenuItem("Удалить файл БД...");
        JMenuItem itemOpen = new JMenuItem("Открыть существующую БД...");
        JMenuItem itemClose = new JMenuItem("Закрыть БД");
        JMenuItem itemSave = new JMenuItem("Сохранить");
        JMenuItem itemMigrate = new JMenuItem("Конвертировать старую БД");

        fileMenu.add(itemCreate);
        fileMenu.add(itemDeleteDB);
        fileMenu.add(itemOpen);
        fileMenu.add(itemClose);
        fileMenu.add(itemSave);
        fileMenu.addSeparator();
        fileMenu.add(itemMigrate);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(mainPanel);

        JPanel inputPanel = new JPanel(new GridLayout(2, 5, 10, 10));
        String[] labels = {"ID (Ключ):", "Название:", "Автор:", "Жанр:", "Год:"};
        JTextField[] fields = new JTextField[5];

        txtId = new JTextField(); fields[0] = txtId;
        txtTitle = new JTextField(); fields[1] = txtTitle;
        txtAuthor = new JTextField(); fields[2] = txtAuthor;
        txtGenre = new JTextField(); fields[3] = txtGenre;
        txtYear = new JTextField(); fields[4] = txtYear;

        for (int i = 0; i < 5; i++) inputPanel.add(new JLabel(labels[i]));
        for (int i = 0; i < 5; i++) inputPanel.add(fields[i]);
        mainPanel.add(inputPanel, BorderLayout.NORTH);

        String[] columnNames = {"ID", "Название", "Автор", "Жанр", "Год"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(25);
        mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        JButton btnAdd = new JButton("Добавить");
        JButton btnDeleteById = new JButton("Удалить по ID");
        JButton btnEdit = new JButton("Редактировать");
        JButton btnClear = new JButton("Очистить БД");
        JButton btnBackup = new JButton("Бэкап");
        JButton btnRestore = new JButton("Восстановить");

        controlPanel.add(btnAdd);
        controlPanel.add(btnDeleteById);
        controlPanel.add(btnEdit);
        controlPanel.add(btnClear);
        controlPanel.add(btnBackup);
        controlPanel.add(btnRestore);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        cmbSearchField = new JComboBox<>(new String[]{"Название", "Автор", "Жанр"});
        txtSearchValue = new JTextField(15);
        JButton btnSearch = new JButton("Найти");
        JButton btnDeleteByField = new JButton("Удалить найденные");
        JButton btnRefresh = new JButton("Показать все");

        searchPanel.add(new JLabel("Поле:"));
        searchPanel.add(cmbSearchField);
        searchPanel.add(new JLabel("Значение:"));
        searchPanel.add(txtSearchValue);
        searchPanel.add(btnSearch);
        searchPanel.add(btnDeleteByField);
        searchPanel.add(btnRefresh);

        JPanel buttonsContainer = new JPanel(new GridLayout(2, 1));
        buttonsContainer.add(controlPanel);
        buttonsContainer.add(searchPanel);
        bottomPanel.add(buttonsContainer, BorderLayout.CENTER);

        lblStatus = new JLabel("Готов к работе. Файл: " + dbPath);
        lblStatus.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        bottomPanel.add(lblStatus, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // --- ЛОГИКА МЕНЮ (JFileChooser) ---

        itemCreate.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Создать новую базу данных");
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                String newPath = chooser.getSelectedFile().getAbsolutePath();
                try {
                    DatabaseManager.createEmptyDB(newPath);
                    dbManager.open(newPath); // ВАЖНО
                    dbPath = newPath;
                    loadData();
                    lblStatus.setText("Создана новая БД: " + dbPath);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Ошибка создания файла!");
                }
            }
        });

        itemDeleteDB.addActionListener(e -> {
            if (dbPath == null) return;
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Вы уверены, что хотите УДАЛИТЬ файл БД с диска?", "Удаление", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                try {
                    dbManager.deleteDatabaseFile(dbPath);
                    tableModel.setRowCount(0); // Очищаем таблицу
                    dbPath = null;
                    lblStatus.setText("Файл БД удален.");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Ошибка удаления файла!");
                }
            }
        });

        itemOpen.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Открыть базу данных");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String newPath = chooser.getSelectedFile().getAbsolutePath();
                try {
                    dbManager.open(newPath); // ВАЖНО
                    dbPath = newPath;
                    loadData();
                    lblStatus.setText("Открыта БД: " + dbPath);
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Ошибка открытия файла!");
                }
            }
        });

        itemClose.addActionListener(e -> {
            try {
                dbManager.close();
                tableModel.setRowCount(0);
                dbPath = null;
                lblStatus.setText("БД закрыта. Выберите файл для работы.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Ошибка при закрытии файла!");
            }
        });

        itemSave.addActionListener(e -> {
            if (!dbManager.isOpen()) return;
            try {
                dbManager.forceSave();
                lblStatus.setText("Данные принудительно сохранены на диск.");
                JOptionPane.showMessageDialog(this, "Все данные синхронизированы с диском.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Ошибка сохранения!");
            }
        });

        itemMigrate.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Выберите старый ТЕКСТОВЫЙ файл БД");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String oldPath = chooser.getSelectedFile().getAbsolutePath();

                JFileChooser saveChooser = new JFileChooser();
                saveChooser.setDialogTitle("Куда сохранить новую БИНАРНУЮ БД?");
                if (saveChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    String newPath = saveChooser.getSelectedFile().getAbsolutePath();
                    try {
                        DatabaseManager.createEmptyDB(newPath);
                        dbManager.open(newPath);
                        dbPath = newPath;
                        dbManager.migrateOldTextFile(oldPath);
                        loadData();
                        JOptionPane.showMessageDialog(this, "Конвертация успешна!");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Ошибка: " + ex.getMessage());
                    }
                }
            }
        });

        btnAdd.addActionListener(e -> addBook());
        btnDeleteById.addActionListener(e -> deleteBookById());
        btnEdit.addActionListener(e -> editBook());
        btnRefresh.addActionListener(e -> loadData());
        btnClear.addActionListener(e -> clearDB());
        btnBackup.addActionListener(e -> backupDB());
        btnRestore.addActionListener(e -> restoreDB());

        btnSearch.addActionListener(e -> searchByField());
        btnDeleteByField.addActionListener(e -> deleteByField());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() != -1) {
                int row = table.getSelectedRow();
                txtId.setText(tableModel.getValueAt(row, 0).toString());
                txtTitle.setText(tableModel.getValueAt(row, 1).toString());
                txtAuthor.setText(tableModel.getValueAt(row, 2).toString());
                txtGenre.setText(tableModel.getValueAt(row, 3).toString());
                txtYear.setText(tableModel.getValueAt(row, 4).toString());
            }
        });
    }

    private void loadData() {
        if (!dbManager.isOpen()) {
            lblStatus.setText("Ошибка: База данных не открыта!");
            return;
        }
        try {
            List<Book> books = dbManager.loadAllForGUI();
            updateTable(books);
            lblStatus.setText("Загружено записей: " + books.size());
        } catch (IOException e) {
            lblStatus.setText("Ошибка чтения файла: " + e.getMessage());
        }
    }

    private void updateTable(List<Book> books) {
        tableModel.setRowCount(0);
        for (Book b : books) {
            tableModel.addRow(new Object[]{b.getId(), b.getTitle(), b.getAuthor(), b.getGenre(), b.getYear()});
        }
    }

    private void addBook() {
        if (!dbManager.isOpen()) {
            JOptionPane.showMessageDialog(this, "Сначала создайте или откройте БД!");
            return;
        }
        try {
            int id = Integer.parseInt(txtId.getText());
            String title = txtTitle.getText();
            String author = txtAuthor.getText();
            String genre = txtGenre.getText();
            int year = Integer.parseInt(txtYear.getText());

            Book book = new Book(id, title, author, genre, year);
            dbManager.addBook(book);
            loadData();
            clearFields();
            lblStatus.setText("Книга успешно добавлена!");
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "ID и Год должны быть числами!", "Ошибка ввода", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Ошибка", JOptionPane.WARNING_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка записи файла!", "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteBookById() {
        if (!dbManager.isOpen()) {
            JOptionPane.showMessageDialog(this, "Сначала создайте или откройте БД!");
            return;
        }
        String idStr = txtId.getText().isEmpty() ? JOptionPane.showInputDialog("Введите ID для удаления:") : txtId.getText();
        if (idStr == null || idStr.isEmpty()) return;
        try {
            int id = Integer.parseInt(idStr);
            if (dbManager.deleteById(id)) {
                loadData();
                clearFields();
                lblStatus.setText("Запись удалена.");
            } else {
                JOptionPane.showMessageDialog(this, "Запись с таким ID не найдена.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Ошибка удаления: " + e.getMessage());
        }
    }

    private void editBook() {
        try {
            if (!dbManager.isOpen()) {
                JOptionPane.showMessageDialog(this, "Сначала создайте или откройте БД!");
                return;
            }
            int id = Integer.parseInt(txtId.getText());
            Book updatedBook = new Book(id, txtTitle.getText(), txtAuthor.getText(), txtGenre.getText(), Integer.parseInt(txtYear.getText()));
            dbManager.updateBook(updatedBook);
            loadData();
            lblStatus.setText("Запись обновлена.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Выберите запись или проверьте ввод!");
        }
    }

    private void searchByField() {
        if (!dbManager.isOpen()) {
            JOptionPane.showMessageDialog(this, "Сначала создайте или откройте БД!");
            return;
        }
        try {
            String field = cmbSearchField.getSelectedItem().toString().toLowerCase();
            String value = txtSearchValue.getText();
            List<Book> results = dbManager.findByField(field, value);
            updateTable(results);
            lblStatus.setText("Найдено записей: " + results.size());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка поиска.");
        }
    }

    private void deleteByField() {
        if (!dbManager.isOpen()) {
            JOptionPane.showMessageDialog(this, "Сначала создайте или откройте БД!");
            return;
        }
        String field = cmbSearchField.getSelectedItem().toString().toLowerCase();
        String value = txtSearchValue.getText();
        if (value.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Введите значение для удаления!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Удалить ВСЕ записи, где " + field + " = " + value + "?",
                "Подтверждение", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                int deleted = dbManager.deleteByField(field, value);
                loadData();
                lblStatus.setText("Удалено записей: " + deleted);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Ошибка удаления.");
            }
        }
    }

    private void clearDB() {
        if (!dbManager.isOpen()) {
            JOptionPane.showMessageDialog(this, "Сначала создайте или откройте БД!");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Вы уверены, что хотите удалить ВСЕ данные?", "Очистка", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                dbManager.clearAll(); // ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД
                loadData();
                lblStatus.setText("База данных очищена.");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Ошибка очистки.");
            }
        }
    }

    private void backupDB() {
        if (dbPath == null) return;
        String backupPath = dbPath + ".backup";
        try {
            DatabaseManager.backup(dbPath, backupPath);
            lblStatus.setText("Бэкап создан: " + backupPath);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка создания бэкапа.");
        }
    }

    private void restoreDB() {
        if (dbPath == null) return;
        String backupPath = dbPath + ".backup";
        try {
            dbManager.close(); // Закрываем, чтобы скопировать файл
            DatabaseManager.restore(backupPath, dbPath);
            dbManager.open(dbPath); // Открываем заново
            loadData();
            lblStatus.setText("БД восстановлена из бэкапа.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Ошибка восстановления: " + e.getMessage());
        }
    }

    private void clearFields() {
        txtId.setText("");
        txtTitle.setText("");
        txtAuthor.setText("");
        txtGenre.setText("");
        txtYear.setText("");
        txtSearchValue.setText("");
        txtId.requestFocus();
    }
}