package test_informal;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

import redis.clients.jedis.Jedis;

public class testJavaJulia {
	public static void main(String[] args) {
		testJavaJulia obj=new testJavaJulia();
		Jedis jedis=new Jedis();
		jedis.set("ctrl", "0");
        Process p;
        final ClassLoader loader = obj.getClass().getClassLoader();
        String[] commands = { "julia", loader.getResource("simpleLQN.jl").getPath(),"1.0","1.0","20"};
        try {
            p = Runtime.getRuntime().exec(commands);
            while(true) {
            	String isCtrlStarted=jedis.get("ctrl");
            	if(isCtrlStarted!=null && isCtrlStarted.equals("1")) {
            		break;
            	}
            }
            System.out.println("Ctrl started");
            p.destroy();
            jedis.set("ctrl", "0");
            System.out.println ("exit: " + p.exitValue());
        } catch (Exception e) {}
	}
}
