package utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ExecuteShellCommand {
	public static String executeCommand(String command, boolean wait) {
	    Process p;
	    StringBuffer output = new StringBuffer();
	    try {
	        p = Runtime.getRuntime().exec(command);
	        if(wait) {
		        p.waitFor();
		        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
	
			    String line = "";           
			    while ((line = reader.readLine())!= null) {
			        output.append(line + "\n");
			    }
	        }
	    }
	    catch (InterruptedException | IOException e) {
	        e.printStackTrace();
	    }
	    return output.toString();
	}
}
