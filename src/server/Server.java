package server;

import messages.*;
import messages.parsing.MessageReadWrite;
import messages.parsing.serialization.SerializationParser;
import messages.parsing.xml.XMLParser;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    public static final Logger LOGGER = Logger.getGlobal();
    public final MessageReadWrite PARSER;
    private static final int BACKLOG_COUNT = 10;
    private final int PORT;
    public static final long TIMEOUT = 100;
    private static final String ADDRESS = "0.0.0.0";
    public Server(int port) {
        final boolean LOGGING = Boolean.parseBoolean(ServerConfigurations.getFieldValue(ServerConfigurations.Field.LOGGING));
        if (LOGGING) {
            LOGGER.setLevel(Level.ALL);
        }
        else {
            LOGGER.setLevel(Level.OFF);
        }
        PORT = port;
        final boolean XML = Boolean.parseBoolean(ServerConfigurations.getFieldValue(ServerConfigurations.Field.XML));
        if (XML) {
            PARSER = new XMLParser();
        }
        else {
            PARSER = new SerializationParser();
        }
    }

    public Server() {
        this(
                Integer.parseInt(ServerConfigurations.getFieldValue(ServerConfigurations.Field.PORT))
        );
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }

    public void start() {
        LOGGER.info("Starting server");
        IsRunning.set(true);
        try (
                Selector selector = Selector.open();
                ServerSocketChannel serverChannel = ServerSocketChannel.open()
        ) {
            serverChannel.bind(new InetSocketAddress(ADDRESS, PORT));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            LOGGER.info("listening to connections...");
            while (!Thread.interrupted()) {
                selector.select();
                var iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isAcceptable()) {
                        SocketChannel channel = serverChannel.accept();
                        UUID usid = UUID.randomUUID();
                        ClientHandler handler = new ClientHandler(channel, this, usid);
                        Session session = new Session(false, null, handler);
                        SessionMap.put(usid, session);
                        Thread thread = new Thread(handler);
                        thread.start();
                        LOGGER.info("incoming socket connection USID: " + usid);
                    }
                }
            }
        }
        catch (IOException e) {
            LOGGER.log(Level.ALL, "server socket IO exception " + e.getMessage());
        }
        catch (RuntimeException e) {
            LOGGER.log(Level.ALL, "main thread runtime exception " + e.getMessage());
        }
        finally {
            broadcast(new Message(
                    MessageType.SERVER_ERROR,
                    new ServerError("Server closed")
            ));
            IsRunning.set(false);
        }
    }

    public boolean isRunning() {
        return IsRunning.get();
    }

    public void handleMessage(Message msg, UUID usid) {
        Session session = SessionMap.get(usid);
        if (session == null) {
            return;
        }
        switch (msg.getType()) {
            case CLIENT_LOGIN -> {
                ClientLogin login = (ClientLogin) msg.getMessage();
                boolean alreadyExist = false;
                synchronized (SessionMap) {
                    for (Session s : SessionMap.values()) {
                        if (s.isAuthorised())
                            if (login.name().equals(s.login().name())) {
                                alreadyExist = true;
                                break;
                            }
                    }
                }
                LOGGER.info("USID: " + usid + "; Login attempt with name " + login.name());
                if (alreadyExist) {
                    LOGGER.info("USID: " + usid + "; Existing username " + login.name());
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_ERROR, new ServerError("this username already exists")
                    ));
                }
                else {
                    LOGGER.info("USID: " + usid + "; New user authorized " + login.name() + "; Client: " + login.client());
                    SessionMap.replace(
                            usid,
                            new Session(true, login, session.handler())
                    );
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_LOGIN_SUCCESS,
                            new ServerClientSessionID(usid)
                    ));
                    LOGGER.info("USID: " + usid + ": Sending backlogs to new user");
                    synchronized (Backlog) {
                        for (ServerMessage m : Backlog) {
                            session.handler().addOutputMessage(new Message(
                                    MessageType.SERVER_MESSAGE,
                                    m
                            ));
                        }
                    }
                    LOGGER.info("Broadcasting about new user to everyone");
                    broadcast(new Message(
                            MessageType.SERVER_USER_LOGIN,
                            new ServerUserName(login.name())
                    ));
                }
            }
            case CLIENT_LIST_REQUEST -> {
                LOGGER.info("USID " + usid + ": requested list of users");
                if (!session.isAuthorised()) {
                    LOGGER.info("USID " + usid + ": list access denied");
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_ERROR,
                            new ServerError("You are not authorized")
                    ));
                }
                else {
                    LOGGER.info("USID " + usid + ": sending list");
                    ServerList serverList;
                    synchronized (SessionMap) {
                        serverList = new ServerList(new ArrayList<>(SessionMap
                                .values().stream()
                                .filter(Session::isAuthorised)
                                .map((Session::login))
                                .toList()
                        ));
                    }
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_LIST_RESPONSE,
                            serverList
                    ));
                }
            }
            case CLIENT_MESSAGE -> {
                if (session.isAuthorised()) {
                    ClientMessage message = (ClientMessage) msg.getMessage();
                    ServerMessage serverMessage = new ServerMessage(message.message(), session.login().name());
                    backlog(serverMessage);
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_EMPTY_SUCCESS,
                            null
                    ));
                    broadcast(new Message(
                            MessageType.SERVER_MESSAGE,
                            serverMessage
                    ));
                    LOGGER.info("USID " + usid + ": message received");
                }
                else {
                    LOGGER.info("USID " + usid + ": sending message denied");
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_ERROR,
                            new ServerError("You are not authorized")
                    ));
                }
            }
            case CLIENT_LOGOUT -> {
                if (session.isAuthorised()) {
                    LOGGER.info("USID " + usid + ": user logout");
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_EMPTY_SUCCESS,
                            null
                    ));
                    removeSession(usid);
                }
                else {
                    LOGGER.info("USID " + usid + ": not authorized message attempting to logout");
                    session.handler().addOutputMessage(new Message(
                            MessageType.SERVER_ERROR,
                            new ServerError("You are not authorized")
                    ));
                }
            }

            default -> {
                LOGGER.info("USID " + usid + ": unrecognized message");
                SessionMap.get(usid).handler().addOutputMessage(new Message(
                        MessageType.SERVER_ERROR,
                        new ServerError("not implemented message type")
                ));
            }
        }
    }

    public void removeSession(UUID usid) {
        LOGGER.info("removing session: " + usid);
        if (SessionMap.containsKey(usid) && SessionMap.get(usid).isAuthorised()) {
            ServerUserName userName = new ServerUserName(SessionMap.get(usid).login().name());
            SessionMap.remove(usid);
            broadcast(new Message(
                    MessageType.SERVER_USER_LOGOUT,
                    userName
            ));
        }
        else {
            SessionMap.remove(usid);
        }
    }

    private void broadcast(Message message) {
        synchronized (SessionMap) {
            for (Session session : SessionMap.values()) {
                session.handler().addOutputMessage(message);
            }
        }
    }

    private void backlog(ServerMessage message) {
        synchronized (Backlog) {
            while (Backlog.size() >= BACKLOG_COUNT) {
                Backlog.removeFirst();
            }
            Backlog.addLast(message);
        }
    }

    private final Deque<ServerMessage> Backlog = new LinkedList<>();
    private final Map<UUID, Session> SessionMap = Collections.synchronizedMap(new HashMap<>());
    private final AtomicBoolean IsRunning = new AtomicBoolean(false);
}

record Session(boolean isAuthorised, ClientLogin login, ClientHandler handler) {  }
