package com.wuntee.ct.jpda;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.jdi.ArrayReference;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ByteValue;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.Connector.Argument;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.NativeMethodException;
import com.sun.tools.jdi.*;

@SuppressWarnings("restriction")
public class JpdaWorkshop {	
	
	public static ProcessAttachingConnector getProccessAttachConnector() {
		List<Connector> connectors = Bootstrap.virtualMachineManager().allConnectors();
		for (Connector c : connectors) {
			if (c.name().equals("com.sun.jdi.ProcessAttach")) {
				return ((ProcessAttachingConnector)c);
			}
		}
		return (null);
	}
	
	public static Map<String, Connector.Argument> getArgumentsForProcessAttachConnector(Connector connector, int pid){
		Map<String, Connector.Argument> arguments = connector.defaultArguments();
		Connector.Argument pidArgument = arguments.get("pid");
		pidArgument.setValue(new Integer(pid).toString());
		return(arguments);
	}

	
	public static SocketAttachingConnector getSocketConnector() {
		List<Connector> connectors = Bootstrap.virtualMachineManager().allConnectors();
		for (Connector c : connectors) {
			if (c.name().equals("com.sun.jdi.SocketAttach")) {
				return (SocketAttachingConnector) (c);
			}
		}
		throw new Error("No socket connector");
	}
	
	
	public static Map<String, Connector.Argument> getArgumentsForSocketConnector(Connector connector, String hostname, int port){
		Map<String, Connector.Argument> arguments = connector.defaultArguments();
		Connector.Argument hostnameArgument = arguments.get("hostname");
		hostnameArgument.setValue(hostname);
		
		Connector.Argument portArgument = arguments.get("port");
		portArgument.setValue(Integer.toString(port));
		
		arguments.put("hostname", hostnameArgument);
		arguments.put("port", portArgument);
		
		return(arguments);
	}

	public static LaunchingConnector getCommandLineLaunchConnector() {
		List<Connector> connectors = Bootstrap.virtualMachineManager().allConnectors();
		for (Connector connector : connectors) {
			if (connector.name().equals("com.sun.jdi.CommandLineLaunch")) {
				return (LaunchingConnector) connector;
			}
		}
		throw new Error("No launching connector");
	}
	
	public static Map<String, Connector.Argument> getMainArgumentsForCommandLineLaunchConnector(LaunchingConnector connector, String args){
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        Connector.Argument mainArg = (Connector.Argument)arguments.get("main");
        if (mainArg == null) {
            throw new Error("Bad launching connector");
        }
        mainArg.setValue(args);
        
        Connector.Argument optionArg = (Connector.Argument)arguments.get("options");
        if (optionArg == null) {
            throw new Error("Bad launching connector");
        }
        optionArg.setValue("-classic");
        
        return(arguments);
	}
	
	public static void addBreakpointToMethod(Method m, EventRequestManager mgr){
		try{
			Location location = m.location(); 
			BreakpointRequest bpr = mgr.createBreakpointRequest(location);
			bpr.enable();
		} catch (NativeMethodException e){
			System.out.println("Error: Cant add breakpoint to native method (" + m.toString() + ")");
		}
	}
	
	public static String arrayReferenceToString(ArrayReference ref){
		String value = "[" + ref.getValue(0);
		if(ref.getValues().size() > 1){
			for(int i=1; i<ref.getValues().size(); i++){
				value = value + ", " + ref.getValue(i);
			}
		}
		value = value + "]";
		return(value);
	}
	
	public static String byteArrayToString(byte[] arr){
		String value = "[" + arr[0];
		if(arr.length > 1){
			for(int i=1; i<arr.length; i++){
				value = value + ", " + arr[i];
			}
		}
		value = value + "]";
		return(value);		
	}
	
	public static byte[] arrayReferenceToByteArray(ArrayReference ref){
		List<Byte> ret = new LinkedList<Byte>();
		for(Value v: ref.getValues()){
			if(v instanceof ByteValue){
				ret.add(((ByteValue) v).byteValue());
			} else {
				return(null);
			}
		}
		byte[] r = new byte[ret.size()];
		for(int i=0; i<ret.size(); i++){
			r[i] = ret.get(i).byteValue();
		}
		return(r);
	}

}
