import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.io.*;

public class RequestHandler implements Runnable {

    private final Socket clientSocket;
    private static ConfigValues config;

    private String headers; // Raw head contents.
    private byte[] body; // Raw body contents.
    private HashMap<String, String> requestVars;

    public RequestHandler(Socket socket_in) {
        this.clientSocket = socket_in;
    }

    public static void setConfig(ConfigValues values) {
        config = values;
    }

    public void run() {
        try {
            // First let's listen to what the user is asking of us.

            System.err.println("Started thread!");

            readRequest();
            System.out.println("headers:" + headers);
            String bodySTR;
            // Add body and raw request to requestVars for POST and TRACE handling
            if (body != null
                    && (headers.contains("Content-Type: text") || headers.contains("Content-Type: application"))) {
                // Convert body to a Base64 string to not corrupt not textual data
                bodySTR = new String(body);
                requestVars.put("RequestBody", bodySTR);
            } else if (body != null) {
                bodySTR = Base64.getEncoder().encodeToString(body);
                requestVars.put("RequestBody", bodySTR);
            } else {
                bodySTR = Base64.getEncoder().encodeToString(new byte[0]);
                requestVars.put("RequestBody", bodySTR);
            }

            requestVars.put("RawRequest", headers + RequestParser.CRLF + bodySTR);

            // Addressing path traversal and misconfiguration issues
            String requestedIndex = RequestParser.sanitizePath(requestVars.get("Requested-Index"));
            requestVars.replace("Requested-Index", requestedIndex);

            HttpMessage response = handleRequest(requestVars);

            // And now - send it back!
            OutputStream out = clientSocket.getOutputStream();
            System.err.println("Returning response.");
            if (requestVars.containsKey("Transfer-Encoding")
                    && requestVars.get("Transfer-Encoding").equalsIgnoreCase("chunked") &&
                    requestVars.containsKey("chunked") && requestVars.get("chunked").equalsIgnoreCase("yes")) {
                sendChunkedBody(out, response.content);
            } else {
                response.send(out);
            }
        } catch (Exception e) {
            System.err.println("Err caught in thread: " + e);

            // Let's try and send them 500.
            try {
                HttpMessage err = RequestParser.ServerErrorResponse();
                OutputStream out = clientSocket.getOutputStream();
                err.send(out);
            } catch (Exception e2) {
                // If this doesn't work... well, damn shame.
            }
        } finally {
            shutConnection(); // HTTP is stateless, so regardless of what happened, once the transaction is
                              // complete we're done. Bye!
        }
    }

    private HttpMessage handleRequest(HashMap<String, String> requestVars) throws IOException {
        // Implementing missing methods based on the type of request
        switch (requestVars.get("Request-Type")) {
            case "GET":
                System.err.println("CASE GET ENTERED, ind = " + requestVars.get("Requested-Index") + ".");
                // if the given index exists return 200, else, 404.
                return handleGet(requestVars);
            case "POST":
                // Utilize the "RequestBody" from requestVars
                return handlePost(requestVars);
            case "HEAD":
                System.err.println("CASE HEAD ENTERED, ind = " + requestVars.get("Requested-Index") + ".");
                return handleHead(requestVars);
            case "TRACE":
                // Utilize the "RawRequest" from requestVars
                return handleTrace(requestVars);
            default:
                return RequestParser.NotImplementedResponseCode();
        }
    }

    private void readRequest() throws IOException {
        InputStream rawInputStream = clientSocket.getInputStream();
        // Wrap the InputStream in a BufferedInputStream
        BufferedInputStream inputStream = new BufferedInputStream(rawInputStream);
        inputStream.mark(1); // Initial arbitrary mark to support reset later
        ByteArrayOutputStream headersBuffer = new ByteArrayOutputStream();
        int lastByte = -1;
        int newByte;
        // Simple state machine to detect the double CRLF (\r\n\r\n) ending the headers
        // section
        boolean inHeaders = true;
        while (inHeaders && (newByte = inputStream.read()) != -1) {
            if (lastByte == '\r' && newByte == '\n') { // Detect \r\n
                inputStream.mark(2); // Mark the current position to allow reset
                if (inputStream.read() == '\r' && inputStream.read() == '\n') {
                    // Detected end of headers (\r\n\r\n)
                    inHeaders = false;
                } else {
                    // Not the end, reset back to the mark
                    inputStream.reset();
                }
            }
            headersBuffer.write(newByte);
            lastByte = newByte;
        }

        // Convert headers to a string
        headers = headersBuffer.toString(StandardCharsets.UTF_8.name());

        if (headers.isBlank()) {
            throw new IOException("Empty request received.");
        }

        // Parse the headers.
        requestVars = RequestParser.readRequest(headers);

        // Now - is there anything else to the request?
        // We're assuming that if there's no "content-length" that
        // they didn't send anything. Otherwise - they have a malformatted request.
        if (requestVars.get("Content-Length") == null) {
            System.err.println("NO BODY, EXITING readRequest");
            return;
        }
        // int contLengthINT =
        // Integer.parseInt(requestVars.get("Content-Length").trim());
        // String contLength = String.valueOf(contLengthINT);
        // if (contLength == null) {
        // System.err.println("NO BODY, EXITING readRequest");
        // return;
        // }

        // Making sure to reset the inputStream when we read from the beginning of the
        // body
        inputStream.reset();

        if (requestVars.get("Transfer-Encoding") != null && requestVars.get("Transfer-Encoding").equals("chunked")) {
            System.err.println("Chunked encoding detected.");
            String chunkedBody = readChunkedBody(inputStream);
            System.err.println("READ BODY " + chunkedBody);
        } else if (requestVars.containsKey("Content-Length")) {
            System.err.println("Not chunked encoding.");

            // We were given the size of the body by the headers, so let's just read that.
            // Read content as bytes for non-textual data like images
            int contentLength = Integer.parseInt(requestVars.get("Content-Length").trim());
            body = new byte[contentLength];
            int bytesRead = 0;
            while (bytesRead < contentLength) {
                int read = inputStream.read(body, bytesRead, contentLength - bytesRead);
                if (read == -1) {
                    break; // End of stream
                }
                bytesRead += read;
            }

            // Assign the byte array directly as the body
            String bodyAsString = new String(body);
            // Base64.getEncoder().encodeToString(body);

            System.err.println("READ BODY: " + bodyAsString);
        }

        System.err.println("EXITING readRequest");
    }

