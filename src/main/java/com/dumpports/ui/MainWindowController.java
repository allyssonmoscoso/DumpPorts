package com.dumpports.ui;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dumpports.model.Socket;
import com.dumpports.service.ExportService;
import com.dumpports.service.PrivilegeManager;
import com.dumpports.service.SocketStatisticsService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;

public class MainWindowController implements Initializable {
                            @FXML private javafx.scene.control.MenuItem exportHistoryMenuItem;
                            @FXML private javafx.scene.control.MenuItem clearHistoryMenuItem;
                            @FXML private javafx.scene.control.Label historyWarningLabel;
                        // Efficient connection history: compositeKey -> bounded deque of history entries
                        private static final int MAX_HISTORY_PER_SOCKET = 200;
                        private static final int MAX_TOTAL_HISTORY = 10000;
                        private final java.util.LinkedHashMap<String, java.util.ArrayDeque<com.dumpports.model.SocketHistoryEntry>> connectionHistory = new java.util.LinkedHashMap<>();
                        private int totalHistoryEntries = 0;
                        private boolean historyPrunedWarning = false;
                    @FXML
                    private javafx.scene.control.ToggleButton favoritesToggleButton;
                // Set of favorite composite keys (persisted)
                private java.util.Set<String> favoriteKeys = new java.util.HashSet<>();
                private static final String FAVORITES_PREF_KEY = "favoriteSockets";
                private boolean filterFavorites = false;
            @FXML
            private TableColumn<Socket, Boolean> favoriteColumn;
        /**
         * Normalize a string: NFKD, lower, remove diacritics and special characters except alphanum and space.
         */
        private String normalize(String input) {
            if (input == null) return "";
            String norm = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", ""); // Remove diacritics
            norm = norm.replaceAll("[^\\p{Alnum} ]", ""); // Remove special chars except alphanum and space
            return norm.toLowerCase();
        }

        /**
         * Checks if the normalized field contains the normalized search string.
         */
        private boolean containsNormalized(String field, String search) {
            if (field == null) return false;
            return normalize(field).contains(search);
        }
    private static final Logger logger = LoggerFactory.getLogger(MainWindowController.class);

    // Map to store previous socket states for monitoring
    private final java.util.Map<String, String> previousSocketStates = new java.util.HashMap<>();
    // List to store detected state transitions (for future UI/log display)
    private final List<String> stateTransitions = new ArrayList<>();

    @FXML
    private TableView<Socket> socketsTable;
    @FXML
    private TableColumn<Socket, String> protocolColumn;
    @FXML
    private TableColumn<Socket, String> stateColumn;
    @FXML
    private TableColumn<Socket, String> localAddressColumn;
    @FXML
    private TableColumn<Socket, String> localPortColumn;
    @FXML
    private TableColumn<Socket, String> remoteAddressColumn;
    @FXML
    private TableColumn<Socket, String> remotePortColumn;
    @FXML
    private TableColumn<Socket, String> processColumn;
    @FXML
    private ComboBox<String> protocolFilterCombo;
    @FXML
    private Button refreshButton;
    @FXML
    private CheckBox autoRefreshCheck;
    @FXML
    private Spinner<Integer> refreshIntervalSpinner;
    @FXML
    private Button exportCSVButton;
    @FXML
    private Button exportJSONButton;
    @FXML
    private Label statusLabel;
    // ...rest of fields, methods, and logic...
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private Label privilegeModeLabel;
    @FXML
    private Button elevateButton;
    @FXML
    private ProgressIndicator elevationIndicator;

    // Advanced filter controls
    @FXML private TextField searchField;
    @FXML private TextField minPortField;
    @FXML private TextField maxPortField;
    @FXML private ComboBox<String> portTypeCombo;
    @FXML private TextField addressRegexField;
    @FXML private CheckBox localAddressCheck;
    @FXML private CheckBox remoteAddressCheck;
    @FXML private Label regexWarningLabel;

    // Preferences for persistence
    private final Preferences prefs = Preferences.userNodeForPackage(MainWindowController.class);

