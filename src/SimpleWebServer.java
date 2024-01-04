import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class SimpleWebServer {
    private static final int PORT = 2912;
    private static ServerSocket serverSocket;
    private static volatile boolean running = true;

    public static void main(String[] args) throws IOException {
        serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } finally {
            serverSocket.close();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = clientSocket.getOutputStream();

                String requestLine = in.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    closeConnection();
                    return;
                }

                StringTokenizer tokenizer = new StringTokenizer(requestLine);
                String method = tokenizer.nextToken().toUpperCase();
                String fileRequested = tokenizer.nextToken();

                if (method.equals("GET")) {
                    handleGetRequest(fileRequested, out);
                } else if (method.equals("POST")) {
                    handlePostRequest(fileRequested, in, out);
                } else {
                    sendHttpResponse(out, 405, "Method Not Allowed", "text/html",
                            "<html><body><h1>405 Method Not Allowed</h1></body></html>");
                }

                closeConnection();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handleGetRequest(String fileRequested, OutputStream out) throws IOException {
            Path filePath = Paths.get(".", fileRequested);
            System.out.println("Client GET " + filePath);
            if (!Files.exists(filePath)) {
                sendHttpResponse(out, 404, "Not Found", "text/html",
                        "<html><body><h1>404 Not Found</h1></body></html>");
                return;
            }

            String contentType = Files.probeContentType(filePath);
            byte[] fileData = Files.readAllBytes(filePath);
            sendHttpResponse(out, 200, "OK", contentType, fileData);
        }

        private void handlePostRequest(String fileRequested, BufferedReader in, OutputStream out) throws IOException {
            if (!fileRequested.equals("/html/dopost")) {
                sendHttpResponse(out, 404, "Not Found", "text/html",
                        "<html><body><h1>404 Not Found</h1></body></html>");
                return;
            }

            // Read and parse headers to find Content-Length
            String line;
            int contentLength = 0;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring("Content-Length:".length()).trim());
                }
            }

            // Read the POST data according to Content-Length
            char[] buffer = new char[contentLength];
            in.read(buffer, 0, contentLength);
            String postData = new String(buffer);

            Map<String, String> params = parsePostData(postData);
            System.out.println("Client POST " + params.get("login")+ " " + params.get("pass"));

            String responseMessage;
            if ("3210102912".equals(params.get("login")) && "2912".equals(params.get("pass"))) {
                responseMessage = "<html><body><h1>Login Successful</h1></body></html>";
            } else {
                responseMessage = "<html><body><h1>Login Failed</h1></body></html>";
            }

            sendHttpResponse(out, 200, "OK", "text/html", responseMessage);
        }

        private void sendHttpResponse(OutputStream out, int statusCode, String statusMessage, String contentType, String content) throws IOException {
            out.write(("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n").getBytes());
            out.write(("Content-Type: " + contentType + "\r\n").getBytes());
            out.write(("Content-Length: " + content.length() + "\r\n").getBytes());
            out.write("\r\n".getBytes());
            out.write(content.getBytes());
            out.flush();
        }

        private void sendHttpResponse(OutputStream out, int statusCode, String statusMessage, String contentType, byte[] content) throws IOException {
            out.write(("HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n").getBytes());
            out.write(("Content-Type: " + contentType + "\r\n").getBytes());
            out.write(("Content-Length: " + content.length + "\r\n").getBytes());
            out.write("\r\n".getBytes());
            out.write(content);
            out.flush();
        }

        private Map<String, String> parsePostData(String data) {
            Map<String, String> params = new HashMap<>();
            for (String param : data.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1) {
                    params.put(pair[0], pair[1]);
                }
            }
            return params;
        }

        private void closeConnection() throws IOException {
            clientSocket.close();
        }
    }
}
