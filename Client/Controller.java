
import static java.nio.channels.SelectionKey.OP_READ;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;

/**
 * Provides the methods that perform the required actions(say, GET, POST) as per the user request.
 * 
 * @author karan
 *
 */
public class Controller {

	// Stores the request
	private String request = null;
	private boolean fileOpen = false;
	private BufferedWriter write;
	
	private static String routerHost = "localhost";
	private static int routerPort = 3000;
	private static String serverHost = "localhost";
	private static int serverPort = 8007;
	
	private static void runClient(SocketAddress routerAddr, InetSocketAddress serverAddr, String msg) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
       
            Packet p = new Packet.Builder()
                    .setType(0)
                    .setSequenceNumber(1L)
                    .setPortNumber(serverAddr.getPort())
                    .setPeerAddress(serverAddr.getAddress())
                    .setPayload(msg.getBytes())
                    .create();
            channel.send(p.toBuffer(), routerAddr);

            
            System.out.println("Sending "+msg+" to router at "+routerAddr);

            // Try to receive a packet within timeout.
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            System.out.println("Waiting for the response");
            selector.select(5000);

            Set<SelectionKey> keys = selector.selectedKeys();
            while(keys.isEmpty()){
                System.out.println("No response after timeout");
                System.out.println("Sending "+msg+" to router at "+routerAddr);
                //runClient(routerAddr, serverAddr, msg);
                channel.send(p.toBuffer(), routerAddr);
             
                channel.configureBlocking(false);
                channel.register(selector, OP_READ);
                System.out.println("Waiting for the response");
                selector.select(10000);
                keys = selector.selectedKeys();
            }

            // We just want a single response.
            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
            SocketAddress router = channel.receive(buf);
            buf.flip();
            Packet resp = Packet.fromBuffer(buf);
            System.out.println("Packet: "+resp);
            System.out.println("Router: "+router);
            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
            System.out.println("Payload: "+payload);

