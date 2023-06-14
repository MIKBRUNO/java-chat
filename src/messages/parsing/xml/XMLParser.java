package messages.parsing.xml;

import messages.*;
import messages.parsing.MessageReadWrite;
import messages.parsing.ParsingException;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class XMLParser implements MessageReadWrite {
    private final static DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private final static TransformerFactory transformerFactory = TransformerFactory.newInstance();

    @Override
    public Message parse(InputStream in) throws ParsingException {
        try {
            Document doc =  documentBuilderFactory.newDocumentBuilder().parse(in);
            Element main = doc.getDocumentElement();
            String tag = main.getTagName();
            String name = "";
            name = main.getAttribute("name");
            if (tag.equals("command")) {
                switch (name) {
                    case "login" -> {
                        String value = getNodeValue("name", main);
                        String type = getNodeValue("type", main);
                        return new Message(
                                MessageType.CLIENT_LOGIN,
                                new ClientLogin(value, type)
                        );
                    }
                    case "list" -> {
                        String USID_string = getNodeValue("session", main);
                        return new Message(
                                MessageType.CLIENT_LIST_REQUEST,
                                new ServerClientSessionID(UUID.fromString(USID_string))
                        );
                    }
                    case "message" -> {
                        String USID_string = getNodeValue("session", main);
                        String message = getNodeValue("message", main);
                        return new Message(
                                MessageType.CLIENT_MESSAGE,
                                new ClientMessage(message, UUID.fromString(USID_string))
                        );
                    }
                    case "logout" -> {
                        String USID_string = getNodeValue("session", main);
                        return new Message(
                                MessageType.CLIENT_LOGOUT,
                                new ServerClientSessionID(UUID.fromString(USID_string))
                        );
                    }

                    default -> {
                        throw new ParsingException("bad format");
                    }
                }
            } else if (tag.equals("event")) {
                switch (name) {
                    case "message" -> {
                        String message = getNodeValue("message", main);
                        String name_from = getNodeValue("name", main);
                        return new Message(
                                MessageType.SERVER_MESSAGE,
                                new ServerMessage(message, name_from)
                        );
                    }
                    case "userlogin" -> {
                        String name_from = getNodeValue("name", main);
                        return new Message(
                                MessageType.SERVER_USER_LOGIN,
                                new ServerUserName(name_from)
                        );
                    }
                    case "userlogout" -> {
                        String name_from = getNodeValue("name", main);
                        return new Message(
                                MessageType.SERVER_USER_LOGOUT,
                                new ServerUserName(name_from)
                        );
                    }

                    default -> {
                        throw new ParsingException("bad format");
                    }
                }
            } else if (tag.equals("error")) {
                String message = getNodeValue("message", main);
                return new Message(
                        MessageType.SERVER_ERROR,
                        new ServerError(message)
                );
            } else if (tag.equals("success")) {
                NodeList successNodes = main.getChildNodes();
                if (successNodes.getLength() == 0) {
                    return new Message(
                            MessageType.SERVER_EMPTY_SUCCESS,
                            null
                    );
                }
                else {
                    if (successNodes.getLength() != 1) {
                        throw new ParsingException("bad format");
                    }
                    Node node = successNodes.item(0);
                    if (node.getNodeName().equals("session")) {
                        return new Message(
                                MessageType.SERVER_LOGIN_SUCCESS,
                                new ServerClientSessionID(UUID.fromString(getNodeValue("session", main)))
                        );
                    } else if (node.getNodeName().equals("listusers")) {
                        ArrayList<ClientLogin> logins = new ArrayList<>();
                        NodeList users = node.getChildNodes();
                        for (int i = 0; i < users.getLength(); ++i) {
                            if (!users.item(i).getNodeName().equals("user")) {
                                throw new ParsingException("bad format");
                            }
                            String username = getNodeValue("name", users.item(i));
                            String type = getNodeValue("type", users.item(i));
                            logins.add(new ClientLogin(username, type));
                        }
                        return new Message(
                                MessageType.SERVER_LIST_RESPONSE,
                                new ServerList(logins)
                        );
                    }
                    else {
                        throw new ParsingException("bad format");
                    }
                }
            }

            return null;
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw new ParsingException(e);
        }

    }

    @Override
    public void encode(OutputStream out, Message message) throws ParsingException {
        try {
            Document doc = documentBuilderFactory.newDocumentBuilder().newDocument();
            Element main;
            switch (message.getType()) {
                case CLIENT_LIST_REQUEST, CLIENT_LOGIN, CLIENT_LOGOUT, CLIENT_MESSAGE -> {
                    main = doc.createElement("command");
                    XMLParsable xmlParsable = (XMLParsable) message.getMessage();
                    xmlParsable.parse(doc, main);
                }
                case SERVER_ERROR -> {
                    main = doc.createElement("error");
                    XMLParsable xmlParsable = (XMLParsable) message.getMessage();
                    xmlParsable.parse(doc, main);
                }
                case SERVER_EMPTY_SUCCESS -> {
                    main = doc.createElement("success");
                }
                case SERVER_LOGIN_SUCCESS, SERVER_LIST_RESPONSE -> {
                    main = doc.createElement("success");
                    XMLParsable xmlParsable = (XMLParsable) message.getMessage();
                    xmlParsable.parse(doc, main);
                }
                case SERVER_MESSAGE, SERVER_USER_LOGIN, SERVER_USER_LOGOUT -> {
                    main = doc.createElement("event");
                    XMLParsable xmlParsable = (XMLParsable) message.getMessage();
                    xmlParsable.parse(doc, main);
                }
                default -> {
                    throw new ParsingException("unrecognized message type");
                }
            }
            switch (message.getType()) {
                case CLIENT_LOGIN -> main.setAttribute("name", "login");
                case CLIENT_MESSAGE, SERVER_MESSAGE -> main.setAttribute("name", "message");
                case CLIENT_LOGOUT -> main.setAttribute("name", "logout");
                case CLIENT_LIST_REQUEST -> main.setAttribute("name", "list");
                case SERVER_USER_LOGIN -> main.setAttribute("name", "userlogin");
                case SERVER_USER_LOGOUT -> main.setAttribute("name", "userlogout");
            }
            doc.appendChild(main);

            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(out);

            transformer.transform(source, result);
        } catch (ClassCastException | TransformerException | ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private String getNodeValue(String nodeName, Node element) throws ParsingException {
        try {
            Element e = (Element) element;
            NodeList nodes = e.getElementsByTagName(nodeName);
            if (nodes.getLength() == 0) {
                throw new ParsingException("bad format");
            }
            NodeList childNodes = nodes.item(0).getChildNodes();
            if (childNodes.getLength() != 1) {
                throw new ParsingException("bad format");
            }
            return ((Text) childNodes.item(0)).getWholeText();
        }
        catch (ClassCastException e) {
            throw new ParsingException("bad format");
        }
    }
}
