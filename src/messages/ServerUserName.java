package messages;

import messages.parsing.xml.XMLParsable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.Serializable;

public record ServerUserName(String name) implements Serializable, XMLParsable {
    @Override
    public void parse(Document doc, Node main) {
        XMLParsable.addTextNode("name", name(), main, doc);
    }
}
