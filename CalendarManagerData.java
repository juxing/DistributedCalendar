import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class CalendarManagerData implements Serializable {
	private HashMap<String, CalendarObjectImpl> userCalObjs = null;  //Data need to be stored.
	private HashMap<String, ArrayList<String>> groupEvents = null;
		
	public CalendarManagerData(HashMap<String, CalendarObjectImpl> u, HashMap<String, ArrayList<String>> g) {
		userCalObjs = u;
		groupEvents = g;;
	}
	
	public HashMap<String, CalendarObjectImpl> getUserCalObjs() {
		return userCalObjs;
	}
	
	public HashMap<String, ArrayList<String>> getGroupEvents() {
		return groupEvents;
	}
	
	
	//Load CalendarManager's data from file.
	public static CalendarManagerData load(String cm) throws IOException, ClassNotFoundException {
		if((new File(cm)).exists()) {
			FileInputStream fin = new FileInputStream(cm);
			ObjectInputStream oin = new ObjectInputStream(fin);
			CalendarManagerData data = (CalendarManagerData)oin.readObject();
			oin.close();
			return data;
		}
		else
			return null;
	}
		
	//Save CalendarManager's data into file.
	public static boolean save(HashMap<String, CalendarObjectImpl> u, HashMap<String, ArrayList<String>> g, String fileName) throws IOException {
		//Before store userCalObjs, clear all CalendarObjectImpls.
		HashMap<String, CalendarObjectImpl> ucopy = new HashMap<String, CalendarObjectImpl>();
		Iterator<String> it = u.keySet().iterator();
		while(it.hasNext()) {
			String user = it.next();
			ucopy.put(user, null);
		}
		
		File f = new File(fileName);
		if(!(f.getParentFile().exists())) {
			f.getParentFile().mkdirs();
		}
		//Modify policy to give this app permission to create file, maybe because I use this policy instead of system default policy file in jre.
		//Because normally app is granted permission to create files.
		FileOutputStream fout = new FileOutputStream(fileName);
		ObjectOutputStream oout = new ObjectOutputStream(fout);
		oout.writeObject(new CalendarManagerData(ucopy, g));
		oout.close();
		return true;
	}
}