    private final PrivilegeManager privilegeManager = new PrivilegeManager();
    private final SocketStatisticsService socketService = new SocketStatisticsService(privilegeManager);
    private final ExportService exportService = new ExportService();
    private ObservableList<Socket> socketsList = FXCollections.observableArrayList();
    private ObservableList<Socket> allSocketsData = FXCollections.observableArrayList();
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private final AtomicBoolean isRequestingElevation = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;
    private int refreshIntervalSeconds = 10;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        updateHistoryWarningLabel();
        logger.info("Initializing MainWindowController");
        setupTableColumns();
        setupProtocolFilter();
        setupRefreshButton();
        setupExportButtons();
        setupAutoRefreshControls();
        setupAdvancedFilterControls();
        setupPrivilegeControls();
        loadSocketData();
        setupSearchHighlighting();
        setupFavoriteColumn();
        loadFavoritesFromPrefs();
        // Optionally: loadHistoryFromDisk();
        if (favoritesToggleButton != null) {
            favoritesToggleButton.setSelected(false);
            favoritesToggleButton.selectedProperty().addListener((obs, oldVal, newVal) -> {
                filterFavorites = newVal;
                filterSockets();
                updateFavoritesToggleStyle();
            });
            updateFavoritesToggleStyle();
        }
    }

    /**
     * Wires the privilege mode controls (mode label and elevation button).
     */
    private void setupPrivilegeControls() {
        updatePrivilegeUi();
        if (elevateButton != null) {
            elevateButton.setOnAction(event -> onElevateRequested());
        }
    }

    /**
     * Prompts the user to elevate to root mode when the application starts.
     * Called by {@code DumpPortsApplication} once the main window is visible.
     */
    public void promptElevationOnStartup() {
        if (privilegeManager.isRootMode()) {
            updatePrivilegeUi();
            statusLabel.setText("Running with root privileges");
            return;
        }
        if (!privilegeManager.isPkexecAvailable()) {
            updatePrivilegeUi();
            statusLabel.setText("User mode (PolicyKit / pkexec not available)");
            return;
        }

        boolean confirmed = showConfirmationDialog(
                "Root Access",
                "Run DumpPorts with root privileges?",
                "Root mode allows DumpPorts to see the processes of every user.\n"
                        + "You will be asked to authenticate with your system password.\n"
                        + "If you decline, the app keeps running in user mode and you can elevate later.");
        if (confirmed) {
            requestElevation();
        } else {
            updatePrivilegeUi();
        }
    }

    /**
     * Handles the "Run as Root" button.
     */
    @FXML
    private void onElevateRequested() {
        boolean confirmed = showConfirmationDialog(
                "Root Access",
                "Run DumpPorts with root privileges?",
                "You will be asked to authenticate with your system password.");
        if (confirmed) {
            requestElevation();
        }
    }

    /**
     * Requests root privileges on a background thread and updates the UI with
     * the result. In root mode all socket queries are delegated to the
     * privileged helper, so auto-refresh does not prompt again.
     */
    private void requestElevation() {
        if (!isRequestingElevation.compareAndSet(false, true)) {
            return;
        }
        if (elevationIndicator != null) {
            elevationIndicator.setVisible(true);
            elevationIndicator.setManaged(true);
        }
        if (elevateButton != null) {
            elevateButton.setDisable(true);
        }
        statusLabel.setText("Requesting root privileges...");

        Thread thread = new Thread(() -> {
            boolean success = privilegeManager.requestRootMode();
            Platform.runLater(() -> {
                isRequestingElevation.set(false);
                if (elevationIndicator != null) {
                    elevationIndicator.setVisible(false);
                    elevationIndicator.setManaged(false);
                }
                if (elevateButton != null) {
                    elevateButton.setDisable(false);
                }
                updatePrivilegeUi();
                if (success) {
                    statusLabel.setText("Root mode enabled");
                    logger.info("Root mode enabled by user");
                    loadSocketData();
                } else {
                    statusLabel.setText("User mode");
                    String error = privilegeManager.getLastError();
                    showWarningAlert("Root Access Denied", "Continuing in user mode",
                            error != null ? error : "Root privileges were not granted.");
                }
            });
        }, "privilege-elevation-thread");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Refreshes the privilege mode label and the visibility of the elevate button.
     */
    private void updatePrivilegeUi() {
        if (privilegeModeLabel != null) {
            if (privilegeManager.isRootMode()) {
                privilegeModeLabel.setText("🔓 Root Mode");
                privilegeModeLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 12; -fx-font-weight: bold;");
            } else {
                privilegeModeLabel.setText("🔒 User Mode");
                privilegeModeLabel.setStyle("-fx-text-fill: #b8860b; -fx-font-size: 12; -fx-font-weight: bold;");
            }
        }
        if (elevateButton != null) {
            boolean showButton = !privilegeManager.isRootMode();
            elevateButton.setVisible(showButton);
            elevateButton.setManaged(showButton);
        }
    }

    /**
     * Releases resources held by the privilege manager (stops the helper).
     */
    public void shutdown() {
        privilegeManager.close();
    }

    /**
     * Export connection history to a JSON file (GZIP compressed).
     */
    @FXML
    private void onExportHistory() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Export Connection History");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Compressed JSON Files", "*.json.gz"));
        // Suggest a default filename
        String defaultName = "connection_history_" + java.time.LocalDateTime.now().toString().replace(":", "-").replace("T", "_") + ".json.gz";
        fileChooser.setInitialFileName(defaultName);
        java.io.File file = fileChooser.showSaveDialog(socketsTable.getScene().getWindow());
        if (file != null) {
            boolean confirmed = showConfirmationDialog("Export History", "Export all connection history to file?", "This will export all tracked connection events as compressed JSON (.json.gz).");
            if (confirmed) {
                // Write JSON to a temp .json file, then compress to .json.gz, then delete temp
                java.io.File tempJson = null;
                try {
                    // 1. Write JSON to temp file
                    tempJson = java.io.File.createTempFile("connection_history_", ".json");
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(tempJson), java.nio.charset.StandardCharsets.UTF_8)) {
                        mapper.writeValue(writer, connectionHistory);
                    }
                    // 2. Compress temp .json to .json.gz
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(tempJson);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                         java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(fos)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            gzip.write(buffer, 0, len);
                        }
                    }
                    showInfoAlert("Export Complete", "History exported successfully.", file.getAbsolutePath());
                } catch (Exception e) {
                    showErrorAlert("Export Failed", "Could not export history.", e.getMessage());
                } finally {
                    if (tempJson != null && tempJson.exists()) tempJson.delete();
                }
            }
        }
    }

    /**
     * Clear all connection history after user confirmation.
     */
    @FXML
    private void onClearHistory() {
        boolean confirmed = showConfirmationDialog("Clear History", "Are you sure you want to clear all connection history?", "This operation cannot be undone.");
        if (confirmed) {
            connectionHistory.clear();
            totalHistoryEntries = 0;
            historyPrunedWarning = false;
            updateHistoryWarningLabel();
            showInfoAlert("History Cleared", "All connection history has been cleared.", null);
        }
    }

    /**
     * Update the warning label if history was pruned.
     */
    private void updateHistoryWarningLabel() {
        if (historyWarningLabel != null) {
            if (historyPrunedWarning) {
                historyWarningLabel.setText("Warning: Some connection history was pruned due to storage limits.");
                historyWarningLabel.setVisible(true);
            } else {
                historyWarningLabel.setText("");
                historyWarningLabel.setVisible(false);
            }
        }
    }

    /**
     * Show a confirmation dialog and return true if user confirms.
     */
    private boolean showConfirmationDialog(String title, String header, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == javafx.scene.control.ButtonType.OK;
    }

    /**
     * Record a connection event for a socket, pruning as needed.
     */
    private void recordSocketHistory(String compositeKey, com.dumpports.model.SocketHistoryEntry entry) {
        connectionHistory.putIfAbsent(compositeKey, new java.util.ArrayDeque<>());
        java.util.ArrayDeque<com.dumpports.model.SocketHistoryEntry> deque = connectionHistory.get(compositeKey);
        if (deque.size() >= MAX_HISTORY_PER_SOCKET) {
            deque.pollFirst();
            totalHistoryEntries--;
            historyPrunedWarning = true;
        }
        deque.addLast(entry);
        totalHistoryEntries++;
        // Prune global history if needed
        while (totalHistoryEntries > MAX_TOTAL_HISTORY) {
            // Remove oldest entry from the oldest socket
            String oldestKey = connectionHistory.keySet().iterator().next();
            java.util.ArrayDeque<com.dumpports.model.SocketHistoryEntry> oldestDeque = connectionHistory.get(oldestKey);
            if (oldestDeque != null && !oldestDeque.isEmpty()) {
                oldestDeque.pollFirst();
                totalHistoryEntries--;
                historyPrunedWarning = true;
                if (oldestDeque.isEmpty()) {
                    connectionHistory.remove(oldestKey);
                }
            } else {
                connectionHistory.remove(oldestKey);
            }
        }
    }
        @FXML
        private void onFavoritesToggle() {
            if (favoritesToggleButton != null) {
                filterFavorites = favoritesToggleButton.isSelected();
                filterSockets();
                updateFavoritesToggleStyle();
            }
        }

        private void updateFavoritesToggleStyle() {
            if (favoritesToggleButton != null) {
                if (favoritesToggleButton.isSelected()) {
                    favoritesToggleButton.setStyle("-fx-background-color: gold; -fx-font-size: 14; -fx-padding: 5 10;");
                } else {
                    favoritesToggleButton.setStyle("-fx-background-color: #f0f0f0; -fx-font-size: 14; -fx-padding: 5 10;");
                }
            }
        }
    /**
     * Sets up the favorite/star column with a toggleable icon and click handler.
     */
    private void setupFavoriteColumn() {
        favoriteColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isFavorite()));
        favoriteColumn.setCellFactory(col -> new javafx.scene.control.TableCell<Socket, Boolean>() {
            private final javafx.scene.control.Label star = new javafx.scene.control.Label();
            {
                star.setStyle("-fx-font-size: 18; -fx-cursor: hand;");
                setGraphic(star);
                setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
                star.setOnMouseClicked(event -> {
                    Socket socket = getTableView().getItems().get(getIndex());
                    boolean newFav = !socket.isFavorite();
                    socket.setFavorite(newFav);
                    updateFavoritePrefs(socket, newFav);
                    getTableView().refresh();
                });
            }
            @Override
            protected void updateItem(Boolean fav, boolean empty) {
                super.updateItem(fav, empty);
                if (empty || getTableRow() == null || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    Socket socket = getTableView().getItems().get(getIndex());
                    boolean isFav = socket.isFavorite();
                    star.setText(isFav ? "★" : "☆");
                    setGraphic(star);
                    if (socket.getExecutablePath() == null || socket.getExecutablePath().isEmpty()) {
                        star.setTooltip(new javafx.scene.control.Tooltip("Warning: Favorite may not be unique (missing executable path)."));
                    } else {
                        star.setTooltip(null);
                    }
                }
            }
        });
    }

    /**
     * Loads favorite keys from Preferences and updates sockets in the table.
     */
    private void loadFavoritesFromPrefs() {
        favoriteKeys.clear();
        String favs = prefs.get(FAVORITES_PREF_KEY, "");
        if (!favs.isEmpty()) {
            for (String key : favs.split(",")) {
                if (!key.isEmpty()) favoriteKeys.add(key);
            }
        }
        // Mark favorites in allSocketsData
        for (Socket s : allSocketsData) {
            s.setFavorite(favoriteKeys.contains(s.getCompositeKey()));
        }
        socketsTable.refresh();
    }

    /**
     * Updates the favorite keys set and persists it.
     */
    private void updateFavoritePrefs(Socket socket, boolean isFav) {
        String key = socket.getCompositeKey();
        if (isFav) {
            favoriteKeys.add(key);
        } else {
            favoriteKeys.remove(key);
        }
        prefs.put(FAVORITES_PREF_KEY, String.join(",", favoriteKeys));
    }

    /**
     * Toggle filtering to show only favorites.
     */
    public void toggleFavoriteFilter() {
        filterFavorites = !filterFavorites;
        filterSockets();
    }

        /**
         * Sets up custom cell factories for all relevant table columns to highlight search matches.
         * Uses a subtle background color for matched text, ignoring diacritics and special characters.
         */
        private void setupSearchHighlighting() {
            javafx.scene.paint.Color highlightColor = javafx.scene.paint.Color.web("#FFFACD"); // LemonChiffon (subtle)
            String searchText = normalize(searchField.getText());

            // Helper to create cell factory for highlighting
            java.util.function.Function<javafx.util.Callback<TableColumn<Socket, String>, javafx.scene.control.TableCell<Socket, String>>, javafx.util.Callback<TableColumn<Socket, String>, javafx.scene.control.TableCell<Socket, String>>> highlighter = baseFactory -> col -> {
                return new javafx.scene.control.TableCell<Socket, String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setStyle("");
                        } else {
                            setText(item);
                            String normalizedItem = normalize(item);
                            String normalizedSearch = normalize(searchField.getText());
                            if (!normalizedSearch.isEmpty() && containsNormalized(normalizedItem, normalizedSearch)) {
                                setStyle("-fx-background-color: #FFFACD;");
                            } else {
                                setStyle("");
                            }
                        }
                    }
                };
            };

            // Apply to all relevant columns
            protocolColumn.setCellFactory(highlighter.apply(protocolColumn.getCellFactory()));
            stateColumn.setCellFactory(highlighter.apply(stateColumn.getCellFactory()));
            localAddressColumn.setCellFactory(highlighter.apply(localAddressColumn.getCellFactory()));
            localPortColumn.setCellFactory(highlighter.apply(localPortColumn.getCellFactory()));
            remoteAddressColumn.setCellFactory(highlighter.apply(remoteAddressColumn.getCellFactory()));
            remotePortColumn.setCellFactory(highlighter.apply(remotePortColumn.getCellFactory()));
            processColumn.setCellFactory(highlighter.apply(processColumn.getCellFactory()));

            // Update highlighting on search text change
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                socketsTable.refresh();
            });
        }
    private void setupAdvancedFilterControls() {
        // Restore persisted filter values
        minPortField.setText(prefs.get("minPort", ""));
        maxPortField.setText(prefs.get("maxPort", ""));
        portTypeCombo.setValue(prefs.get("portType", "Both"));
        addressRegexField.setText(prefs.get("addressRegex", ""));
        localAddressCheck.setSelected(prefs.getBoolean("localAddressCheck", true));
        remoteAddressCheck.setSelected(prefs.getBoolean("remoteAddressCheck", true));
        regexWarningLabel.setVisible(false);
        searchField.setText(prefs.get("searchText", ""));

        // Listeners for persistence and filtering
        minPortField.textProperty().addListener((obs, o, n) -> saveAndFilter());
        maxPortField.textProperty().addListener((obs, o, n) -> saveAndFilter());
        portTypeCombo.valueProperty().addListener((obs, o, n) -> saveAndFilter());
        addressRegexField.textProperty().addListener((obs, o, n) -> saveAndFilter());
        localAddressCheck.selectedProperty().addListener((obs, o, n) -> saveAndFilter());
        remoteAddressCheck.selectedProperty().addListener((obs, o, n) -> saveAndFilter());
        searchField.textProperty().addListener((obs, o, n) -> {
            prefs.put("searchText", n);
            filterSockets();
        });
    }

    private void saveAndFilter() {
        prefs.put("minPort", minPortField.getText());
        prefs.put("maxPort", maxPortField.getText());
        prefs.put("portType", portTypeCombo.getValue() == null ? "Both" : portTypeCombo.getValue());
        prefs.put("addressRegex", addressRegexField.getText());
        prefs.putBoolean("localAddressCheck", localAddressCheck.isSelected());
        prefs.putBoolean("remoteAddressCheck", remoteAddressCheck.isSelected());
        filterSockets();
    }

    private void setupAutoRefreshControls() {
        // Spinner defaults and bounds: 2..300 seconds
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 300, refreshIntervalSeconds);
        refreshIntervalSpinner.setValueFactory(valueFactory);
        refreshIntervalSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            refreshIntervalSeconds = newVal;
            if (autoRefreshCheck.isSelected()) {
                restartAutoRefresh();
            }
        });

        autoRefreshCheck.setOnAction(event -> {
            if (autoRefreshCheck.isSelected()) {
                startAutoRefresh();
            } else {
                stopAutoRefresh();
            }
        });
    }

    private void startAutoRefresh() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auto-refresh-thread");
                t.setDaemon(true);
                return t;
            });
        }
        scheduleRefreshTask();
        statusLabel.setText("Auto refresh ON (" + refreshIntervalSeconds + "s)");
        logger.debug("Auto refresh started with interval {}s", refreshIntervalSeconds);
    }

    private void scheduleRefreshTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel(false);
        }
        refreshTask = scheduler.scheduleAtFixedRate(() -> {
            // trigger data load; it already ensures thread safety
            loadSocketData();
        }, refreshIntervalSeconds, refreshIntervalSeconds, TimeUnit.SECONDS);
    }

    private void restartAutoRefresh() {
        stopAutoRefresh();
        startAutoRefresh();
    }

    private void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        statusLabel.setText("Auto refresh OFF");
        logger.debug("Auto refresh stopped");
    }

    private void setupTableColumns() {
        protocolColumn.setCellValueFactory(new PropertyValueFactory<>("protocol"));
        stateColumn.setCellValueFactory(new PropertyValueFactory<>("state"));
        localAddressColumn.setCellValueFactory(new PropertyValueFactory<>("localAddress"));
        localPortColumn.setCellValueFactory(new PropertyValueFactory<>("localPort"));
        remoteAddressColumn.setCellValueFactory(new PropertyValueFactory<>("remoteAddress"));
        remotePortColumn.setCellValueFactory(new PropertyValueFactory<>("remotePort"));
        
        // Use executable path if available, otherwise fall back to process name
        processColumn.setCellValueFactory(cellData -> {
            Socket socket = cellData.getValue();
            String display = socket.getExecutablePath();
            if (display == null || display.isEmpty()) {
                display = socket.getProcess();
            }
            return new javafx.beans.property.SimpleStringProperty(display);
        });

        socketsTable.setItems(socketsList);
    }

    private void setupProtocolFilter() {
        ObservableList<String> protocols = FXCollections.observableArrayList(
                "All", "tcp", "udp", "raw", "sctp"
        );
        protocolFilterCombo.setItems(protocols);
        protocolFilterCombo.setValue("All");
        protocolFilterCombo.setOnAction(event -> filterSockets());
    }

    private void setupRefreshButton() {
        refreshButton.setOnAction(event -> loadSocketData());
    }

    private void setupExportButtons() {
        exportCSVButton.setOnAction(event -> exportToCSV());
        exportJSONButton.setOnAction(event -> exportToJSON());
    }

    @FXML
    private void loadSocketData() {
        if (!isRefreshing.compareAndSet(false, true)) {
            // A refresh is already running; skip starting another
            return;
        }

        final boolean wasRoot = privilegeManager.isRootMode();
        loadingIndicator.setVisible(true);
        statusLabel.setText("Loading socket statistics...");

        Thread loadThread = new Thread(() -> {
            try {
                List<Socket> sockets = socketService.getSocketStatistics();
                
                Platform.runLater(() -> {
                    allSocketsData.clear();
                    allSocketsData.addAll(sockets);

                    // Record history transitions before updating the UI
                    detectSocketStateTransitions(sockets);

                    String selectedProtocol = protocolFilterCombo.getValue();
                    if ("All".equals(selectedProtocol) || selectedProtocol == null) {
                        socketsList.clear();
                        socketsList.addAll(sockets);
                        statusLabel.setText("Loaded " + sockets.size() + " sockets");
                    } else {
                        // Reapply the current filter after loading new data
                        filterSockets();
                    }

                    loadingIndicator.setVisible(false);
                    logger.debug("Socket data loaded successfully");
                    isRefreshing.set(false);
                    if (wasRoot && !privilegeManager.isRootMode()) {
                        updatePrivilegeUi();
                        showWarningAlert("Root Access Lost", "Falling back to user mode",
                                privilegeManager.getLastError());
                    }
                });
            } catch (Exception e) {
                logger.error("Error loading socket data", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading socket data");
                    loadingIndicator.setVisible(false);
                    showErrorAlert("Error", "Failed to load socket statistics", e.getMessage());
                    isRefreshing.set(false);
                    updatePrivilegeUi();
                });
            }
        });
        
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Detects socket state transitions and logs them.
     * @param currentSockets List of current sockets
     */
    private void detectSocketStateTransitions(List<Socket> currentSockets) {
        java.util.Map<String, String> newStates = new java.util.HashMap<>();
        List<String> transitions = new ArrayList<>();
        for (Socket socket : currentSockets) {
            String key = buildSocketKey(socket);
            String newState = socket.getState();
            newStates.put(key, newState);
            String oldState = previousSocketStates.get(key);
            if (oldState == null) {
                // Socket opened
                String transition = String.format("Socket [%s] opened: %s", key, newState);
                transitions.add(transition);
                logger.info(transition);
                // Record open event
                recordSocketHistory(key, new com.dumpports.model.SocketHistoryEntry(
                        com.dumpports.model.SocketHistoryEntry.EventType.OPENED, newState, socket.toString()));
            } else if (!oldState.equals(newState)) {
                // State changed
                String transition = String.format("Socket [%s] state changed: %s -> %s", key, oldState, newState);
                transitions.add(transition);
                logger.info(transition);
                // Record state change event
                recordSocketHistory(key, new com.dumpports.model.SocketHistoryEntry(
                        com.dumpports.model.SocketHistoryEntry.EventType.STATE_CHANGED, newState, socket.toString()));
            }
        }
        // Detect closed sockets (present before, missing now)
        for (String key : previousSocketStates.keySet()) {
            if (!newStates.containsKey(key)) {
                String oldState = previousSocketStates.get(key);
                String transition = String.format("Socket [%s] closed (was %s)", key, oldState);
                transitions.add(transition);
                logger.info(transition);
                // Record close event
                recordSocketHistory(key, new com.dumpports.model.SocketHistoryEntry(
                        com.dumpports.model.SocketHistoryEntry.EventType.CLOSED, oldState, "Socket closed"));
            }
        }
        previousSocketStates.clear();
        previousSocketStates.putAll(newStates);
        // Store transitions for possible UI display
        synchronized (stateTransitions) {
            stateTransitions.clear();
            stateTransitions.addAll(transitions);
        }
    }

    /**
     * Builds a unique key for a socket based on protocol, local/remote address/port, and pid.
     */
    private String buildSocketKey(Socket socket) {
        return String.join(":",
                safe(socket.getProtocol()),
                safe(socket.getLocalAddress()),
                safe(socket.getLocalPort()),
                safe(socket.getRemoteAddress()),
                safe(socket.getRemotePort()),
                safe(socket.getPid())
        );
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    @FXML
    private void filterSockets() {
            final String searchTextRaw = searchField.getText().trim();
            final String searchText = normalize(searchTextRaw);
        final String selectedProtocol = protocolFilterCombo.getValue();
        final String minPortStr = minPortField.getText().trim();
        final String maxPortStr = maxPortField.getText().trim();
        final String portType = portTypeCombo.getValue() == null ? "Both" : portTypeCombo.getValue();
        final String regex = addressRegexField.getText().trim();
        final boolean filterLocal = localAddressCheck.isSelected();
        final boolean filterRemote = remoteAddressCheck.isSelected();

        final Integer minPort;
        if (!minPortStr.isEmpty()) {
            Integer temp = null;
            try { temp = Integer.parseInt(minPortStr); } catch (NumberFormatException ignored) {}
            minPort = temp;
        } else {
            minPort = null;
        }
        final Integer maxPort;
        if (!maxPortStr.isEmpty()) {
            Integer temp = null;
            try { temp = Integer.parseInt(maxPortStr); } catch (NumberFormatException ignored) {}
            maxPort = temp;
        } else {
            maxPort = null;
        }

        final Pattern addressPattern;
        final boolean regexValid;
        if (!regex.isEmpty() && (filterLocal || filterRemote)) {
            Pattern tempPattern = null;
            boolean valid = true;
            try {
                tempPattern = Pattern.compile(regex);
                regexWarningLabel.setVisible(false);
            } catch (PatternSyntaxException e) {
                valid = false;
                regexWarningLabel.setText("Invalid regex pattern");
                regexWarningLabel.setVisible(true);
            }
            addressPattern = tempPattern;
            regexValid = valid;
        } else {
            addressPattern = null;
            regexValid = true;
            regexWarningLabel.setVisible(false);
        }

        List<Socket> filtered = allSocketsData.stream()
            .filter(socket -> !filterFavorites || socket.isFavorite())
            .filter(socket -> {
                // Protocol filter
                if (!"All".equals(selectedProtocol) && selectedProtocol != null && !socket.getProtocol().equalsIgnoreCase(selectedProtocol))
                    return false;
                // Port range filter
                if (minPort != null || maxPort != null) {
                    boolean match = false;
                    if (portType.equals("Local")) {
                        match = portInRange(socket.getLocalPort(), minPort, maxPort);
                    } else if (portType.equals("Remote")) {
                        match = portInRange(socket.getRemotePort(), minPort, maxPort);
                    } else if (portType.equals("Both")) {
                        match = portInRange(socket.getLocalPort(), minPort, maxPort) || portInRange(socket.getRemotePort(), minPort, maxPort);
                    }
                    if (!match) return false;
                }
                // Address regex filter (only if valid)
                if (regexValid && addressPattern != null) {
                    boolean localOk = !filterLocal || (socket.getLocalAddress() != null && addressPattern.matcher(socket.getLocalAddress()).find());
                    boolean remoteOk = !filterRemote || (socket.getRemoteAddress() != null && addressPattern.matcher(socket.getRemoteAddress()).find());
                    if (!(localOk || remoteOk)) return false;
                }
                // Search filter (match any field, ignore diacritics/specials)
                if (!searchText.isEmpty()) {
                    boolean found = false;
                    found |= containsNormalized(socket.getProtocol(), searchText);
                    found |= containsNormalized(socket.getState(), searchText);
                    found |= containsNormalized(socket.getLocalAddress(), searchText);
                    found |= containsNormalized(socket.getLocalPort(), searchText);
                    found |= containsNormalized(socket.getRemoteAddress(), searchText);
                    found |= containsNormalized(socket.getRemotePort(), searchText);
                    found |= containsNormalized(socket.getProcess(), searchText);
                    found |= containsNormalized(socket.getExecutablePath(), searchText);
                    if (!found) return false;
                }
                return true;
            })
            .toList();
        socketsList.clear();
        socketsList.addAll(filtered);
        statusLabel.setText("Filtered: " + filtered.size() + " sockets");
    }

    private boolean portInRange(String portStr, Integer min, Integer max) {
        if (portStr == null || portStr.isEmpty()) return false;
        try {
            int port = Integer.parseInt(portStr);
            if (min != null && port < min) return false;
            if (max != null && port > max) return false;
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void exportToCSV() {
        if (socketsList.isEmpty()) {
            showWarningAlert("No Data", "No sockets to export", "Please load socket data first.");
            return;
        }

        File selectedFile = showFileChooser("CSV Files (*.csv)", "*.csv", "Export Sockets to CSV");
        if (selectedFile != null) {
            // Export only the displayed/filtered data from the table
            exportData(new ArrayList<>(socketsList), selectedFile, "csv");
        }
    }

    @FXML
    private void exportToJSON() {
        if (socketsList.isEmpty()) {
            showWarningAlert("No Data", "No sockets to export", "Please load socket data first.");
            return;
        }

        File selectedFile = showFileChooser("JSON Files (*.json)", "*.json", "Export Sockets to JSON");
        if (selectedFile != null) {
            // Export only the displayed/filtered data from the table
            exportData(new ArrayList<>(socketsList), selectedFile, "json");
        }
    }

    private File showFileChooser(String filterDescription, String filterExtension, String title) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filterDescription, filterExtension));
        fileChooser.setInitialFileName(exportService.generateDefaultFilename(filterExtension.replace("*.", "")));
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        Window window = refreshButton.getScene().getWindow();
        return fileChooser.showSaveDialog(window);
    }

    private void exportData(List<Socket> dataToExport, File file, String format) {
        Thread exportThread = new Thread(() -> {
            try {
                if ("csv".equalsIgnoreCase(format)) {
                    exportService.exportToCSV(dataToExport, file.getAbsolutePath());
                } else if ("json".equalsIgnoreCase(format)) {
                    exportService.exportToJSON(dataToExport, file.getAbsolutePath());
                }

                Platform.runLater(() -> {
                    statusLabel.setText("Successfully exported " + dataToExport.size() + " sockets to " + 
                            format.toUpperCase() + ": " + file.getName());
                    showInfoAlert("Export Successful", "Export completed", 
                            "Exported " + dataToExport.size() + " sockets to:\n" + file.getAbsolutePath());
                    logger.info("Export completed successfully to {}", file.getAbsolutePath());
                });
            } catch (Exception e) {
                logger.error("Error exporting data", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Export failed");
                    showErrorAlert("Export Failed", "Error exporting sockets", e.getMessage());
                });
            }
        });

        exportThread.setDaemon(true);
        exportThread.start();
    }



    private void showWarningAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

