import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public class httpfs {

	private static int port = 8007;
	private static boolean isVerbose = false;
	private static String directory = "C:\\Users\\mehak\\Documents\\New folder";
	private static String response = "";

	private static int windowSize = 4;
	private static long lastPacketReceivedInSequence;
	private static boolean isFirstPacket = true;
	private static ArrayList<Packet> receivedPackets = new ArrayList<>(windowSize);

	static public class StartServer implements Runnable {

		@Override
		public void run() {

		}
	}

	public static void main(String[] args) throws IOException {

		Thread startListening = new Thread(new StartServer());
		startListening.start();

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-v")) {
				isVerbose = true;
			}
			if (args[i].equals("-p")) {
				port = Integer.parseInt(args[i + 1]);
			}
			if (args[i].equals("-d")) {
				directory = args[i + 1];
			}
		}

		try (DatagramChannel channel = DatagramChannel.open()) {
			channel.bind(new InetSocketAddress(port));
			System.out.println("EchoServer is listening at " + channel.getLocalAddress());
			ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);

			for (;;) {
				buf.clear();
				SocketAddress router = channel.receive(buf);

				// Parse a packet from the received raw data.
				buf.flip();
				Packet packet = Packet.fromBuffer(buf);
				buf.flip();

				String payload = new String(packet.getPayload(), UTF_8);
				System.out.println("Packet: " + packet);
				System.out.println("Router: " + router);
				System.out.println("Payload: " + payload);

				parseRequest(payload);

				payload = "Request handled by server!";

				// Send the response to the router not the client.
				// The peer address of the packet is the address of the client already.
				// We can use toBuilder to copy properties of the current packet.
				// This demonstrate how to create a new packet from an existing packet.
				Packet resp = packet.toBuilder().setPayload(response.getBytes()).create();
				channel.send(resp.toBuffer(), router);
				buf.clear();
				response = "";
			}
		}
	}

	private static void parseRequest(String request) {

		String[] requestLines = request.trim().split("\n");
		String typeOfRequest = requestLines[0].split(" ")[0];
		String fileName = requestLines[0].split(" ")[1];
		String fileType = "";
		boolean isFile = true;

		if (fileName.trim().equals("/")
				|| fileName.trim().equals("../") /* second condition implementation pending, maybe we'll need */) {
			isFile = false;
		}

		if (typeOfRequest.equalsIgnoreCase("get") && requestLines.length == 3) {
			fileType = requestLines[2].split(":")[1].trim();
		} else if (typeOfRequest.equalsIgnoreCase("get") && requestLines.length == 2) {
			fileType = "text";
		}

		String body = "";
		boolean bodyStarted = false;
		for (String line : requestLines) {
			if (bodyStarted) {
				body += line;
			}
			if (line.length() == 1) {
				bodyStarted = true;
			}
		}
		handleRequest(typeOfRequest, isFile, fileName, body, fileType);
	}

	private static void handleRequest(String typeOfRequest, boolean isFile, String fileName, String body,
			String fileType) {

		File file;
		File[] files;

		try {
			switch (typeOfRequest.toLowerCase()) {

			case "get":
				if (isFile) {
					file = new File(directory + fileName);
					if (file.exists()) {
						openFile(fileName, fileType, file);
					} else {
						response += "HTTP ERROR 404\nFILE NOT FOUND";
					}
				} else {
					files = new File(directory).listFiles();
					for (int i = 0; i < files.length; i++) {
						response += files[i].getName() + "\n";
					}
				}
				break;
			case "post":
				RandomAccessFile fileToWrite = new RandomAccessFile(directory + fileName, "rw");
				FileChannel channel = fileToWrite.getChannel();
				FileLock lock = null;
				try {
					lock = channel.tryLock();
				} catch (final OverlappingFileLockException e) {
					System.out.println("Cannot access the file while the other client is writing to the file");
					fileToWrite.close();
					channel.close();
				}
				fileToWrite.writeChars(body);
				TimeUnit.SECONDS.sleep(7);
				lock.release();
				fileToWrite.close();
				channel.close();
				break;

			default:
				response += "INVALID REQUEST";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void openFile(String fileName, String fileType, File file) {

		switch (fileType) {
		case "text":
			BufferedReader fileReader;
			try {
				response += "reading " + fileName + "...\nContent:";
				fileReader = new BufferedReader(new FileReader(file));
				String allLines = "", line;
				while ((line = fileReader.readLine()) != null) {
					allLines += line + "\n";
				}
				response += allLines;
			} catch (Exception e) {
				e.printStackTrace();
			}
			break;

		case "image":
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					JFrame editorFrame = new JFrame("Image Demo");
					editorFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

					BufferedImage image = null;
					try {
						image = ImageIO.read(new File(directory + fileName));
					} catch (Exception e) {
						e.printStackTrace();
						System.exit(1);
					}

					ImageIcon imageIcon = new ImageIcon(image);
					JLabel jLabel = new JLabel();
					jLabel.setIcon(imageIcon);
					editorFrame.getContentPane().add(jLabel, BorderLayout.CENTER);

					editorFrame.pack();
					editorFrame.setLocationRelativeTo(null);
					editorFrame.setVisible(true);
				}
			});
			break;

		case "pdf":
			if (Desktop.isDesktopSupported()) {
				try {
					File myFile = new File(directory + fileName);
					Desktop.getDesktop().open(myFile);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
			break;

		default:
			response += "HTTP ERROR 404\nFILE NOT FOUND";
			break;
		}
	}

	private static void runServer() throws IOException {
		try (DatagramChannel channel = DatagramChannel.open()) {
			channel.bind(new InetSocketAddress(port));
			System.out.println("EchoServer is listening at " + channel.getLocalAddress());
			ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN).order(ByteOrder.BIG_ENDIAN);

			for (;;) {
				buf.clear();
				SocketAddress router = channel.receive(buf);

				// Parse a packet from the received raw data.
				buf.flip();
				Packet packet = Packet.fromBuffer(buf);
				buf.flip();
				
				if(isFirstPacket) {
					lastPacketReceivedInSequence = packet.getSequenceNumber();
					isFirstPacket = false;
				}

				if (receivedPackets.size() < 4) {
					// add packet to window
					receivedPackets.add(packet);

					String payload = new String(packet.getPayload(), UTF_8);
					System.out.println("Packet: " + packet);
					System.out.println("Router: " + router);
					System.out.println("Payload: " + payload);

					parseRequest(payload);

					payload = "Request handled by server!";

					// Send the response to the router not the client.
					// The peer address of the packet is the address of the client already.
					// We can use toBuilder to copy properties of the current packet.
					// This demonstrate how to create a new packet from an existing packet.
					Packet resp = packet.toBuilder().setPayload(response.getBytes()).create();
					channel.send(resp.toBuffer(), router);
				}
				buf.clear();
				response = "";
				
				//check if window can be slided down
				for(int i = 0; i<receivedPackets.size(); i++) {
					for(Packet test : receivedPackets) {
						if(test.getSequenceNumber() == lastPacketReceivedInSequence+1) {
							lastPacketReceivedInSequence = test.getSequenceNumber();
							receivedPackets.remove(test);
						}
					}
				}
			}
		}
	}
}