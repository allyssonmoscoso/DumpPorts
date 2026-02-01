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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dumpports.model.Socket;
import com.dumpports.service.ExportService;
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
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Controller for the main window of the DumpPorts application.
 * Manages the UI interactions and coordination between services and views.
 */
public class MainWindowController implements Initializable {

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
    @FXML
    private ProgressIndicator loadingIndicator;

    private final SocketStatisticsService socketService = new SocketStatisticsService();
    private final ExportService exportService = new ExportService();
    private ObservableList<Socket> socketsList = FXCollections.observableArrayList();
    private ObservableList<Socket> allSocketsData = FXCollections.observableArrayList();
    private final AtomicBoolean isRefreshing = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;
    private int refreshIntervalSeconds = 10;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing MainWindowController");
        
        setupTableColumns();
        setupProtocolFilter();
        setupRefreshButton();
        setupExportButtons();
        setupAutoRefreshControls();
        loadSocketData();
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

        loadingIndicator.setVisible(true);
        statusLabel.setText("Loading socket statistics...");

        Thread loadThread = new Thread(() -> {
            try {
                List<Socket> sockets = socketService.getSocketStatistics();
                
                Platform.runLater(() -> {
                    allSocketsData.clear();
                    allSocketsData.addAll(sockets);
                    
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
                });
            } catch (Exception e) {
                logger.error("Error loading socket data", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading socket data");
                    loadingIndicator.setVisible(false);
                    showErrorAlert("Error", "Failed to load socket statistics", e.getMessage());
                    isRefreshing.set(false);
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
            if (oldState != null && !oldState.equals(newState)) {
                String transition = String.format("Socket [%s] state changed: %s -> %s", key, oldState, newState);
                transitions.add(transition);
                logger.info(transition);
            }
        }
        // Optionally, detect closed sockets (present before, missing now)
        for (String key : previousSocketStates.keySet()) {
            if (!newStates.containsKey(key)) {
                String transition = String.format("Socket [%s] closed (was %s)", key, previousSocketStates.get(key));
                transitions.add(transition);
                logger.info(transition);
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
        String selectedProtocol = protocolFilterCombo.getValue();
        
        if ("All".equals(selectedProtocol)) {
            socketsList.clear();
            socketsList.addAll(allSocketsData);
            statusLabel.setText("Loaded " + allSocketsData.size() + " sockets");
        } else {
            List<Socket> filtered = allSocketsData.stream()
                    .filter(socket -> socket.getProtocol().equalsIgnoreCase(selectedProtocol))
                    .toList();
            socketsList.clear();
            socketsList.addAll(filtered);
            statusLabel.setText("Filtered to " + selectedProtocol.toUpperCase() + 
                    ": " + filtered.size() + " sockets");
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

