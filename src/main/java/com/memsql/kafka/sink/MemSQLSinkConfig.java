package com.memsql.kafka.sink;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.types.Password;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemSQLSinkConfig extends AbstractConfig {

    private static final String CONNECTION_GROUP = "Connection";
    private static final String RETRY_GROUP = "Retry";

    public static final String DDL_ENDPOINT = "connection.ddlEndpoint";
    private static final String DDL_ENDPOINT_DOC =
            "Hostname or IP address of the MemSQL Master Aggregator in the format `host[:port]`";
    private static final String DDL_ENDPOINT_DISPLAY = "DDL Endpoint";

    public static final String DML_ENDPOINTS = "connection.dmlEndpoints";
    private static final String DML_ENDPOINTS_DOC =
            "Hostname or IP address of MemSQL Aggregator nodes to run queries against " +
                    "in the format host[:port],host[:port],... (port is optional, multiple hosts separated by comma). " +
                    "Example: child-agg:3308,child-agg2 (default: ddlEndpoint)";
    private static final String DML_ENDPOINTS_DISPLAY = "DML Endpoints";

    public static final String CONNECTION_DATABASE = "connection.database";
    private static final String CONNECTION_DATABASE_DOC = "MemSQL connection database.";
    private static final String CONNECTION_DATABASE_DISPLAY = "MemSQL Database";

    public static final String CONNECTION_USER = "connection.user";
    private static final String CONNECTION_USER_DOC = "MemSQL connection user.";
    private static final String CONNECTION_USER_DISPLAY = "MemSQL User";

    public static final String CONNECTION_PASSWORD = "connection.password";
    private static final String CONNECTION_PASSWORD_DOC = "MemSQL connection password.";
    private static final String CONNECTION_PASSWORD_DISPLAY = "MemSQL Password";

    public static final String SQL_PARAMETERS = "params.<value>";
    private static final String SQL_PARAMETERS_DOC = "Additional parameters for sql queries";
    private static final String SQL_PARAMETERS_DISPLAY = "SQL Parameters";

    public static final String MAX_RETRIES = "max.retries";
    private static final String MAX_RETRIES_DOC = "The maximum number of times to retry on errors before failing the task.";
    private static final String MAX_RETRIES_DISPLAY = "Maximum Retries";

    public static final String RETRY_BACKOFF_MS = "retry.backoff.ms";
    private static final String RETRY_BACKOFF_MS_DOC = "The time in milliseconds to wait following an error before a retry attempt is made.";
    private static final String RETRY_BACKOFF_MS_DISPLAY = "Retry Backoff (millis)";

    private static final ConfigDef.Range NON_NEGATIVE_INT_VALIDATOR = ConfigDef.Range.atLeast(0);

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(
                    DDL_ENDPOINT,
                    ConfigDef.Type.STRING,
                    ConfigDef.NO_DEFAULT_VALUE,
                    ConfigDef.Importance.HIGH,
                    DDL_ENDPOINT_DOC,
                    CONNECTION_GROUP,
                    1,
                    ConfigDef.Width.LONG,
                    DDL_ENDPOINT_DISPLAY
            )
            .define(
                    DML_ENDPOINTS,
                    ConfigDef.Type.LIST,
                    null,
                    ConfigDef.Importance.MEDIUM,
                    DML_ENDPOINTS_DOC,
                    CONNECTION_GROUP,
                    2,
                    ConfigDef.Width.LONG,
                    DML_ENDPOINTS_DISPLAY
            )
            .define(
                    CONNECTION_DATABASE,
                    ConfigDef.Type.STRING,
                    ConfigDef.NO_DEFAULT_VALUE,
                    ConfigDef.Importance.HIGH,
                    CONNECTION_DATABASE_DOC,
                    CONNECTION_GROUP,
                    3,
                    ConfigDef.Width.MEDIUM,
                    CONNECTION_DATABASE_DISPLAY
            )
            .define(
                    CONNECTION_USER,
                    ConfigDef.Type.STRING,
                    null,
                    ConfigDef.Importance.HIGH,
                    CONNECTION_USER_DOC,
                    CONNECTION_GROUP,
                    4,
                    ConfigDef.Width.MEDIUM,
                    CONNECTION_USER_DISPLAY
            )
            .define(
                    CONNECTION_PASSWORD,
                    ConfigDef.Type.PASSWORD,
                    null,
                    ConfigDef.Importance.HIGH,
                    CONNECTION_PASSWORD_DOC,
                    CONNECTION_GROUP,
                    5,
                    ConfigDef.Width.MEDIUM,
                    CONNECTION_PASSWORD_DISPLAY
            )
            .define(
                    SQL_PARAMETERS,
                    ConfigDef.Type.STRING,
                    null,
                    ConfigDef.Importance.LOW,
                    SQL_PARAMETERS_DOC,
                    CONNECTION_GROUP,
                    6,
                    ConfigDef.Width.MEDIUM,
                    SQL_PARAMETERS_DISPLAY
            )
            .define(MAX_RETRIES,
                    ConfigDef.Type.INT,
                    10,
                    NON_NEGATIVE_INT_VALIDATOR,
                    ConfigDef.Importance.MEDIUM,
                    MAX_RETRIES_DOC,
                    RETRY_GROUP,
                    1,
                    ConfigDef.Width.SHORT,
                    MAX_RETRIES_DISPLAY)
            .define(RETRY_BACKOFF_MS,
                    ConfigDef.Type.INT,
                    3000,
                    NON_NEGATIVE_INT_VALIDATOR,
                    ConfigDef.Importance.MEDIUM,
                    RETRY_BACKOFF_MS_DOC,
                    RETRY_GROUP,
                    2,
                    ConfigDef.Width.SHORT,
                    RETRY_BACKOFF_MS_DISPLAY);

    public final String ddlEndpoint;
    public final List<String> dmlEndpoints;
    public final String database;
    public final String user;
    public final String password;
    public final Map<String, String> sqlParams;
    public final int maxRetries;
    public final int retryBackoffMs;

    public MemSQLSinkConfig(Map<?, ?> props) {
        super(CONFIG_DEF, props);
        this.ddlEndpoint = getString(DDL_ENDPOINT);
        this.dmlEndpoints = getList(DML_ENDPOINTS);
        this.database = getString(CONNECTION_DATABASE);
        this.user = getString(CONNECTION_USER);
        this.password = getPasswordValue(CONNECTION_PASSWORD);
        this.sqlParams = getSqlParams(props);
        this.maxRetries = getInt(MAX_RETRIES);
        this.retryBackoffMs = getInt(RETRY_BACKOFF_MS);
    }

    private Map<String, String> getSqlParams(Map<?, ?> props) {
        String paramsPrefix = "params.";
        Map<String, String> sqlParams = new HashMap<>();
        props.keySet().stream()
                .filter(key -> ((String)key).startsWith(paramsPrefix))
                .forEach(key -> sqlParams.put(((String) key).substring(paramsPrefix.length()), getString((String)key)));
        return sqlParams;
    }

    private String getPasswordValue(String key) {
        Password password = getPassword(key);
        if (password != null) {
            return password.value();
        }
        return null;
    }

    public static void main(String... args) {
        System.out.println(CONFIG_DEF.toEnrichedRst());
    }
}
