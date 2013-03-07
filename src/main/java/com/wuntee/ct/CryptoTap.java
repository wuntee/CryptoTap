package com.wuntee.ct;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.tools.jdi.ProcessAttachingConnector;
import com.sun.tools.jdi.SocketAttachingConnector;
import com.wuntee.ct.jpda.JpdaWorkshop;

@SuppressWarnings("restriction")
public class CryptoTap {
	public static void main(String[] args) throws IOException, IllegalConnectorArgumentsException, VMStartException, InterruptedException, IncompatibleThreadStateException, AbsentInformationException, ClassNotLoadedException{
		if(args.length == 0){
			usage();
			return;
		}
		CryptoTap ct = new CryptoTap();
		for(int i=0; i<args.length; i++){
			if(args[i].equalsIgnoreCase("-l") || args[i].equalsIgnoreCase("--launch")){
				String command = args[++i];
				ct.setTapType(CryptoTap.TapType.LAUNCH);
				ct.setMainArgs(command);
			}else if(args[i].equalsIgnoreCase("-r") || args[i].equalsIgnoreCase("--remote")){
				String[] hostPort = args[++i].split(":");
				ct.setTapType(CryptoTap.TapType.REMOTE);
				ct.setHostname(hostPort[0]);
				ct.setPort(new Integer(hostPort[1]).intValue());
			}else if(args[i].equalsIgnoreCase("-p") || args[i].equalsIgnoreCase("--process")){
				ct.setTapType(CryptoTap.TapType.PROCESS);
				ct.setPid(new Integer(args[++i]).intValue());
			}else if(args[i].equalsIgnoreCase("-ls") || args[i].equalsIgnoreCase("--ls")){
				ct.list = true;
			}else{
				System.err.println("Invalid arugment: " + args[i]);
				usage();
				return;
			}
		}
		ct.run();
	}
	
	public static void usage(){
		System.out.println("CryptoTap (-l|--launch) javaArgs (-r|--remote) hostname:port (-p|--pid) pid (-ls|--ls)");
		System.out.println("\t-l|--launch javaArgs: The full java arugment string as if you were to run a " +
						   "\n\t\tcommand via 'java ...'");
		System.out.println("\t-r|--remote hostname:port: The hostname and port of the remote java process");
		System.out.println("\t-p|--pid pid: Attach to a java VM process. In order for this to work, the process." +
						   "\n\t\tmust be started with the '-agentlib:jdwp=transport=dt_socket,server=y' arguments. " +
						   "\n\t\tThe PID of the java process will then be what is passed as the argument to --pid.");
		System.out.println("\t-ls|--ls: Flag that will cause the applicaiton to list all available classes and exit.");
	}
		
	private String[] entryBreakpoints;
	private String[] exitBreakpoints;
	private CryptoTap.TapType type;
	private String mainArgs;
	private int port;
	private int pid;
	private String hostname;
	private boolean list = false;
	private VirtualMachine vm;
	private Map<String, EncryptionSpec> threadToLastEncryptionSpec;
	
    void redirectOutput() {
        Process process = vm.process();
        Thread errThread = new StreamRedirectThread(process.getErrorStream(), System.err);
        Thread outThread = new StreamRedirectThread(process.getInputStream(), System.out);
        errThread.start();
        outThread.start();
    }
	
    public enum TapType {
    	LAUNCH, PROCESS, REMOTE
    }
    
    public CryptoTap(){
    	entryBreakpoints = new String[]{"javax.crypto.Cipher.init",
    									"javax.crypto.Cipher.getInstance",
    									"javax.crypto.Cipher.doFinal",
								    	"javax.crypto.spec.IvParameterSpec.<init>",
								    	"javax.crypto.spec.SecretKeySpec.<init>"};
    	exitBreakpoints = new String[]{"javax.crypto.Cipher.doFinal"};
    	threadToLastEncryptionSpec = new HashMap<String, EncryptionSpec>();
    }
    
    public void setPid(int pid){
    	this.pid = pid;
    }
    
    public void setHostname(String hostname){
    	this.hostname = hostname;
    }
    
    public void setPort(int port){
    	this.port = port;
    }
    
    public void setList(boolean list){
    	this.list = list;
    }
    
    public void setTapType(CryptoTap.TapType type){
    	this.type = type;
    }
    
    public void setMainArgs(String mainArgs){
    	this.mainArgs = mainArgs;
    }
    
