package messages.parsing.serialization;

import messages.Message;
import messages.parsing.MessageReadWrite;
import messages.parsing.ParsingException;

import java.io.*;

public class SerializationParser implements MessageReadWrite {

    @Override
    public Message parse(InputStream in) throws ParsingException {
        try {
            var stream = new ObjectInputStream(in);
            return (Message) stream.readObject();
        }
        catch (IOException | ClassNotFoundException e) {
            throw new ParsingException(e);
        }
    }

    @Override
    public void encode(OutputStream out, Message message) throws ParsingException {
        try {
            var stream = new ObjectOutputStream(out);
            stream.writeObject(message);
        }
        catch (Exception e) {
            throw new ParsingException(e);
        }
    }
}
