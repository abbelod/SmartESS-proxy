import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.Timestamp;

public class ModbusClient implements Runnable {

    private Engine engine;
    private volatile Socket srv;
    private volatile InputStream inputStream;
    private volatile OutputStream outputStream;
    private final Object socketLock = new Object();
    private volatile boolean shouldStop = false;

    public ModbusClient(Engine engine) throws IOException {
        this.engine = engine;
    }

    public void run() {
        while (!shouldStop) {
            try {
                establishConnection();
                handleIncomingData();
                
            } catch (Exception e) {
                String time = new Timestamp(System.currentTimeMillis()).toString();
                System.out.println(time + " - Connection error: " + e.getMessage());
                closeConnection();
                
                // Wait before reconnecting (exponential backoff could be added here)
                if (!shouldStop) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void establishConnection() throws IOException {
        synchronized (socketLock) {
            closeConnection(); // Ensure clean state
            
            srv = new Socket();
            srv.setSoTimeout(5000); // 5 second socket timeout
            srv.connect(new java.net.InetSocketAddress(engine.realModbusServer, 502), 10000);
            
            inputStream = srv.getInputStream();
            outputStream = srv.getOutputStream();
            
            String time = new Timestamp(System.currentTimeMillis()).toString();
            System.out.println(time + " - Connected to server " + srv.getInetAddress().getHostAddress());
        }
    }

    private void handleIncomingData() throws IOException, InterruptedException {
        byte[] buffer = new byte[1024]; // Use a fixed buffer instead of available()
        
        while (isConnected() && !shouldStop) {
            try {
                // Use blocking read with timeout instead of available()
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead == -1) {
                    // End of stream - connection closed by server
                    System.out.println("Server closed connection");
                    break;
                }
                
                if (bytesRead > 0) {
                    // Copy only the bytes that were actually read
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    
                    String hex = Engine.bytesToHex(data);
                    String time = new Timestamp(System.currentTimeMillis()).toString();
                    System.out.println(time + " - Server: " + hex);
                    
                    int ret = engine.nsrv.sendData(data);
                    if (ret == -1) break;
                }
                
            } catch (SocketTimeoutException e) {
                // Socket timeout - continue loop to check connection status
                continue;
            } catch (IOException e) {
                // Connection broken
                throw e;
            }
        }
    }

    public int sendData(byte[] data) throws InterruptedException {
        if (data == null || data.length == 0) {
            return -1;
        }

        // Wait for connection with timeout
        if (!waitForConnection(10000)) {
            System.out.println("Timeout waiting for server connection");
            return -1;
        }

        synchronized (socketLock) {
            try {
                if (!isConnected()) {
                    return -1;
                }

                outputStream.write(data);
                outputStream.flush();
                
                String time = new Timestamp(System.currentTimeMillis()).toString();
                System.out.println(time + " - Sent " + data.length + " bytes to server");

            } catch (IOException e) {
                String time = new Timestamp(System.currentTimeMillis()).toString();
                System.out.println(time + " - Write failed: " + e.getMessage());
                closeConnection();
                return -1;
            }
        }
        return 0;
    }

    private boolean isConnected() {
        synchronized (socketLock) {
            return srv != null && 
                   srv.isConnected() && 
                   !srv.isClosed() && 
                   inputStream != null &&
                   outputStream != null;
        }
    }

    private boolean waitForConnection(long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        
        while (!isConnected() && !shouldStop) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return false;
            }
            
            if (startTime == System.currentTimeMillis()) { // Only print once
                String time = new Timestamp(System.currentTimeMillis()).toString();
                System.out.println(time + " - Waiting for server connection...");
            }
            
            Thread.sleep(100);
        }
        return isConnected();
    }

    private void closeConnection() {
        synchronized (socketLock) {
            // Close streams first
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
                inputStream = null;
            }
            
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
                outputStream = null;
            }
            
            // Close socket
            if (srv != null) {
                try {
                    srv.close();
                } catch (IOException e) {
                    // Ignore
                }
                srv = null;
            }
        }
    }

    public void shutdown() {
        shouldStop = true;
        closeConnection();
    }
}