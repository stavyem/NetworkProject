import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

// Parses and generates appropriately-typed requests.
public class RequestParser {

    static final String CRLF = "\r\n";
    static ConfigValues CONFIG;

    public static void setConfigValues(ConfigValues val) { // Run once on startup.
        CONFIG = val;
    }

    enum ContentType {
        Text("text/html"),
        Image("image"),
        Icon("icon"),
        Stream("application/octet-stream");

        String value;

        private ContentType(String val) {
            value = val;
        }

        public String toString() {
            return value;
        }
    }

    // The types of responses we're sending back.
    enum ResponseType {
        OK("200 OK"), // 200 - OK
        NotFound("404 Not Found"), // 404 - Not Found
        NotImplemented("501 Not Implemented"), // 501 - Not Implemented
        BadRequest("400 Bad Request"), // 400 - Invalid Request Format
        InternalError("500 Internal Error"); // 500 - Internal Server Error

        String value;

        private ResponseType(String val) {
            value = val;
        }

        public String toString() {
            return value;
        }
    }

    public static HashMap<String, String> readRequest(String request) {
        HashMap<String, String> organizedContents = new HashMap<>();

        // First let's take our request and break it down into manageable sections.
        String[] requestArray = request.split("\n"); // NOTE - Might be CRLF and not \n? Switch if bugs found.

        // In line one we have the request type, the requested index, and the HTTP type.
        // Let's get the first two.
        String[] firstVars = requestArray[0].split(" ");
        organizedContents.put("Request-Type", firstVars[0]);
        organizedContents.put("Requested-Index", firstVars[1]);

        for (int i = 1; i < requestArray.length; i++) {
            // Now, for each line, figure out what info they're supplying and format it.
            // Outside of line 1, which we handled, every line is [variable]: [contents].
            int separator = requestArray[i].indexOf(": ");
            if (separator != -1) {
                organizedContents.put(requestArray[i].substring(0, separator), // variable
                        requestArray[i].substring(separator + 2)); // contents
            }
        }

        return organizedContents;
    }

    static String sanitizePath(String requestedPath) {
        // Handle the special case of a request for the root ("/"), replacing it with
        // the default page
        if (requestedPath.equals("/")) {
            requestedPath = CONFIG.getDefaultPage();
        }

        // Handle tilde ("~") at the start of the path
        if (requestedPath.startsWith("~")) {
            // Replace "~" with the user's home directory on Unix-like systems
            // You might log a warning or handle it differently on Windows or other systems
            String homeDir = System.getProperty("user.home");
            requestedPath = requestedPath.replaceFirst("~", homeDir);
        }

        // Resolve the requested path against the server's root directory and normalize
        Path rootPath = Paths.get(CONFIG.getRootPathString()).toAbsolutePath().normalize();
        Path resolvedPath = rootPath.resolve(requestedPath.replaceFirst("^/+", "")).normalize(); // Remove leading
                                                                                                 // slashes before
                                                                                                 // resolving

        // Prevent directory traversal attacks by checking if the resolved path is
        // outside the root directory
        if (!resolvedPath.startsWith(rootPath)) {
            throw new SecurityException("Invalid path - attempted directory traversal attack");
        }

        // Return the resolved path as a string relative to the server's root directory
        // This ensures the path is safe to use for file operations within the server's
        // content directory
        return rootPath.relativize(resolvedPath).toString();
    }

    // Fundamental function everything else uses;
    // Given the parameters, constructs and returns the
    // message that should be sent.
    public static String basicHeaderFormat(ResponseType response,
            ContentType contentType, int contentLength) {

        String message = ""; // We'll construct this message.

        // Let's fill in depending on the response type.
        message += "HTTP/1.1 " + response + CRLF;

        // Insert if contentType is not empty.
        if (contentType != null) {
            message += "content-type: " + contentType + CRLF;
        }

        if (contentLength != 0) {
            message += "content-length: " + Integer.toString(contentLength) + CRLF;
        }

        return message;
    }

    public static HttpMessage sendGivenHTMLFile(ResponseType response, Path path) throws IOException {
        String fileContents = Files.readString(path);
        System.err.println("RequestParser successfuly read html file " + path);

        HttpMessage message = new HttpMessage();
        message.setHeaders(basicHeaderFormat(response, ContentType.Text, fileContents.length()));
        message.setContent(fileContents);
        return message;
    }