            keys.clear();
        }
    }
	
	/**
	 * Simple GET request to fetch data from host.
	 * 
	 * @param host Name of the host
	 * @param port Port Number(By default, HTTP has port number 80)
	 * @param path Directory within the host
	 * @param headers Collection of request headers with key-value pair
	 * @throws IOException 
	 */
	public void getRequest(Attributes attributes) throws IOException {
		
		SocketAddress routerAddr = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddr = new InetSocketAddress(serverHost, serverPort);
        
        request = "";
		
		// building a GET request
		request += "GET " +attributes.getPath()+" HTTP/1.0\r\n";
		request += "Host: "+attributes.getHost()+"\r\n";
		// adding headers
		if (attributes.getHeaders() != null) {
			addHeaders(attributes.getHeaders());
		}
		request += "\r\n";
		request += "\r\n";
		
		runClient(routerAddr, serverAddr, request);
		
    }
	
	/**
	 * GET request with verbose[-v] option. Prints the detail of the response such as 
	 * protocol, status, and headers. Verbosity could be useful for testing and 
	 * debugging stages where you need more information to do so.
	 * 
	 * @param host Name of the host
	 * @param port Port Number(By default, HTTP has port number 80)
	 * @param path Directory within the host
	 * @param headers Collection of request headers with key-value pair
	 * @throws IOException 
	 */
	public void getRequestWithVerbose(Attributes attributes) throws IOException {
		
		Socket socket = null;
		BufferedWriter bufferWriter = null;
		BufferedReader bufferReader = null;
		try {	
			socket = new Socket(attributes.getHost(), attributes.getPort());
			bufferWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
			request = "";
			
			// building a GET request
			request += "GET " +attributes.getPath()+" HTTP/1.0\r\n";
			request += "Host: "+attributes.getHost()+"\r\n";
			// adding headers
			if (attributes.getHeaders() != null) {
				addHeaders(attributes.getHeaders());
			}
			request += "\r\n";
			
			bufferWriter.write(request);
			bufferWriter.flush();
			
			bufferReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			String response = "";
			String line;
			
			// Getting response from host
			while ((line = bufferReader.readLine())!= null){

				response += line+"\n";
			}

			if(response.isEmpty() || (!response.substring(9, 12).equals("302") && !response.substring(9, 12).equals("301"))) {
			// print response in console 
			System.out.println(response);
			// save response in external file
			if (attributes.getFileForHttpResponse() != null) {
				saveResponse(attributes.getFileForHttpResponse(), response);
				write.close();
			}
			}
			else {
				//redirecting
				redirect(attributes, response);
				if (attributes.getFileForHttpResponse() != null) {
					saveResponse(attributes.getFileForHttpResponse(), response);
					write.write("\nRedirecting to... http://" + attributes.getHost() + attributes.getPath() + "\n\n");
				}
				getRequestWithVerbose(attributes);
				if(fileOpen)
					write.close();
			}
		} finally {
			request = null;
			bufferReader.close();
			if(fileOpen)
				bufferWriter.close();
			socket.close();
		}
	}
	
	private void redirect(Attributes attributes, String response) {
		System.out.println(response);
		System.out.println("\nRedirecting to... http://" + attributes.getHost() + attributes.getPath() + "\n\n");
		int index1 = response.indexOf("Location");
		int index2 = response.indexOf("\n", index1);
		String newURL = response.substring(index1+10, index2);
		
		//check if URL starts with http:// or http://
		if(newURL.startsWith("http://"))
			newURL = newURL.substring(7);
		else if(newURL.startsWith("https://"))
			newURL = newURL.substring(8);
		else if(newURL.startsWith("'https://"))
			newURL = newURL.substring(9, newURL.length()-1);
		else if(newURL.startsWith("'http://"))
			newURL = newURL.substring(8, newURL.length()-1);
		
		//checking first occurence of '/' in the string without http:// or https://
		int index3 = newURL.indexOf('/');
		if(index3==-1)
			index3 = newURL.indexOf(".com")+4;
		
		//splitting the string into host, path based on index of '/'
		if(index3 != -1) {
		attributes.setHost(newURL.substring(0, index3));
		attributes.setPath(newURL.substring(index3));
		}
		else {
			attributes.setHost(newURL);
			attributes.setPath("/");
		}

	}

	/**
	 * Simple POST request to fetch additional data from host.
	 * 
	 * @param host Name of the host
	 * @param port Port Number(By default, HTTP has port number 80)
	 * @param path Directory within the host
	 * @param headers Collection of request headers with key-value pair
	 * @throws IOException 
	 */
	public void postRequest(Attributes attributes) throws IOException {
		
		SocketAddress routerAddr = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddr = new InetSocketAddress(serverHost, serverPort);
        
        request = "";
		
			
			// building a POST request
			request += "POST " +attributes.getPath()+" HTTP/1.0\r\n";
			request += "Host: "+attributes.getHost()+"\r\n";
			// adding headers
			if (attributes.getHeaders() != null) {
				addHeaders(attributes.getHeaders());
			}
			// setting up the length of inline data
			if (attributes.getInlineData() != null) {
				request += "Content-Length:" + attributes.getInlineData().length() + "\r\n";
			}			
			request += "Connection: close\r\n";
			request += "\r\n";
			// adding inline data
			if (attributes.getInlineData() != null) {
				request += attributes.getInlineData();
			}
			request += "\r\n";
			
			runClient(routerAddr, serverAddr, request);

			

	}
	
	/**
	 * POST request with verbose[-v] option. Prints the detail of the response such as 
	 * protocol, status, and headers. Verbosity could be useful for testing and 
	 * debugging stages where you need more information to do so.
	 * 
	 * @param host Name of the host
	 * @param port Port Number(By default, HTTP has port number 80)
	 * @param path Directory within the host
	 * @param headers Collection of request headers with key-value pair
	 * @throws IOException 
	 */
	public void postRequestWithVerbose(Attributes attributes) throws IOException {
		
		Socket socket = null;
		BufferedWriter bufferWriter = null;
		BufferedReader bufferReader = null;
		try {	
			socket = new Socket(attributes.getHost(), attributes.getPort());
			bufferWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
			request = "";
			
			// building a POST request
			request += "POST " +attributes.getPath()+" HTTP/1.0\r\n";
			request += "Host: "+attributes.getHost()+"\r\n";
			// adding headers
			if (attributes.getHeaders() != null) {
				addHeaders(attributes.getHeaders());
			}
			// setting up the length of inline data
			if (attributes.getInlineData() != null) {
				request += "Content-Length:" + attributes.getInlineData().length() + "\r\n";
			}			
			request += "Connection: close\r\n";
			request += "\r\n";
			// adding inline data
			if (attributes.getInlineData() != null) {
				request += attributes.getInlineData();
			}
			
			bufferWriter.write(request);
			bufferWriter.flush();
			
			bufferReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			String response = "";
			String line;
			
			// Getting response from host
			while ((line = bufferReader.readLine())!= null){

				response += line+"\n";
			}
			
			// print response in console 
			System.out.println(response);
			// save response in external file
			if (attributes.getFileForHttpResponse() != null) {
				saveResponse(attributes.getFileForHttpResponse(), response);
				write.close();
			}
		} finally {
			request = null;
			bufferReader.close();
			bufferWriter.close();
			socket.close();
		}
	}
	
	/**
	 * Adds headers to the request message.
	 * 
	 * @param headers Collection of request headers with key-value pair
	 */
	public void addHeaders(HashMap<String, String> headers) {
		try {
			headers.forEach((key,value) -> {
				request += key+": "+value+"\r\n";
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Save response to an external file
	 * 
	 * @param fileForHttpResponse User efined file name for storing response
	 * @param response HTTP Response
	 * @throws IOException
	 */
	public void saveResponse(String fileForHttpResponse, String response) throws IOException {
		if(!fileOpen) {
			write = new BufferedWriter(new FileWriter(new File(fileForHttpResponse)));
			fileOpen = true;
		}
		try {
			write.write(response);
		} catch (Exception e) {	
			e.printStackTrace();
		} 
	}
}
