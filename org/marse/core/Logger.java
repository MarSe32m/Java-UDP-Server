package org.marse.core;
public class Logger {
	
	private Logger() {}
	
	//TODO: Add colored output
	public static void info(String message) {
		System.out.println( getTimePrefix() + "[INFO]: " + message);
	}

	//TODO: Add colored output
	public static void warning(String message) {
		System.out.println(getTimePrefix() + "[WARNING]: " + message);
	}

	//TODO: Add colored output
	public static void error(String message) {
		System.out.println(getTimePrefix() + "[ERROR]: " + message);
	}
	
	private static String getTimePrefix() {
		final long seconds = Time.getSeconds();
		final long minutes = Time.getMinutes();
		final long hours = Time.getHours();
		final long days = Time.getTotalDays();
		return "[" + (days > 0 ? days > 10 ? days + ":" : "0" + days + ":" : "") + 
					  (hours > 10 ? hours : "0" + hours) + 
				":" + (minutes > 10 ? minutes : "0" + minutes) +
				":" + (seconds > 10 ? seconds : "0" + seconds) + "]";
	}
	
}