    public static HttpMessage OKResponseCode(Path path) throws IOException {
        return sendGivenHTMLFile(ResponseType.OK, path);
    }

    public static HttpMessage OKImage(Path path) throws IOException {
        byte[] image = Files.readAllBytes(path);
        System.err.println("RequestParser successfuly read image file " + path);

        HttpMessage message = new HttpMessage();
        message.setHeaders(basicHeaderFormat(ResponseType.OK, ContentType.Image, image.length));
        System.out.println(message.headers);
        message.setContent(image);
        return message;
    }

    public static HttpMessage OKIcon(Path path) throws IOException {
        byte[] image = Files.readAllBytes(path);
        System.err.println("RequestParser successfuly read icon file " + path);

        HttpMessage message = new HttpMessage();
        message.setHeaders(basicHeaderFormat(ResponseType.OK, ContentType.Icon, image.length));
        message.setContent(image);
        return message;
    }

    public static HttpMessage OKOther(Path path) throws IOException {
        byte[] file = Files.readAllBytes(path);
        System.err.println("RequestParser successfuly read file " + path);

        HttpMessage message = new HttpMessage();
        message.setHeaders(basicHeaderFormat(ResponseType.OK, ContentType.Stream, file.length));
        message.setContent(file);
        return message;
    }

    public static HttpMessage TRACEResponse(String request) {
        HttpMessage message = new HttpMessage();
        message.setHeaders(basicHeaderFormat(ResponseType.OK, ContentType.Text, request.length()));
        System.out.println(message.headers);
        message.setContent(request);
        return message;
    }

    public static HttpMessage ServerErrorResponse() {
        HttpMessage message = new HttpMessage();
        message.setHeaders(basicHeaderFormat(ResponseType.InternalError, null, 0));
        message.setSendContent(false);
        return message;
    }

    private static HashMap<String, String> parseFormData(String body) {
        HashMap<String, String> formData = new HashMap<>();
        String[] params = body.replace("\r\n", "").trim().split("&"); // Remove CRLF and trim whitespace
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];
                formData.put(key, value);
            } else if (keyValue.length == 1) {
                String key = keyValue[0];
                formData.put(key, "");
            }
        }
        return formData;
    }

    public static String generateHTML(HashMap<String, String> formData) {
        StringBuilder writer = new StringBuilder();
        writer.append("<!DOCTYPE html>\n<html>\n<head>\n<title>Form Data</title>\n</head>\n<body>\n");
        writer.append("<h1>Form Data</h1>\n<ul>\n");
        for (String key : formData.keySet()) {
            String value = formData.get(key);
            writer.append("<li>" + key + ": " + value + "</li>\n");
        }
        writer.append("</ul>\n</body>\n</html>");

        return writer.toString();
    }

    public static HttpMessage ParamsInfoResponse(HashMap<String, String> requestVars) throws IOException {
        String body = requestVars.get("RequestBody");
        HashMap<String, String> formData = parseFormData(body);
        String html = generateHTML(formData);

        HttpMessage message = new HttpMessage();
        message.setContent(html);
        message.setHeaders(basicHeaderFormat(ResponseType.OK, ContentType.Text, html.length()));
        return message;
    }

    public static HttpMessage NotFoundResponseCode() throws IOException {
        Path path = CONFIG.getRootPath().resolve("_404.html");
        return sendGivenHTMLFile(ResponseType.NotFound, path);
    }

    public static HttpMessage NotImplementedResponseCode() throws IOException {
        Path path = CONFIG.getRootPath().resolve("_501.html");
        return sendGivenHTMLFile(ResponseType.NotImplemented, path);
    }

    public static HttpMessage BadRequestResponseCode() throws IOException {
        Path path = CONFIG.getRootPath().resolve("_400.html");
        return sendGivenHTMLFile(ResponseType.BadRequest, path);
    }

    public static void main(String[] args) {
        System.out.println("TESTING REQUESTPARSER\nSetting up config");
        try {
            CONFIG = new ConfigValues("config.ini");
            System.out.println("NotFoundResponseCode: " + NotFoundResponseCode());
            System.out.println("NotImplementedResponseCode: " + NotImplementedResponseCode());
        } catch (Exception e) {
            System.err.println("Error caught: " + e);
        }
    }
}