    private String readChunkedBody(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String sizeLine;
        while (!(sizeLine = reader.readLine()).isEmpty()) {
            int chunkSize = Integer.parseInt(sizeLine.trim(), 16); // Convert hex size to decimal
            if (chunkSize == 0) {
                break; // End of chunks
            }

            byte[] chunk = new byte[chunkSize];
            int bytesRead = 0;
            while (bytesRead < chunkSize) {
                int result = inputStream.read(chunk, bytesRead, chunkSize - bytesRead);
                if (result == -1)
                    break; // End of stream
                bytesRead += result;
            }
            buffer.write(chunk, 0, bytesRead);
            reader.readLine(); // Consume the trailing \r\n after the chunk
        }
        // Convert the binary data in the buffer to a Base64-encoded string
        return Base64.getEncoder().encodeToString(buffer.toByteArray());
    }

    private void sendChunkedBody(OutputStream out, byte[] content) throws IOException {
        if (content != null && content.length > 0) {
            int offset = 0;
            int chunkSize = 1024; // Chunk size (adjust as needed)

            while (offset < content.length) {
                int remaining = content.length - offset;
                chunkSize = Math.min(chunkSize, remaining);
                out.write(Integer.toHexString(chunkSize).getBytes());
                out.write(RequestParser.CRLF.getBytes());
                out.write(content, offset, chunkSize);
                out.write(RequestParser.CRLF.getBytes());
                offset += chunkSize;
            }
        }

        // Send zero-sized chunk to indicate end of chunks
        out.write("0\r\n".getBytes());
        out.write(RequestParser.CRLF.getBytes());
    }

    private HttpMessage createResponse(String HTTPMethod, HashMap<String, String> requestVars) throws IOException {
        String filePath = "";
        try {
            URI uri = new URI(URLEncoder.encode(requestVars.get("Requested-Index"), "UTF-8"));
            filePath = uri.getPath();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Path reqPath = Paths.get(config.getRootPathString(), filePath);

        System.err.println(Files.exists(reqPath));
        // NOTE - This technically would allow for "POST sdadasdasd/params_info.html" to
        // work, but that's not something that would break the server, or security, and
        // requires a very
        // specific and intentional setup. So, I'm allowing this to occur.
        if (reqPath.toString().endsWith("params_info.html") && HTTPMethod.equals("POST")) {
            return RequestParser.ParamsInfoResponse(requestVars);
        } else if (HTTPMethod.equals("POST")) {
            return RequestParser.BadRequestResponseCode();
        } else if (Files.exists(reqPath) && !Files.isDirectory(reqPath)) {
            System.err.println("REQUESTED INDEX EXISTS! ind: " + reqPath);
            // Now let's see which type of file we're returning.
            if (filePath.endsWith(".ico")) {
                // Icon
                return RequestParser.OKIcon(reqPath);
            }
            if (filePath.endsWith(".bmp") || filePath.endsWith(".gif") ||
                    filePath.endsWith(".png") || filePath.endsWith(".jpg")) {
                // Image
                return RequestParser.OKImage(reqPath);
            } else if (filePath.endsWith(".html")) {
                // HTML
                return RequestParser.OKResponseCode(reqPath);
            } else {
                // Octet-Stream
                return RequestParser.OKOther(reqPath);
            }
        } else {
            System.err.println("REQUESTED INDEX DOES NOT EXIST! ind: " + reqPath);
            return RequestParser.NotFoundResponseCode();
        }
    }

    private HttpMessage handleGet(HashMap<String, String> requestVars) throws IOException {
        return createResponse("GET", requestVars);
    }

    private HttpMessage handlePost(HashMap<String, String> requestVars) throws IOException {
        return createResponse("POST", requestVars);
    }

    private HttpMessage handleTrace(HashMap<String, String> requestVars) throws IOException {
        return RequestParser.TRACEResponse(requestVars.get("RawRequest"));
    }

    private HttpMessage handleHead(HashMap<String, String> requestVars) throws IOException {
        HttpMessage message = createResponse("HEAD", requestVars);
        message.sendContent = false;
        return message;
    }

    public void shutConnection() {
        try { // Let's shut down the socket. This is in try/catch because of IOException.
            clientSocket.close();
        } catch (Exception e) {
            System.out.println("RARE ERROR - Socket closed before we could close it.");
        }
    }

    public static void main(String[] args) {
        // This should NOT be run on its own!
    }
}