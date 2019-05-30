package org.marse.core;
public class Time {

	private static Time instance;
	
	private long startTime = System.currentTimeMillis();
	
	static {
		instance = new Time();
	}
	
	private Time() {}
	
	public static long getSystemTime() {
		return System.currentTimeMillis();
	}
	
	public static long getMillis() {
		return System.currentTimeMillis() - instance.startTime;
	}
	
	public static long getSeconds() {
		return getTotalSeconds() % 60;
	}
	
	public static long getTotalSeconds() {
		return (System.currentTimeMillis() - instance.startTime) / 1000;
	}
	
	public static long getMinutes() {
		return getTotalMinutes() % 60;
	}
	
	public static long getTotalMinutes() {
		return getTotalSeconds() / 60;
	}
	
	public static long getHours() {
		return getTotalHours() % 24;
	}
	
	public static long getTotalHours() {
		return getTotalSeconds() / 3600;
	}
	
	public static long getTotalDays() {
		return getTotalHours() / 24;
	}
	
}
