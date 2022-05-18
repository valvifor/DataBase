package ru.nsu.pharmacydatabase.controllers.select;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import ru.nsu.pharmacydatabase.*;
import ru.nsu.pharmacydatabase.controllers.base.EntranceController;
import ru.nsu.pharmacydatabase.controllers.filter.Filter;
import ru.nsu.pharmacydatabase.controllers.filter.Filters;
import ru.nsu.pharmacydatabase.controllers.insert.InsertController;
import ru.nsu.pharmacydatabase.utils.Connection;
import ru.nsu.pharmacydatabase.utils.DBInit;
import ru.nsu.pharmacydatabase.controllers.insert.InsertMode;
import ru.nsu.pharmacydatabase.utils.Tables;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class TableController implements Initializable {
    private String tableName;
    private final Connection connection = Main.getConnection();
    private final LinkedList<TableColumn<Map, String>> columns = new LinkedList<>();
    private final ObservableList<Map<String, Object>> items = FXCollections.<Map<String, Object>>observableArrayList();
    private final LinkedList<String> columnNames = new LinkedList<>();
    private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.ENGLISH);
    private final SimpleDateFormat formatter2 = new SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH);
    private final int rowsPerPage = 8;

    @FXML
    private TableView tableView;
    @FXML
    private Button filterButton;
    @FXML
    private Pagination pagination;


    public TableController() {
    }

    @FXML
    private void removeButtonTapped() {
        Object itemToRemove = tableView.getSelectionModel().getSelectedItem();
        String item = itemToRemove.toString();
        int id = DBInit.getIdFrom(item);
        if (tableName.equals("ORDER_")) {
            connection.delete("DELETE FROM " + tableName + " WHERE " + tableName + "ID LIKE " + id);
        } else {
            connection.delete("DELETE FROM " + tableName + " WHERE " + tableName + "_ID LIKE " + id);
        }
        tableView.getItems().removeAll(itemToRemove);
        try {
            loadData();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    @FXML
    private void insertButtonTapped() {
        configureWindow(InsertMode.insert);
    }

    @FXML
    private void updateButtonTapped() {
        if(tableView.getSelectionModel().getSelectedItem() != null) {
            configureWindow(InsertMode.update);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tableView.setItems(items);
        pagination.setPageFactory(this::createPage);
    }

    private Node createPage(int pageIndex) {
        int fromIndex = pageIndex * rowsPerPage;
        int toIndex = Math.min(fromIndex + rowsPerPage, items.size());
        if (fromIndex > toIndex) {
            toIndex = fromIndex + rowsPerPage;
            int iter = toIndex - items.size();
            for (int i = 0; i < iter; i++) {
                Map<String, Object> map = new HashMap<>();
                for (int j = 1; j <= toIndex; j++) {
                    String value = "";
                    map.put("", value);
                }
                items.add(map);
            }
        }
        tableView.setItems(FXCollections.observableList(items.subList(fromIndex, toIndex)));
        return tableView;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
        System.out.println("TABLE NAME: " + tableName);
    }

    private void configureWindow(InsertMode mode) {
        String windowName = "";
        ChangeListener listener = (observable, oldValue, newValue) -> {
            try {
                loadData();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        };

        Tables tableType = Tables.getTableByName(tableName);
        windowName = tableType.getWindowName();
        Stage stage = new Stage();
        FXMLLoader loader = new FXMLLoader();
        Parent root = null;
        try {
            root = loader.load(getClass().getResourceAsStream(windowName));
        } catch (IOException e) {
            e.printStackTrace();
        }

        InsertController controller = loader.getController();
        controller.setListener(listener);
        controller.setMode(mode);

        if (mode == InsertMode.update) {
            Object itemToUpdate = tableView.getSelectionModel().getSelectedItem();
            String item = itemToUpdate.toString();
            controller.setItem(item);
            stage.setTitle("Update " + tableName);
        } else {
            stage.setTitle("Insert to " + tableName);
        }
        assert root != null;
        stage.setScene(new Scene(root));
        stage.show();
    }

    public void loadData() throws SQLException {
        if (!tableName.equals("ORDER_")) {
            filterButton.setDisable(true);
        }
        items.clear();
        columns.clear();
        String operation;
        if (tableName.equals("ORDER_")) {
            operation = "SELECT ord.order_id, med.title, ord.is_ready, ord.is_received, ord.start_date " +
                    "FROM order_ ord " +
                    "INNER JOIN medicament med " +
                    "ON med.medicament_id = ord.medicam_id";
        } else if (tableName.equals("REQUEST")) {
            operation = "SELECT request.request_id, provider.provider_name " +
                    "FROM request " +
                    "INNER JOIN provider " +
                    "ON provider.provider_id = request.provider_id";
        } else {
            operation = "SELECT * FROM " + tableName;
        }
        ResultSet set = connection.executeQueryAndGetResult(operation);
        ResultSetMetaData metaData = set.getMetaData();
        int columnSize = set.getMetaData().getColumnCount();
        try {
            for(int i = 1; i <= columnSize; i++) {
                String columnName = metaData.getColumnName(i);
                TableColumn<Map, String> column = new TableColumn<>(columnName);
                column.setCellValueFactory(new MapValueFactory<>(columnName));
                column.setMinWidth(40);
                columns.add(column);
                columnNames.add(columnName);
            }
            tableView.getColumns().setAll(columns);
            for(int i = 1; set.next(); ++i) {
                Map<String, Object> map = new HashMap<>();
                for(int j = 1; j <= columnSize; j++) {
                    String value = set.getString(j);
                    if (value == null) {
                        value = "";
                    }
                    try {
                        Date date = formatter.parse(value);
                        value = formatter2.format(date);
                    } catch (ParseException ignore) {
                    }
                    map.put(columnNames.get(j - 1), value);
                }
                items.add(map);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    public void filterButtonTapped () throws IOException {
        Stage stage = new Stage();
        InputStream inputStream = getClass().getResourceAsStream("/ru/nsu/pharmacydatabase/windows/filter/order_filter.fxml");
        Parent root = new FXMLLoader().load(inputStream);
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

}
