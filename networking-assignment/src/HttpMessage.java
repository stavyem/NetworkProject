
// A class meant to hold the headers and message content separately.
import java.io.*;

public class HttpMessage {
    byte[] headers;
    byte[] content;
    boolean sendContent = true;

    // Sends headers and content over given outputstream.
    public void send(OutputStream out) throws IOException {
        out.write(headers);
        System.err.println(new String(headers));
        out.write(RequestParser.CRLF.getBytes());
        if (sendContent) {
            if (content != null && content.length > 0) {
                out.write(content);
            }
        }
    } // todo - make new function that sends back chunked

    public void setHeaders(String head) {
        headers = head.getBytes();
    }

    public void setHeaders(byte[] head) {
        headers = head;
    }

    public void setContent(String cont) {
        content = cont.getBytes();
    }

    public void setContent(byte[] cont) {
        content = cont;
    }

    public void setSendContent(boolean set) {
        sendContent = set;
    }
}