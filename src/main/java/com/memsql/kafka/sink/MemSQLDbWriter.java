package com.memsql.kafka.sink;

import com.memsql.kafka.utils.DataCompression;
import com.memsql.kafka.utils.JdbcHelper;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.apache.kafka.connect.sink.SinkRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static com.memsql.kafka.sink.MemSQLDialect.KAFKA_METADATA_TABLE;

public class MemSQLDbWriter {

    private static final Logger log = LoggerFactory.getLogger(MemSQLDbWriter.class);
    private final MemSQLSinkConfig config;

    private final int BUFFER_SIZE = 524288;

    public MemSQLDbWriter(MemSQLSinkConfig config) {
        this.config = config;
    }

    public void write(Collection<SinkRecord> records) throws SQLException {
        SinkRecord first = records.iterator().next();
        String table = first.topic();

        String metaId = String.format("%s-%s-%s", first.topic(), first.kafkaPartition(), first.kafkaOffset());
        Integer recordsCount = records.size();

        JdbcHelper.createTableIfNeeded(config, table, first);
        try (PipedOutputStream baseStream  = new PipedOutputStream();
            InputStream inputStream = new PipedInputStream(baseStream, BUFFER_SIZE)) {
            // TODO think about caching connection instead of opening it each time
            try (com.mysql.jdbc.Connection connection = JdbcHelper.isReferenceTable(config, table)
                    ? (com.mysql.jdbc.Connection)JdbcHelper.getDDLConnection(config)
                    : (com.mysql.jdbc.Connection)JdbcHelper.getDMLConnection(config);
                 com.mysql.jdbc.Statement stmt = (com.mysql.jdbc.Statement) connection.createStatement()) {
                connection.setAllowLoadLocalInfile(true);
                stmt.setLocalInfileInputStream(inputStream);
                connection.setAutoCommit(false);

                String metadataQuery = String.format("INSERT INTO `%s` VALUES ('%s', %s)", KAFKA_METADATA_TABLE, metaId, recordsCount);
                try {
                    stmt.executeUpdate(metadataQuery);
                } catch (SQLIntegrityConstraintViolationException ex) {
                    // If metadata record already exists, skip writing this batch of data
                    return;
                }

                DataCompression dataCompression = getDataCompression(config, baseStream);
                try (OutputStream outputStream = dataCompression.getOutputStream()) {
                    String columnNames = JdbcHelper.getSchemaTables(first.valueSchema());
                    String queryPrefix = String.format("LOAD DATA LOCAL INFILE '###.%s'", dataCompression.getExt());
                    String queryEnding = String.format("INTO TABLE `%s` (%s)", table, columnNames);
                    String dataQuery = String.join(" ", queryPrefix, queryEnding);

                    List<byte[]> values = records.stream().map(record ->
                            MemSQLDialect.getRecordValue(record).getBytes(StandardCharsets.UTF_8)
                    ).collect(Collectors.toList());
                    values.forEach(value -> {
                        try {
                            outputStream.write(value);
                            outputStream.write('\n');
                        } catch (IOException ex) {
                            throw new RuntimeException(ex.getLocalizedMessage());
                        }
                    });
                    outputStream.close();
                    stmt.executeUpdate(dataQuery);
                    connection.commit();
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex.getLocalizedMessage());
        }
    }

    private DataCompression getDataCompression(MemSQLSinkConfig config, OutputStream baseStream) {
        try {
            switch (config.dataCompression.toLowerCase()) {
                case "gzip" :
                    return new DataCompression("gz", new GZIPOutputStream(baseStream));
                case "lz4" :
                    return new DataCompression("lz4", new LZ4FrameOutputStream(baseStream));
                case "skip" :
                    return new DataCompression("tsv", baseStream);
                default:
                    throw new IllegalArgumentException(String.format("Invalid data compression type. Type %s doesn't exist", config.dataCompression));
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex.getLocalizedMessage());
        }
    }

}