    public void processConfig(String filename) throws IOException{
    	FileInputStream fstream = new FileInputStream(filename);
    	DataInputStream in = new DataInputStream(fstream);
    	BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	String line = "";
    	
    	List<String> entry = new LinkedList<String>();
    	List<String> exit = new LinkedList<String>();
    	while((line = br.readLine()) != null){
    		if(!line.startsWith("#")){
	    		String[] l = line.split("\\s+");
	    		if(l[0].equalsIgnoreCase("entry")){
	    			entry.add(l[1]);
	    		} else if(l[0].equalsIgnoreCase("exit")) {
	    			exit.add(l[1]);
	    		} else {
	    			System.err.println("Could not process line: " + line);
	    		}
    			entryBreakpoints = entry.toArray(new String[entry.size()]);
    			exitBreakpoints = exit.toArray(new String[exit.size()]);
    		}
    	}
    }
    
    public void run() throws IOException, IllegalConnectorArgumentsException, VMStartException, InterruptedException, IncompatibleThreadStateException, ClassNotLoadedException, AbsentInformationException{
    	if(type == CryptoTap.TapType.LAUNCH){
			LaunchingConnector connector = JpdaWorkshop.getCommandLineLaunchConnector();
			Map<String, Connector.Argument> arguments = JpdaWorkshop.getMainArgumentsForCommandLineLaunchConnector(connector, mainArgs);
			vm = connector.launch(arguments);
			redirectOutput();			
    	} else if(type == CryptoTap.TapType.REMOTE){
    		SocketAttachingConnector connector = JpdaWorkshop.getSocketConnector();
    		Map<String, Connector.Argument> arguments = JpdaWorkshop.getArgumentsForSocketConnector(connector, hostname, port);
    		vm = connector.attach(arguments);
    	} else if(type == CryptoTap.TapType.PROCESS){
    		ProcessAttachingConnector connector = JpdaWorkshop.getProccessAttachConnector();
    		Map<String, Connector.Argument> arguments = JpdaWorkshop.getArgumentsForProcessAttachConnector(connector, pid);
    		vm = connector.attach(arguments);
    	}
    	
    	if(list){
    		for(ReferenceType rt : vm.allClasses()){
    			System.out.println(rt.name());
    		}
    		vm.dispose();
    		System.exit(0);
    	} else {
			EventRequestManager mgr = vm.eventRequestManager();		
			addEntryBreakpoints(mgr);
			addExitBreakpoints(mgr);
		
			EventQueue q = vm.eventQueue();
			boolean running = true;
			while(running){
				try{
					EventSet es = q.remove();
					Iterator<Event> it = es.iterator();
					while(it.hasNext()){
						Event e = it.next();
						
						// Add an empty encryption spec if there hasnt been one created
						// for the thread yet
						if(e instanceof LocatableEvent){
							String threadName = ((LocatableEvent) e).thread().name();
							if(threadToLastEncryptionSpec.get(threadName) == null){
								threadToLastEncryptionSpec.put(threadName, new EncryptionSpec());
							}
						}
						if(e instanceof MethodEntryEvent){
							processEventEntryBreakpoint((MethodEntryEvent)e);
						} else if(e instanceof MethodExitEvent){
							processEventExitBreakpoint((MethodExitEvent)e);
						}
						es.resume();
					}
				} catch (VMDisconnectedException e) {
					// Application has closed, or the debugger has been disconnected
					System.out.println("The debugger has been disconnected.");
					running = false;
				}
			}
    	}
    }
    
	
	private void processEventExitBreakpoint(MethodExitEvent mee) throws IncompatibleThreadStateException{
		String methodSig = mee.method().declaringType().name() + "." + mee.method().name();
		
		// Do final will either be an encryption or decryption routine
		if(methodSig.equals("javax.crypto.Cipher.doFinal")){
			EncryptionSpec spec = this.threadToLastEncryptionSpec.get(mee.thread().name());
			// If the last cipher was a decryption cipher, the return value should be 
			// the decrypted string. 
			if(spec.getEncryptOrDecrypt() == Cipher.DECRYPT_MODE){
				System.out.println(Color.BLACK + "Decrypted:\t" + Color.RED + new String(JpdaWorkshop.arrayReferenceToByteArray((ArrayReference)mee.returnValue())) + Color.RESET);
			} else {
				System.out.println(Color.BLACK + "Encrypted:\t" + Color.BLUE + JpdaWorkshop.arrayReferenceToString((ArrayReference)mee.returnValue()) + Color.RESET);
			}
		}
	}
	
