package com.tianshu.backend;

import org.testcontainers.containers.MySQLContainer;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Never connects to a developer's existing database. Uses a container or an isolated local process. */
final class MySqlTestDatabase implements AutoCloseable {
    String url;
    final String username;
    final String password;
    private final MySQLContainer<?> container;
    private final Process process;

    MySqlTestDatabase() {
        String binary = System.getenv("MYSQL_TEST_BINARY");
        if (binary == null || binary.isBlank()) {
            container = new MySQLContainer<>("mysql:8.4.13").withDatabaseName("f19_f20_it");
            container.start();
            url = container.getJdbcUrl() + "?serverTimezone=UTC";
            username = container.getUsername(); password = container.getPassword(); process = null;
        } else {
            container = null;
            try {
                Path executable = Path.of(binary).toAbsolutePath().normalize();
                Path root = Files.createTempDirectory("sky-f19-f20-mysql-");
                Path data = Files.createDirectory(root.resolve("data"));
                Path initLog = root.resolve("initialize.log");
                var init = new ProcessBuilder(executable.toString(), "--no-defaults", "--initialize-insecure",
                        "--datadir=" + data).redirectErrorStream(true).redirectOutput(initLog.toFile()).start();
                if (!init.waitFor(60, TimeUnit.SECONDS) || init.exitValue() != 0) {
                    init.destroyForcibly(); throw new IllegalStateException("MySQL init failed: " + initLog);
                }
                int port;
                try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
                String schema = "f19_f20_it_" + UUID.randomUUID().toString().replace("-", "");
                username = "f19_it"; password = UUID.randomUUID().toString();
                Path bootstrap = root.resolve("bootstrap.sql");
                Files.writeString(bootstrap, "CREATE DATABASE " + schema + " CHARACTER SET utf8mb4;\n"
                        + "CREATE USER 'f19_it'@'127.0.0.1' IDENTIFIED BY '" + password + "';\n"
                        + "GRANT ALL ON " + schema + ".* TO 'f19_it'@'127.0.0.1';\n");
                Path log = root.resolve("server.log");
                process = new ProcessBuilder(executable.toString(), "--no-defaults", "--console", "--datadir=" + data,
                        "--port=" + port, "--bind-address=127.0.0.1", "--mysqlx=OFF", "--skip-name-resolve", "--init-file=" + bootstrap)
                        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> process.destroy()));
                String rootUrl = "jdbc:mysql://127.0.0.1:" + port + "/" + schema + "?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false";
                long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
                boolean ready = false;
                java.sql.SQLException lastConnectionFailure = null;
                Class.forName("com.mysql.cj.jdbc.Driver");
                while (System.nanoTime() < deadline && process.isAlive()) {
                    try (var connection = DriverManager.getConnection(rootUrl, username, password)) {
                        if (!connection.isValid(2)) continue;
                        url = rootUrl;
                        ready = true;
                        Files.deleteIfExists(bootstrap);
                        System.out.println("F19/F20 isolated MySQL logs: " + log);
                        return;
                    } catch (java.sql.SQLException e) { lastConnectionFailure = e; Thread.sleep(150); }
                }
                if (!ready) process.destroyForcibly();
                throw new IllegalStateException("Isolated MySQL did not become ready: " + log, lastConnectionFailure);
            } catch (Exception e) { throw new IllegalStateException("Isolated MySQL setup failed", e); }
        }
    }

    @Override public void close() {
        if (container != null) container.stop();
        if (process != null) {
            process.destroy();
            try { if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroyForcibly(); }
            catch (InterruptedException e) { process.destroyForcibly(); Thread.currentThread().interrupt(); }
        }
    }
}
