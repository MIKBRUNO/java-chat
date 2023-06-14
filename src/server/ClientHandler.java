package server;

import messages.Message;
import messages.parsing.ParsingException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class ClientHandler implements Runnable {
    public ClientHandler(SocketChannel channel, Server server, UUID usid) {
        SockChannel = channel;
        ControlServer = server;
        USID = usid;
    }

    @Override
    public void run() {
        try (Selector selector = Selector.open()) {
            SockChannel.configureBlocking(false);
            SelectionKey socketKey = SockChannel.register(selector, SelectionKey.OP_READ);
            ByteBuffer sizeBuffer = ByteBuffer.allocate(4);
            ByteBuffer messageBuffer = null;
            boolean isReadingObject = false;

            while (!Thread.interrupted() && ControlServer.isRunning() && isAlive()) {
                selector.select(Server.TIMEOUT);
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
                    synchronized (this) {
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
                        message = inputQueue.poll();
                    }
                    if (message != null) {
                        ControlServer.handleMessage(message, USID);
                    }
                }
                if (!outputQueue.isEmpty()) {
                    socketKey.interestOpsOr(SelectionKey.OP_WRITE);
                }
            }
        }
        catch (IOException e) {
            Server.LOGGER.info("USID: " + USID + "; connection has been corrupted, error: " + e.getMessage());
            Server.LOGGER.info("USID: " + USID + "; cancelling corrupted connection");
        }
        finally {
            ControlServer.removeSession(USID);
            try {
                SockChannel.close();
            } catch (IOException e) {
                Server.LOGGER.info("USID: " + USID + "; ignored exception on closing SocketChannel");
            }
        }
    }

    public synchronized void addOutputMessage(Message message) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            ControlServer.PARSER.encode(stream, message);
        } catch (ParsingException e) {
            Server.LOGGER.info("Parser exception: " + e.getMessage());
            IsAlive.set(false);
            return;
        }
        byte[] bytes = stream.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length + Integer.BYTES);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        buffer.position(0);
        outputQueue.offer(buffer);
    }

    private synchronized void addInputMessage(ByteBuffer message) {
        try {
            inputQueue.offer(ControlServer.PARSER.parse(new ByteArrayInputStream(message.array())));
        } catch (ParsingException e) {
            Server.LOGGER.info("Parser exception: " + e.getMessage());
            IsAlive.set(false);
        }
    }

    private boolean isAlive() {
        return IsAlive.get();
    }

    private AtomicBoolean IsAlive = new AtomicBoolean(true);
    private final SocketChannel SockChannel;
    private final Server ControlServer;
    private final UUID USID;
    private final Queue<ByteBuffer> outputQueue = new LinkedList<>();
    private final Queue<Message> inputQueue = new LinkedList<>();
}