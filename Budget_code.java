import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;

public class BudgetMiniProject {


    private static final String INCOME_FILE = "income.txt";
    private static final String EXPENSE_FILE = "expense.txt";
    private static final String BUDGET_FILE = "budget.txt";

    // Date format
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");

    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JTextField tfUser;
    private JPasswordField pfPass;

    private JLabel lblWelcome;
    private JLabel lblTotalIncome;
    private JLabel lblTotalExpense;
    private JLabel lblRemaining;
    private JLabel lblBudget;

    private DefaultTableModel tableModel;
    private JTable transactionTable;

    private JComboBox<String> cbSearchCategory;
    private JTextField tfSearchDate; 
    private static final String[] EXP_CATEGORIES = {"Food", "Transport", "Rent", "Bills", "Shopping", "Entertainment", "Other"};


    private List<Income> incomes = new ArrayList<>();
    private List<Expense> expenses = new ArrayList<>();
    private double budgetGoal = 0.0;

    private static final String DEFAULT_USER = "user";
    private static final String DEFAULT_PASS = "1234";

    // Threshold for low-budget notification (10%)
    private static final double LOW_BUDGET_THRESHOLD = 0.10;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new BudgetMiniProject().start();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Fatal error: " + e.getMessage());
            }
        });
    }

    private void start() {
        ensureFilesExist();
        loadAllData();
        buildGui();
    }

    // Ensure files exist (create empty if not)
    private void ensureFilesExist() {
        try {
            Path p1 = Paths.get(INCOME_FILE);
            Path p2 = Paths.get(EXPENSE_FILE);
            Path p3 = Paths.get(BUDGET_FILE);
            if (!Files.exists(p1)) Files.createFile(p1);
            if (!Files.exists(p2)) Files.createFile(p2);
            if (!Files.exists(p3)) Files.createFile(p3);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error creating files: " + e.getMessage());
        }
    }

    private void buildGui() {
        frame = new JFrame("Personal Budget Manager - Mini Project");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(buildLoginPanel(), "LOGIN");
        mainPanel.add(buildAppPanel(), "APP");

        frame.setContentPane(mainPanel);
        cardLayout.show(mainPanel, "LOGIN");
        frame.setVisible(true);
    }

    // -------------------- Login Panel --------------------
    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        JLabel title = new JLabel("Personal Budget Manager");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));

        JLabel lblUser = new JLabel("Username:");
        tfUser = new JTextField(15);
        tfUser.setText(DEFAULT_USER);

        JLabel lblPass = new JLabel("Password:");
        pfPass = new JPasswordField(15);
        pfPass.setText(DEFAULT_PASS);

        JButton btnLogin = new JButton("Login");
        JButton btnExit = new JButton("Exit");

        c.insets = new Insets(8, 8, 8, 8);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        p.add(title, c);

        c.gridwidth = 1; c.gridy++;
        c.gridx = 0; p.add(lblUser, c);
        c.gridx = 1; p.add(tfUser, c);

        c.gridy++; c.gridx = 0; p.add(lblPass, c);
        c.gridx = 1; p.add(pfPass, c);

        c.gridy++; c.gridx = 0; p.add(btnLogin, c);
        c.gridx = 1; p.add(btnExit, c);

        btnLogin.addActionListener(e -> doLogin());
        btnExit.addActionListener(e -> System.exit(0));

        return p;
    }

    private void doLogin() {
        String user = tfUser.getText().trim();
        String pass = new String(pfPass.getPassword());
        if (user.equals(DEFAULT_USER) && pass.equals(DEFAULT_PASS)) {
            // proceed to app
            lblWelcome.setText("Welcome, " + user + "!");
            refreshSummaryLabels();
            refreshTransactionTable(null);
            cardLayout.show(mainPanel, "APP");
            checkLowBudgetAndNotify();
        } else {
            JOptionPane.showMessageDialog(frame, "Invalid credentials. Use user/1234", "Login failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------------------- Main Application Panel --------------------
    private JPanel buildAppPanel() {
        JPanel p = new JPanel(new BorderLayout());

        // Top: toolbar
        JPanel top = new JPanel(new BorderLayout());
        lblWelcome = new JLabel("Welcome!");
        lblWelcome.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        top.add(lblWelcome, BorderLayout.WEST);

        JPanel buttons = new JPanel();
        JButton btnAddIncome = new JButton("Add Income");
        JButton btnAddExpense = new JButton("Add Expense");
        JButton btnSetBudget = new JButton("Set Budget");
        JButton btnSummary = new JButton("Summary");
        JButton btnPie = new JButton("Pie Chart");
        JButton btnLogout = new JButton("Logout");

        buttons.add(btnAddIncome);
        buttons.add(btnAddExpense);
        buttons.add(btnSetBudget);
        buttons.add(btnSummary);
        buttons.add(btnPie);
        buttons.add(btnLogout);

        top.add(buttons, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);


        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.65);

        JPanel left = new JPanel(new BorderLayout());
        tableModel = new DefaultTableModel(new String[]{"Type", "Date", "Amount", "Category/Source", "Note"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        transactionTable = new JTable(tableModel);
        left.add(new JScrollPane(transactionTable), BorderLayout.CENTER);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search / Filter"));
        tfSearchDate = new JTextField(10);
        cbSearchCategory = new JComboBox<>();
        cbSearchCategory.addItem("Any");
        for (String s : EXP_CATEGORIES) cbSearchCategory.addItem(s);
        JButton btnSearch = new JButton("Search");
        JButton btnReset = new JButton("Reset");
        JButton btnDelete = new JButton("Delete Selected");

        searchPanel.add(new JLabel("Date (yyyy-mm-dd):"));
        searchPanel.add(tfSearchDate);
        searchPanel.add(new JLabel("Category:"));
        searchPanel.add(cbSearchCategory);
        searchPanel.add(btnSearch);
        searchPanel.add(btnReset);
        searchPanel.add(btnDelete);

        left.add(searchPanel, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout());
        JPanel summaryPanel = new JPanel(new GridLayout(5, 1));
        summaryPanel.setBorder(BorderFactory.createTitledBorder("Summary"));

        lblTotalIncome = new JLabel("Total Income: ₹0.00");
        lblTotalExpense = new JLabel("Total Expense: ₹0.00");
        lblBudget = new JLabel("Budget Goal: ₹0.00");
        lblRemaining = new JLabel("Remaining: ₹0.00");

        summaryPanel.add(lblTotalIncome);
        summaryPanel.add(lblTotalExpense);
        summaryPanel.add(lblBudget);
        summaryPanel.add(lblRemaining);

        right.add(summaryPanel, BorderLayout.NORTH);


        JPanel chartHolder = new JPanel(new BorderLayout());
        chartHolder.setBorder(BorderFactory.createTitledBorder("Expenses by Category"));
        right.add(chartHolder, BorderLayout.CENTER);

        split.setLeftComponent(left);
        split.setRightComponent(right);
        p.add(split, BorderLayout.CENTER);


        btnAddIncome.addActionListener(e -> showAddIncomeDialog());
        btnAddExpense.addActionListener(e -> showAddExpenseDialog());
        btnSetBudget.addActionListener(e -> showSetBudgetDialog());
        btnSummary.addActionListener(e -> showSummaryDialog());
        btnPie.addActionListener(e -> showPieChartDialog(chartHolder));
        btnLogout.addActionListener(e -> {
            cardLayout.show(mainPanel, "LOGIN");
        });

        btnSearch.addActionListener(e -> applySearchFilter());
        btnReset.addActionListener(e -> {
            tfSearchDate.setText("");
            cbSearchCategory.setSelectedIndex(0);
            refreshTransactionTable(null);
        });

        btnDelete.addActionListener(e -> deleteSelectedTransaction());


        refreshTransactionTable(null);

        drawPieChart(chartHolder);

        split.setDividerLocation(580);
        return p;
    }

    // -------------------- Data Models --------------------
    static class Income {
        String date; // yyyy-MM-dd
        double amount;
        String source;
        String note;
        Income(String date, double amount, String source, String note) {
            this.date = date; this.amount = amount; this.source = source; this.note = note;
        }
        String toLine() {
            return escape(date) + "," + amount + "," + escape(source) + "," + escape(note);
        }
        static Income fromLine(String line) {
            String[] p = splitLine(line, 4);
            if (p == null) return null;
            try { return new Income(p[0], Double.parseDouble(p[1]), p[2], p[3]); }
            catch (NumberFormatException e) { return null; }
        }
    }

    static class Expense {
        String date;
        double amount;
        String category;
        String note;
        Expense(String date, double amount, String category, String note) {
            this.date = date; this.amount = amount; this.category = category; this.note = note;
        }
        String toLine() {
            return escape(date) + "," + amount + "," + escape(category) + "," + escape(note);
        }
        static Expense fromLine(String line) {
            String[] p = splitLine(line, 4);
            if (p == null) return null;
            try { return new Expense(p[0], Double.parseDouble(p[1]), p[2], p[3]); }
            catch (NumberFormatException e) { return null; }
        }
    }


    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\n"," ").replace("\r"," ").replace(",", " ");
    }


    private static String[] splitLine(String line, int expectedParts) {
        if (line == null) return null;
        String[] parts = line.split(",", expectedParts);
        if (parts.length < expectedParts) return null;
        return parts;
    }

    // -------------------- Load / Save --------------------
    private void loadAllData() {
        incomes.clear();
        expenses.clear();
        budgetGoal = 0.0;

        // load incomes
        try (BufferedReader br = Files.newBufferedReader(Paths.get(INCOME_FILE), StandardCharsets.UTF_8)) {
            String l;
            while ((l = br.readLine()) != null) {
                if (l.trim().isEmpty()) continue;
                Income in = Income.fromLine(l);
                if (in != null) incomes.add(in);
            }
        } catch (IOException e) { /* ignore */ }

        // load expenses
        try (BufferedReader br = Files.newBufferedReader(Paths.get(EXPENSE_FILE), StandardCharsets.UTF_8)) {
            String l;
            while ((l = br.readLine()) != null) {
                if (l.trim().isEmpty()) continue;
                Expense ex = Expense.fromLine(l);
                if (ex != null) expenses.add(ex);
            }
        } catch (IOException e) { /* ignore */ }

        // load budget
        try (BufferedReader br = Files.newBufferedReader(Paths.get(BUDGET_FILE), StandardCharsets.UTF_8)) {
            String l = br.readLine();
            if (l != null && !l.trim().isEmpty()) {
                try { budgetGoal = Double.parseDouble(l.trim()); }
                catch (NumberFormatException e) { budgetGoal = 0.0; }
            }
        } catch (IOException e) { /* ignore */ }
    }

    private void saveIncome(Income in) {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(INCOME_FILE), StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            bw.write(in.toLine());
            bw.newLine();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error saving income: " + e.getMessage());
        }
    }

    private void saveExpense(Expense ex) {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(EXPENSE_FILE), StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            bw.write(ex.toLine());
            bw.newLine();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error saving expense: " + e.getMessage());
        }
    }

    private void saveAllIncomes() { // overwrite file
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(INCOME_FILE), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Income in : incomes) {
                bw.write(in.toLine()); bw.newLine();
            }
        } catch (IOException e) { /* ignore */ }
    }

    private void saveAllExpenses() {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(EXPENSE_FILE), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Expense ex : expenses) {
                bw.write(ex.toLine()); bw.newLine();
            }
        } catch (IOException e) { /* ignore */ }
    }

    private void saveBudget() {
        try (BufferedWriter bw = Files.newBufferedWriter(Paths.get(BUDGET_FILE), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            bw.write(String.valueOf(budgetGoal));
            bw.newLine();
        } catch (IOException e) { /* ignore */ }
    }

    private void showAddIncomeDialog() {
        JDialog d = new JDialog(frame, "Add Income", true);
        d.setSize(400, 280);
        d.setLocationRelativeTo(frame);
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        JTextField tfAmount = new JTextField(12);
        JTextField tfDate = new JTextField(10);
        tfDate.setText(DATE_FMT.format(new Date()));
        JTextField tfSource = new JTextField(12);
        JTextField tfNote = new JTextField(15);

        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Amount:"), c);
        c.gridx = 1; p.add(tfAmount, c);
        c.gridy++; c.gridx = 0; p.add(new JLabel("Date (yyyy-mm-dd):"), c);
        c.gridx = 1; p.add(tfDate, c);
        c.gridy++; c.gridx = 0; p.add(new JLabel("Source:"), c);
        c.gridx = 1; p.add(tfSource, c);
        c.gridy++; c.gridx = 0; p.add(new JLabel("Note:"), c);
        c.gridx = 1; p.add(tfNote, c);

        JButton btnSave = new JButton("Save");
        JButton btnCancel = new JButton("Cancel");
        c.gridy++; c.gridx = 0; p.add(btnSave, c);
        c.gridx = 1; p.add(btnCancel, c);

        btnCancel.addActionListener(ev -> d.dispose());
        btnSave.addActionListener(ev -> {
            String amtS = tfAmount.getText().trim();
            String dateS = tfDate.getText().trim();
            String src = tfSource.getText().trim();
            String note = tfNote.getText().trim();
            if (amtS.isEmpty() || dateS.isEmpty()) {
                JOptionPane.showMessageDialog(d, "Please enter amount and date.");
                return;
            }
            double amt;
            try { amt = Double.parseDouble(amtS); }
            catch (NumberFormatException ex) { JOptionPane.showMessageDialog(d, "Invalid amount."); return; }
            // date validation
            try { DATE_FMT.setLenient(false); DATE_FMT.parse(dateS); }
            catch (ParseException ex) { JOptionPane.showMessageDialog(d, "Invalid date format."); return; }

            Income in = new Income(dateS, amt, src, note);
            incomes.add(in);
            saveIncome(in);
            refreshTransactionTable(null);
            refreshSummaryLabels();
            d.dispose();
            JOptionPane.showMessageDialog(frame, "Income saved.");
        });

        d.getContentPane().add(p);
        d.setVisible(true);
    }

    private void showAddExpenseDialog() {
        JDialog d = new JDialog(frame, "Add Expense", true);
        d.setSize(420, 320);
        d.setLocationRelativeTo(frame);
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        JTextField tfAmount = new JTextField(12);
        JTextField tfDate = new JTextField(10);
        tfDate.setText(DATE_FMT.format(new Date()));
        JComboBox<String> cbCat = new JComboBox<>(EXP_CATEGORIES);
        JTextField tfNote = new JTextField(15);

        c.gridx = 0; c.gridy = 0; p.add(new JLabel("Amount:"), c);
        c.gridx = 1; p.add(tfAmount, c);
        c.gridy++; c.gridx = 0; p.add(new JLabel("Date (yyyy-mm-dd):"), c);
        c.gridx = 1; p.add(tfDate, c);
        c.gridy++; c.gridx = 0; p.add(new JLabel("Category:"), c);
        c.gridx = 1; p.add(cbCat, c);
        c.gridy++; c.gridx = 0; p.add(new JLabel("Note:"), c);
        c.gridx = 1; p.add(tfNote, c);

        JButton btnSave = new JButton("Save");
        JButton btnCancel = new JButton("Cancel");
        c.gridy++; c.gridx = 0; p.add(btnSave, c);
        c.gridx = 1; p.add(btnCancel, c);

        btnCancel.addActionListener(ev -> d.dispose());
        btnSave.addActionListener(ev -> {
            String amtS = tfAmount.getText().trim();
            String dateS = tfDate.getText().trim();
            String cat = (String) cbCat.getSelectedItem();
            String note = tfNote.getText().trim();
            if (amtS.isEmpty() || dateS.isEmpty()) {
                JOptionPane.showMessageDialog(d, "Please enter amount and date.");
                return;
            }
            double amt;
            try { amt = Double.parseDouble(amtS); }
            catch (NumberFormatException ex) { JOptionPane.showMessageDialog(d, "Invalid amount."); return; }
           
            try { DATE_FMT.setLenient(false); DATE_FMT.parse(dateS); }
            catch (ParseException ex) { JOptionPane.showMessageDialog(d, "Invalid date format."); return; }

            Expense exObj = new Expense(dateS, amt, cat, note);
            expenses.add(exObj);
            saveExpense(exObj);
            refreshTransactionTable(null);
            refreshSummaryLabels();
            checkLowBudgetAndNotify();
            d.dispose();
            JOptionPane.showMessageDialog(frame, "Expense saved.");
        });

        d.getContentPane().add(p);
        d.setVisible(true);
    }

    private void showSetBudgetDialog() {
        String cur = String.format("%.2f", budgetGoal);
        String s = JOptionPane.showInputDialog(frame, "Set monthly budget goal (number):", cur);
        if (s == null) return;
        s = s.trim();
        double goal;
        try { goal = Double.parseDouble(s); }
        catch (NumberFormatException e) { JOptionPane.showMessageDialog(frame, "Invalid number."); return; }
        budgetGoal = goal;
        saveBudget();
        refreshSummaryLabels();
        checkLowBudgetAndNotify();
        JOptionPane.showMessageDialog(frame, "Budget saved.");
    }

    private void showSummaryDialog() {
        double totalIn = getTotalIncome();
        double totalEx = getTotalExpense();
        double remaining = budgetGoal - totalEx;
        String msg = "Total Income: ₹" + String.format("%.2f", totalIn) + "\n"
                + "Total Expense: ₹" + String.format("%.2f", totalEx) + "\n"
                + "Budget Goal: ₹" + String.format("%.2f", budgetGoal) + "\n"
                + "Remaining: ₹" + String.format("%.2f", remaining);
        JOptionPane.showMessageDialog(frame, msg, "Summary", JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------- Table and Filters --------------------
    private void refreshTransactionTable(List<Map<String,String>> optionalRows) {
        // optionalRows: if not null, show these rows; otherwise build full list (incomes+expenses)
        tableModel.setRowCount(0);
        if (optionalRows != null) {
            for (Map<String,String> r : optionalRows) {
                tableModel.addRow(new Object[]{r.get("type"), r.get("date"), r.get("amount"), r.get("cat"), r.get("note")});
            }
            return;
        }

        for (Income in : incomes) {
            tableModel.addRow(new Object[]{"Income", in.date, String.format("%.2f", in.amount), in.source, in.note});
        }
        for (Expense ex : expenses) {
            tableModel.addRow(new Object[]{"Expense", ex.date, String.format("%.2f", ex.amount), ex.category, ex.note});
        }
    }

    private void applySearchFilter() {
        String date = tfSearchDate.getText().trim();
        String cat = (String) cbSearchCategory.getSelectedItem();
        boolean filterDate = !date.isEmpty();
        boolean filterCat = cat != null && !"Any".equals(cat);

        List<Map<String,String>> rows = new ArrayList<>();
        // incomes
        for (Income in : incomes) {
            boolean ok = true;
            if (filterDate && !in.date.equals(date)) ok = false;
            if (filterCat) ok = false; // incomes have no category -> skip when filtering by category
            if (ok) {
                Map<String,String> r = new HashMap<>();
                r.put("type","Income"); r.put("date",in.date); r.put("amount",String.format("%.2f", in.amount));
                r.put("cat", in.source); r.put("note", in.note);
                rows.add(r);
            }
        }
        // expenses
        for (Expense ex : expenses) {
            boolean ok = true;
            if (filterDate && !ex.date.equals(date)) ok = false;
            if (filterCat && !ex.category.equals(cat)) ok = false;
            if (ok) {
                Map<String,String> r = new HashMap<>();
                r.put("type","Expense"); r.put("date",ex.date); r.put("amount",String.format("%.2f", ex.amount));
                r.put("cat", ex.category); r.put("note", ex.note);
                rows.add(r);
            }
        }
        refreshTransactionTable(rows);
    }

    private void deleteSelectedTransaction() {
        int row = transactionTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(frame, "Select a row to delete.");
            return;
        }
        String type = (String) tableModel.getValueAt(row, 0);
        String date = (String) tableModel.getValueAt(row, 1);
        String amountS = (String) tableModel.getValueAt(row, 2);
        String catOrSrc = (String) tableModel.getValueAt(row, 3);
        String note = (String) tableModel.getValueAt(row, 4);

        double amount;
        try { amount = Double.parseDouble(amountS); } catch (NumberFormatException e) { amount = 0; }

        int confirm = JOptionPane.showConfirmDialog(frame, "Delete selected transaction?", "Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        boolean removed = false;
        if ("Income".equals(type)) {
            Iterator<Income> it = incomes.iterator();
            while (it.hasNext()) {
                Income in = it.next();
                if (in.date.equals(date) && Math.abs(in.amount - amount) < 0.001 && in.source.equals(catOrSrc) && in.note.equals(note)) {
                    it.remove(); removed = true; break;
                }
            }
            if (removed) saveAllIncomes();
        } else {
            Iterator<Expense> it = expenses.iterator();
            while (it.hasNext()) {
                Expense ex = it.next();
                if (ex.date.equals(date) && Math.abs(ex.amount - amount) < 0.001 && ex.category.equals(catOrSrc) && ex.note.equals(note)) {
                    it.remove(); removed = true; break;
                }
            }
            if (removed) saveAllExpenses();
        }

        if (removed) {
            refreshTransactionTable(null);
            refreshSummaryLabels();
            JOptionPane.showMessageDialog(frame, "Deleted.");
            checkLowBudgetAndNotify();
        } else {
            JOptionPane.showMessageDialog(frame, "Transaction not found or already removed.");
        }
    }

    // -------------------- Summary / Budget --------------------
    private double getTotalIncome() {
        return incomes.stream().mapToDouble(i -> i.amount).sum();
    }
    private double getTotalExpense() {
        return expenses.stream().mapToDouble(e -> e.amount).sum();
    }

    private void refreshSummaryLabels() {
        double totIn = getTotalIncome();
        double totEx = getTotalExpense();
        double remaining = budgetGoal - totEx;

        lblTotalIncome.setText("Total Income: ₹" + String.format("%.2f", totIn));
        lblTotalExpense.setText("Total Expense: ₹" + String.format("%.2f", totEx));
        lblBudget.setText("Budget Goal: ₹" + String.format("%.2f", budgetGoal));
        lblRemaining.setText("Remaining: ₹" + String.format("%.2f", remaining));

        if (budgetGoal > 0 && remaining < 0) {
            lblRemaining.setForeground(Color.RED);
        } else {
            lblRemaining.setForeground(Color.BLACK);
        }
    }

    // Notify if remaining budget falls below 10% of budget
    private void checkLowBudgetAndNotify() {
        if (budgetGoal <= 0) return; // no budget set
        double remaining = budgetGoal - getTotalExpense();
        if (remaining <= budgetGoal * LOW_BUDGET_THRESHOLD) {
            // show a popup notification
            JOptionPane.showMessageDialog(frame,
                    "Warning: Your remaining budget is below 10% of the goal.\nRemaining: ₹" + String.format("%.2f", remaining),
                    "Low Budget Warning",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // -------------------- Pie Chart --------------------
    private void showPieChartDialog(JPanel holder) {
        // Create a dialog to show a larger chart
        JDialog d = new JDialog(frame, "Expense Pie Chart", true);
        d.setSize(600, 500);
        d.setLocationRelativeTo(frame);
        JPanel panel = new JPanel(new BorderLayout());
        drawPieChart(panel);
        d.getContentPane().add(panel);
        d.setVisible(true);
    }

    private void drawPieChart(JPanel holder) {
        Map<String, Double> sums = new HashMap<>();
        for (Expense e : expenses) {
            sums.put(e.category, sums.getOrDefault(e.category, 0.0) + e.amount);
        }
        DefaultPieDataset dataset = new DefaultPieDataset();
        if (sums.isEmpty()) {
            dataset.setValue("No data", 1.0);
        } else {
            for (Map.Entry<String, Double> en : sums.entrySet()) {
                dataset.setValue(en.getKey(), en.getValue());
            }
        }
        JFreeChart chart = ChartFactory.createPieChart("Expenses by Category", dataset, true, true, false);
        holder.removeAll();
        holder.add(new ChartPanel(chart), BorderLayout.CENTER);
        holder.revalidate();
        holder.repaint();
    }


    // Call to reload all data from files and refresh UI
    private void reloadAllAndRefresh() {
        loadAllData();
        refreshTransactionTable(null);
        refreshSummaryLabels();
    }


}