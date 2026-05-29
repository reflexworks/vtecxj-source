package jp.reflexworks.test;

import java.util.Date;

public class DivideTest {
	
	public static void main() {
		long time = new Date().getTime();
		long result = time / 1000;
		System.out.println("time(millisec)=" + time + "\n   result(sec)=" + result);
	}

}
