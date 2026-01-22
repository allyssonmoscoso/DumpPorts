package com.dumpports.ui;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dumpports.model.Socket;
import com.dumpports.service.SocketStatisticsService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller for the main window of the DumpPorts application.
 * Manages the UI interactions and coordination between services and views.
 */
public class MainWindowController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(MainWindowController.class);

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
    private Label statusLabel;
    @FXML
    private ProgressIndicator loadingIndicator;

    private final SocketStatisticsService socketService = new SocketStatisticsService();
    private ObservableList<Socket> socketsList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        logger.info("Initializing MainWindowController");
        
        setupTableColumns();
        setupProtocolFilter();
        setupRefreshButton();
        loadSocketData();
    }

    private void setupTableColumns() {
        protocolColumn.setCellValueFactory(new PropertyValueFactory<>("protocol"));
        stateColumn.setCellValueFactory(new PropertyValueFactory<>("state"));
        localAddressColumn.setCellValueFactory(new PropertyValueFactory<>("localAddress"));
        localPortColumn.setCellValueFactory(new PropertyValueFactory<>("localPort"));
        remoteAddressColumn.setCellValueFactory(new PropertyValueFactory<>("remoteAddress"));
        remotePortColumn.setCellValueFactory(new PropertyValueFactory<>("remotePort"));
        processColumn.setCellValueFactory(new PropertyValueFactory<>("process"));

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

    @FXML
    private void loadSocketData() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("Loading socket statistics...");

        Thread loadThread = new Thread(() -> {
            try {
                List<Socket> sockets = socketService.getSocketStatistics();
                
                Platform.runLater(() -> {
                    socketsList.clear();
                    socketsList.addAll(sockets);
                    statusLabel.setText("Loaded " + sockets.size() + " sockets");
                    loadingIndicator.setVisible(false);
                    logger.info("Socket data loaded successfully");
                });
            } catch (Exception e) {
                logger.error("Error loading socket data", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading socket data");
                    loadingIndicator.setVisible(false);
                    showErrorAlert("Error", "Failed to load socket statistics", e.getMessage());
                });
            }
        });
        
        loadThread.setDaemon(true);
        loadThread.start();
    }

    @FXML
    private void filterSockets() {
        String selectedProtocol = protocolFilterCombo.getValue();
        
        if ("All".equals(selectedProtocol)) {
            loadSocketData();
        } else {
            List<Socket> filtered = socketService.getSocketsByProtocol(selectedProtocol);
            Platform.runLater(() -> {
                socketsList.clear();
                socketsList.addAll(filtered);
                statusLabel.setText("Filtered to " + selectedProtocol.toUpperCase() + 
                        ": " + filtered.size() + " sockets");
            });
        }
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
