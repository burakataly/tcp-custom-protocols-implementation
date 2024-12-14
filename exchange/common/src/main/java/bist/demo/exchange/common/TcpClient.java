package bist.demo.exchange.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/*
* Socket builtin classı bizi bind, send, receive gibi sistem çağrılarıyla uğraşmaktan
* kurtarıyor. kernel socketiyle ve bu socket için bulunan bufferlarla bu class iletişime
* geçiyor.
* Network uygulamalarında client'ın tcp tarafı gatewayden gelen ByteBuffer'a yazılmış protokolü
* byte dizisine dönüştürüp bunu Socket nesnesinin metodu ile gerçek socket için bulunan kernel
* bufferına yazıyor. ByteBuffer tcp katmanının raw bytelar ile iş yapmasından dolayı
* kullanılıyor. app layer'ın protokol tipi ne olursa olsun mesajını byteBuffer nesnesine
* yazıyor ve tcp katmanına gönderiyor. Yani tcp layer ve app layer tarafı arasında bir
* abstraction sağlıyor
*
*/

public class TcpClient {
    private final String host;

    private final int port; //bağlanılmak istenen server'ın çalıştığı port

    private Socket socket = null;

    private ByteBuffer buffer = ByteBuffer.allocate(Constants.BUFFER_CAPACITY);;


    public TcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean connect() throws IOException {

        try {
            socket = new Socket(InetAddress.getByName(host), port); //serverın ip ve portu ile bağlantı
            //isteği gönderir ve ayrıca OS client'a bu porttan farklı bir port atar
            return true;
        } catch (ConnectException exception) {
            System.err.println("Connection failed: No server listening on the specified port." + exception.getMessage());

            socket = null;
            return false;
        }
    }

    public boolean send(ByteBuffer buffer) throws IOException {
        if (socket == null) {

            System.err.println("Connection is not available.");
            return false;
        }

        OutputStream outputStream = socket.getOutputStream(); //socketin bufferına yazmak için

        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes); //bu iki satır bytebuffer nesnesinden byte dizisi elde ediyor

        outputStream.write(bytes); //byte dizisi socketin kerneldeki bufferına yazılıyo

        return true;
    }

    public ByteBuffer handleResponses() {
        if (socket == null || socket.isClosed()) {
            System.err.println("Socket is disconnected. " + socket);
            return null;
        }

        InputStream inputStream;
        try {
            inputStream = socket.getInputStream(); //kernelde bulunan bufferdaki byte şeklindeki verileri alır
        } catch (IOException e) {
            System.err.println("Input stream read error. " + e.getMessage());
            return null;
        }

        boolean readStatus = readIncomingMessage(inputStream); //kernelden alınan byteları tek tek okuyup bytebuffer nesnesine yazacak

        if (!readStatus) {
            System.err.println("Read incoming message error.");
            return null;
        }

        int size = buffer.remaining();

        if (size <= 0) {
            return null;
        }

        return buffer;
    }

    private boolean readIncomingMessage(InputStream inputStream) {
        int readByte;

        buffer.clear(); //bytebuffera yeni okunacak mesajı yazmadan önce temizliyo ve position 0'a set ediliyo

        while (true) {

            try {
                if (inputStream.available() <= 0) {
                    break;
                }
            } catch (IOException e) {
                return false;
            }

            try {
                readByte = inputStream.read(); //byte byte okuyo
            } catch (IOException e) {
                return false;
            }

            if (readByte == -1) {
                break;
            }

            buffer.put((byte) readByte); //eğer okunan byteda sorun yoksa buffera yazıyo
            //bu yazmadan sonra position 1 artar
        }
        buffer.flip(); //buffera yazma işi bittikten sonra okunabilmesi için buffer kapasitesini
        //gösteren limit o anki positiona position ise 0'a set ediliyo
        return true;
    }

    public void close() throws IOException {
        if (socket == null) {
            return;
        }
        socket.close();
    }
}
