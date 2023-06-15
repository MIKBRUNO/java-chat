package client;

import messages.*;
import messages.parsing.MessageReadWrite;
import messages.parsing.ParsingException;
import messages.parsing.serialization.SerializationParser;
import messages.parsing.xml.XMLParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Client implements AutoCloseable {
    private static final String CLIENT = "Client@21208_03";
    private static final int TIMEOUT = 100;
    private final MessageReadWrite PARSER;
    public Client() throws IOException {
        IsAlive.set(false);
        boolean XML = Boolean.parseBoolean(ClientConfig.getFieldValue(ClientConfig.Field.XML));
        if (XML) {
            PARSER = new XMLParser();
        }
        else {
            PARSER = new SerializationParser();
        }
    }

    public void start(String address, int port) throws Exception {
        if (!IsAlive.get()) {
            SockChannel = SocketChannel.open();
            SockChannel.connect(new InetSocketAddress(address, port));
            Worker = new Thread(this::work);
            Worker.start();
            IsAlive.set(true);
        }
    }

    @Override
    public void close() throws Exception {
        logout();
        if (CancelListener != null) {
            CancelListener.run();
        }
        IsAlive.set(false);
        SockChannel.close();
    }

    private void work() {
        try (Selector selector = Selector.open()) {
            synchronized (this) {
                ListeningSelector = selector;
                this.notify();
            }
            SockChannel.configureBlocking(false);
            SelectionKey socketKey = SockChannel.register(selector, SelectionKey.OP_READ);
            ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
            ByteBuffer messageBuffer = null;
            boolean isReadingObject = false;

            while (IsAlive.get()) {
                selector.select();
                var iter = selector.selectedKeys().iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isReadable()) {
                        int readCode = 0;
                        if (isReadingObject) {
                            readCode = SockChannel.read(messageBuffer);
                            if (!messageBuffer.hasRemaining()) {
                                messageBuffer.position(0);
                                isReadingObject = false;
                                addInputMessage(messageBuffer);
                                messageBuffer.clear();
                                messageBuffer = null;
                            }
                        } else {
                            readCode = SockChannel.read(sizeBuffer);
                            if (!sizeBuffer.hasRemaining()) {
                                sizeBuffer.position(0);
                                isReadingObject = true;
                                messageBuffer = ByteBuffer.allocate(sizeBuffer.getInt());
                                sizeBuffer.clear();
                            }
                        }
                        if (readCode == -1) {
                            IsAlive.set(false);
                            break;
                        }
                    }
                    synchronized (outputQueue) {
                        if (!outputQueue.isEmpty() && key.isWritable()) {
                            ByteBuffer buffer = outputQueue.element();
                            SockChannel.write(buffer);
                            if (!buffer.hasRemaining()) {
                                outputQueue.poll();
                                if (outputQueue.isEmpty())
                                    socketKey.interestOpsAnd(~SelectionKey.OP_WRITE);
                            }
                        }
                    }
                    Message message;
                    synchronized (this) {
                        synchronized (inputQueue) {
                            message = inputQueue.poll();
                        }
                    }
                    if (message != null) {
                        handleMessage(message);
                    }
                }
                synchronized (outputQueue) {
                    if (!outputQueue.isEmpty()) {
                        socketKey.interestOpsOr(SelectionKey.OP_WRITE);
                    }
                }
            }
        }
        catch (IOException e) {
            //...
        }
        finally {
            try {
                close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case SERVER_LOGIN_SUCCESS -> {
                ServerClientSessionID id = (ServerClientSessionID) message.getMessage();
                USID = id.usid();
            }
            case SERVER_EMPTY_SUCCESS -> {  }
        }
        if (MessageListener != null) {
            MessageListener.accept(message);
        }
    }

    public void setMessageListener(Consumer<Message> consumer) {
        MessageListener = consumer;
    }

    public void  setCloseListener(Runnable runnable) {
        CancelListener = runnable;
    }

    public void login(String name) {
        if (IsAlive.get()) {
            addOutputMessage(new Message(
                    MessageType.CLIENT_LOGIN,
                    new ClientLogin(name, CLIENT)
            ));
        }
    }

    public void requestUserList() {
        if (IsAlive.get()) {
            addOutputMessage(new Message(
                    MessageType.CLIENT_LIST_REQUEST,
                    new ServerClientSessionID(USID)
            ));
        }
    }

    public void sendMessage(String message) {
        if (IsAlive.get()) {
            addOutputMessage(new Message(
                    MessageType.CLIENT_MESSAGE,
                    new ClientMessage(message, USID)
            ));
        }
    }

    public void logout() {
        if (IsAlive.get()) {
            addOutputMessage(new Message(
                    MessageType.CLIENT_LOGOUT,
                    new ServerClientSessionID(USID)
            ));
        }
    }

    public void addOutputMessage(Message message) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            PARSER.encode(stream, message);
        } catch (ParsingException e) {
            IsAlive.set(false);
            return;
        }
        byte[] bytes = stream.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length + Integer.BYTES);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        buffer.position(0);
        synchronized (outputQueue) {
            outputQueue.offer(buffer);
        }
        synchronized (this) {
            if (ListeningSelector == null) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    IsAlive.set(false);
                }
            }
            ListeningSelector.wakeup();
        }
    }

    private void addInputMessage(ByteBuffer message) {
        try {
            synchronized (inputQueue) {
                inputQueue.offer(PARSER.parse(new ByteArrayInputStream(message.array())));
            }
        } catch (ParsingException e) {
            IsAlive.set(false);
        }
    }

    private Selector ListeningSelector;
    private Runnable CancelListener;
    private Consumer<Message> MessageListener;
    private UUID USID;
    private final AtomicBoolean IsAlive = new AtomicBoolean(false);
    private Thread Worker;
    private SocketChannel SockChannel;
    private final Queue<ByteBuffer> outputQueue = new LinkedList<>();
    private final Queue<Message> inputQueue = new LinkedList<>();
}