	private void processEventEntryBreakpoint(MethodEntryEvent mee) throws IncompatibleThreadStateException, ClassNotLoadedException, InterruptedException, AbsentInformationException{
		String methodSig = mee.method().declaringType().name() + "." + mee.method().name();
		EncryptionSpec spec = this.threadToLastEncryptionSpec.get(mee.thread().name());
		StackFrame sf = mee.thread().frame(0);
		int args = sf.getArgumentValues().size();
		
		if(methodSig.equals("javax.crypto.spec.SecretKeySpec.<init>")){
			//SecretKeySpec(byte[] key, String algorithm)
			if(args == 2){
				ArrayReference arg0 = (ArrayReference)sf.getArgumentValues().get(0);
				spec.setKey(JpdaWorkshop.arrayReferenceToByteArray(arg0));
				StringReference arg1 = (StringReference)sf.getArgumentValues().get(1);
				spec.setSecretKeySpecType(arg1.value());
			}
			//SecretKeySpec(byte[] key, int offset, int len, String algorithm) 
			else if(args == 4){
				ArrayReference arg0 = (ArrayReference)sf.getArgumentValues().get(0);
				byte[] fullkey = JpdaWorkshop.arrayReferenceToByteArray(arg0);
				IntegerValue arg1 = (IntegerValue)sf.getArgumentValues().get(1);
				int offset = arg1.value();
				IntegerValue arg2 = (IntegerValue)sf.getArgumentValues().get(2);
				int len  = arg2.value();
				byte[] key = new byte[len];
				for(int i=0; i<len; i++){
					key[i] = fullkey[offset + i];
				}
				StringReference arg3 = (StringReference)sf.getArgumentValues().get(3);
				spec.setSecretKeySpecType(arg3.value());
			}
			System.out.println(Color.BLACK + "Secret key:\t" + Color.BLUE + spec.getSecretKeySpecType() + JpdaWorkshop.byteArrayToString(spec.getKey()) + Color.RESET);
			
		} else if(methodSig.equals("javax.crypto.spec.IvParameterSpec.<init>")){
			//IvParameterSpec(byte[] iv)
			if(args == 1){
				ArrayReference iv = (ArrayReference)sf.getArgumentValues().get(0);
				spec.setIv(JpdaWorkshop.arrayReferenceToByteArray(iv));
			}
			//IvParameterSpec(byte[] iv, int offset, int len)
			else if(args == 3){
				ArrayReference arg0 = (ArrayReference)sf.getArgumentValues().get(0);
				byte[] fulliv = JpdaWorkshop.arrayReferenceToByteArray(arg0);
				IntegerValue arg1 = (IntegerValue)sf.getArgumentValues().get(1);
				int offset = arg1.value();
				IntegerValue arg2 = (IntegerValue)sf.getArgumentValues().get(2);
				int len  = arg2.value();
				byte[] iv = new byte[len];
				for(int i=0; i<len; i++){
					iv[i] = fulliv[offset + i];
				}	
				spec.setIv(iv);
			}
			System.out.println(Color.BLACK + "IV:\t\t" + Color.BLUE + JpdaWorkshop.byteArrayToString(spec.getIv()) + Color.RESET);
		} else if (methodSig.equals("javax.crypto.Cipher.init")){
			IntegerValue value = (IntegerValue)sf.getArgumentValues().get(0);
			spec.setEncryptOrDecrypt(value.intValue());
			if(value.intValue() == Cipher.ENCRYPT_MODE){
			}
		} else if(methodSig.equals("javax.crypto.Cipher.getInstance")){
			StringReference arg0 = (StringReference)sf.getArgumentValues().get(0);
			spec.setCipherType(arg0.value());
			System.out.println(Color.BLACK + "Cipher type:\t" + Color.BLUE + spec.getCipherType() + Color.RESET);
		} else if(methodSig.equals("javax.crypto.Cipher.doFinal")){
			ArrayReference arg0 = (ArrayReference)sf.getArgumentValues().get(0);
			if(spec.getEncryptOrDecrypt() == Cipher.DECRYPT_MODE){
				System.out.println(Color.BLACK + "Decrypting:\t" + Color.BLUE + JpdaWorkshop.arrayReferenceToString(arg0) + Color.RESET);
			} else {
				System.out.println(Color.BLACK + "Encrypting:\t" + Color.BLUE + new String(JpdaWorkshop.arrayReferenceToByteArray(arg0)) + Color.RESET);
			}
		} else {
			//System.out.println("We dont know what the bp is for: " + methodSig);
		}
	}
	
	private boolean shouldBreak(String[] breakMethods, MethodEntryEvent mee){
		String methodSig = mee.method().declaringType().name() + "." + mee.method().name();
		for(String b : breakMethods){
			if(methodSig.matches("^" + b + ".*")){
				return(true);
			}
		}
		return(false);
	}
	
