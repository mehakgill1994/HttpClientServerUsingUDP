
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;


/**
 * This main class performs the following tasks
 * -	accept commands line arguments from users
 * -	parse command line arguments to isolate
 * 			- the type of html request
 * 			- host
 * 			- path
 * 			- additional headers
 * 			- input and output file names
 * -	Then, it calls the respective html methods implemented in Controller class
 * 
 * @author mehak
 *
 */
public class Httpc {
	
	private static String URL;
	private static String outputFileName;
	private static String inputFileName;
	private static HashMap<String, String> headers = null;
	public static Attributes atrObj;
		
	private static void parseURL(String URL) {
		String str = URL;
		
		//check if URL starts with http:// or http://
		if(str.startsWith("http://"))
			str = str.substring(7);
		else if(str.startsWith("https://"))
			str = str.substring(8);
		else if(str.startsWith("'https://"))
			str = str.substring(9, str.length()-1);
		else if(str.startsWith("'http://"))
			str = str.substring(8, str.length()-1);
		else if(str.startsWith("\'"))
			str = str.substring(1, str.length()-1);
		
		//checking first occurence of '/' in the string without http:// or https://
		int index1 = str.indexOf('/');
		
		//splitting the string into host, path based on index of '/'
		if(index1 != -1) {
			atrObj.setHost(str.substring(0, index1));
			atrObj.setPath(str.substring(index1));
		}
		else {
			atrObj.setHost(URL);
		}
	}
	
	private static void parseCommand(String[] cmdArgs) throws IOException {
		String[] args;
		if(Constants.storeOutputToFile) {
			outputFileName = cmdArgs[cmdArgs.length-1];
			atrObj.setFileForHttpResponse(outputFileName);
			args = Arrays.copyOf(cmdArgs, cmdArgs.length-2);
		}
		else {
			args = cmdArgs.clone();
		}
		Controller obj = new Controller();
		
		String command = args[0];
		switch (command) {
		case "help":
			if (args.length == 1)
				System.out.println(Constants.HELP);					//command -> httpc help
			else if (args.length == 2 && args[1].equals("get"))
				System.out.println(Constants.GETHELP);				//command -> httpc help get
			else if (args.length == 2 && args[1].equals("post"))
				System.out.println(Constants.POSTHELP);				//command -> httpc help post
			else
				System.out.println(Constants.INVALID);				//Invalid Command
				break;
		case "get":
			if(args.length == 2)
				obj.getRequest(atrObj);				//command -> httpc get URL
			else if (args.length == 3)
				obj.getRequestWithVerbose(atrObj);	//command -> httpc get -v URL
			else {
				if(args[1].equals("-v")) {	//with verbose
					int numHeaders = (args.length-3)/2;
					int startIndex = 3;
					processHeaders(args, numHeaders, startIndex);
					obj.getRequestWithVerbose(atrObj); //command -> httpc get -v (-h key:value)* URL
				}
				else { 	//without verbose
					int numHeaders = (args.length-2)/2;
					int startIndex = 2;
					processHeaders(args, numHeaders, startIndex);
					obj.getRequest(atrObj); //command -> httpc get (-h key:value)* URL
				}
			}
			break;
		case "post":
			if(args.length == 2)
				obj.postRequest(atrObj);				//command -> httpc post URL
			else if (args.length == 3)
				obj.postRequestWithVerbose(atrObj);	//command -> httpc post -v URL
			else {
				if(args[1].equals("-v")) { 	//with verbose
					int numHeaders = 0;
					int startIndex = 3;
					if(args[args.length-3].equals("-d")) {
						atrObj.setInlineData(args[args.length-2]);
						numHeaders = (args.length-5)/2;
					}
					else if(args[args.length-3].equals("-f")) {
						inputFileName = args[args.length-2];
						File file = new File(inputFileName);
						BufferedReader br = new BufferedReader(new FileReader(file)); 
						String input = ""; 
						String nextLine;
						while ((nextLine = br.readLine()) != null) {
						    input+=nextLine; 
						 } 
						atrObj.setInlineData(input);
						br.close();
						numHeaders = (args.length-5)/2;
					}
					else if (args[args.length-3].equals("-h")) {
						numHeaders = (args.length-3)/2;
					}
					if(numHeaders > 0) {
						processHeaders(args, numHeaders, startIndex);
					}
					obj.postRequestWithVerbose(atrObj); //command -> httpc post -v (-h key:value)* [-d] [-f] URL
				}
				else { 	//without verbose
					int numHeaders = 0;
					int startIndex = 2;
					if(args[args.length-3].equals("-d")) {
						atrObj.setInlineData(args[args.length-2]);
						numHeaders = (args.length-4)/2;
					}
					else if(args[args.length-3].equals("-f")) {
						inputFileName = args[args.length-2];
						File file = new File(inputFileName); 
					    BufferedReader br = new BufferedReader(new FileReader(file));   
						String input = ""; 
						String nextLine;
						while ((nextLine = br.readLine()) != null) {
						    input+=nextLine; 
						} 
						atrObj.setInlineData(input);
						br.close();
						numHeaders = (args.length-4)/2;
					}
					else if (args[args.length-3].equals("-h")) {
						numHeaders = (args.length-2)/2;
					}
					if(numHeaders > 0) {
						processHeaders(args, numHeaders, startIndex);
					}
					obj.postRequest(atrObj); //command -> httpc post (-h key:value)* [-d] [-f] URL
				}
			}
			break;
		default:
			System.out.println(Constants.INVALID);
			break;
		}		
	}
	
	public static void processHeaders(String args[], int numHeaders, int startIndex) {
		headers = new HashMap<>();
		for(int i=0; i<numHeaders; i++) {
			int argNumber =  startIndex+i*2;
			String keyValue[] = args[argNumber].split(":");
			headers.put(keyValue[0], keyValue[1]);
		}
		atrObj.setHeaders(headers);
	}

	public static void main(String[] args) throws IOException {
		
		atrObj = new Attributes();
		atrObj.setPort(Constants.DEFAULT_PORT);
		
		if(!args[0].equals("help")) {
			
			//check if output is to be stored into file
			if(args[args.length - 2].equals("-o")) {
				//getting URL i.e. the third last element of the command line arguments
				URL = args[args.length - 3];
				Constants.storeOutputToFile = true;
			}
			else {
				//getting URL i.e. the last element of the command line arguments
				URL = args[args.length - 1];
			}
			//split URL into host, path & query
			parseURL(URL);
		}
		
		//parse arguments
		parseCommand(args);
		
	}

}
