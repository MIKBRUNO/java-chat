package messages.parsing;

import messages.Message;

import java.io.InputStream;
import java.io.OutputStream;

public interface MessageReadWrite {
    Message parse(InputStream in) throws ParsingException;
    void encode(OutputStream out, Message message) throws ParsingException;
}