	private boolean shouldBreak(String[] breakMethods, MethodExitEvent mee){
		String methodSig = mee.method().declaringType().name() + "." + mee.method().name();
		for(String b : breakMethods){
			if(methodSig.matches("^" + b + ".*")){
				return(true);
			}
		}
		return(false);
	}
	
	private void addExitBreakpoints(EventRequestManager mgr){
		Map<String, List<String>> bps = new HashMap<String, List<String>>();
		for(String bp : exitBreakpoints){
			String actualBp = bp.substring(0, bp.lastIndexOf('.'));
			String method = bp.substring(bp.lastIndexOf('.')+1, bp.length());
			if(!bps.keySet().contains(actualBp)){
				List<String> methods = new LinkedList<String>();
				methods.add(method);
				bps.put(actualBp, methods);
			}else if(!bps.get(actualBp).contains(method)){
				List<String> methods = bps.get(actualBp);
				methods.add(method);
			}
		}
		
		for(String bp: bps.keySet()){
			MethodExitRequest req = mgr.createMethodExitRequest();
			req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
			req.addClassFilter(bp);
			req.putProperty("methodNames", bps.get(bp));
			req.enable();	
		}
	}
	
	private void addEntryBreakpoints(EventRequestManager mgr){
		Map<String, List<String>> bps = new HashMap<String, List<String>>();
		for(String bp : entryBreakpoints){
			String actualBp = bp.substring(0, bp.lastIndexOf('.'));
			String method = bp.substring(bp.lastIndexOf('.')+1, bp.length());
			if(!bps.keySet().contains(actualBp)){
				List<String> methods = new LinkedList<String>();
				methods.add(method);
				bps.put(actualBp, methods);
			}else if(!bps.get(actualBp).contains(method)){
				List<String> methods = bps.get(actualBp);
				methods.add(method);
			}
		}
		
		for(String bp: bps.keySet()){
			MethodEntryRequest req = mgr.createMethodEntryRequest();
			req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
			req.addClassFilter(bp);
			req.putProperty("methodNames", bps.get(bp));
			req.enable();	
		}
	}
	/*
	private void addExitBreakpoints(EventRequestManager mgr){
		List<String> bps = new LinkedList<String>();
		for(String bp : exitBreakpoints){
			String actualBp = bp.substring(0, bp.lastIndexOf('.'));
			if(!bps.contains(actualBp)){
				bps.add(actualBp);
			}
		}
		
		for(String bp: bps){
			MethodExitRequest req = mgr.createMethodExitRequest();
			req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
			req.addClassFilter(bp);
			req.enable();	
		}
	}
	
	private void addEntryBreakpoints(EventRequestManager mgr){
		List<String> bps = new LinkedList<String>();
		for(String bp : entryBreakpoints){
			String actualBp = bp.substring(0, bp.lastIndexOf('.'));
			if(!bps.contains(actualBp)){
				bps.add(actualBp);
			}
		}
		
		for(String bp : bps){
			MethodEntryRequest req = mgr.createMethodEntryRequest();
			req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
			req.addClassFilter(bp);
			req.enable();
		}
	}
	*/
	
	private String listToString(List<String> list){
		String ret = list.get(0);
		if(ret.length() > 1){
			for(int i=1; i<list.size(); i++){
				ret = ret + ":" + list.get(i);
			}
		}
		return(ret);
	}
		
}

class StreamRedirectThread extends Thread {

    private final Reader in;
    private final Writer out;
    
    private static final int BUFFER_SIZE = 2048;

    StreamRedirectThread(InputStream in, OutputStream out) {
        super();
        this.in = new InputStreamReader(in);
        this.out = new OutputStreamWriter(out);
        setPriority(Thread.MAX_PRIORITY - 1);
    }

    public void run() {
        try {
            char[] cbuf = new char[BUFFER_SIZE];
            int count;
            while ((count = in.read(cbuf, 0, BUFFER_SIZE)) >= 0) {
                out.write(cbuf, 0, count);
            }
            out.flush();
        } catch(IOException exc) {
            System.err.println("Child I/O Transfer - " + exc);
        }
    }
}

class Color {
	public static final String RESET = "\u001B[0m";
	public static final String BLACK = "\u001B[30m";
	public static final String RED = "\u001B[31m";
	public static final String GREEN = "\u001B[32m";
	public static final String YELLOW = "\u001B[33m";
	public static final String BLUE = "\u001B[34m";
	public static final String PURPLE = "\u001B[35m";
	public static final String CYAN = "\u001B[36m";
	public static final String WHITE = "\u001B[37m";
	
	public static final String BOLD_ON = "\u001B[1m";
	public static final String BOLD_OFF = "\u001B[22m";
}
