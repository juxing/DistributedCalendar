import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Calendar;
import java.util.TreeMap;

public class CalendarObjectData implements Serializable {
	//private String fileName = null;
	private TreeMap<Calendar, Event> events = null;  //CalendarObject data we should save.
	
	public CalendarObjectData(TreeMap<Calendar, Event> bEvents) {
		events = bEvents;
	}
	
	public TreeMap<Calendar, Event> getEvents() {
		return events;
	}
	
	//Load data from file.
	public static CalendarObjectData load(String file) throws IOException, ClassNotFoundException {
		if((new File(file)).exists()) {
			FileInputStream fin = new FileInputStream(file);
			ObjectInputStream oin = new ObjectInputStream(fin);
			CalendarObjectData data = (CalendarObjectData)oin.readObject();
			oin.close();
			return data;
		}
		else
			return null;
	}
	
	//Save data to file.
	public static boolean save(TreeMap<Calendar, Event> bEvents, String file) throws IOException {
		File f = new File(file);
		if(!(f.getParentFile().exists())) {
			f.getParentFile().mkdirs();
		}
		
		FileOutputStream fout = new FileOutputStream(file);
		ObjectOutputStream oout = new ObjectOutputStream(fout);
		oout.writeObject(new CalendarObjectData(bEvents));
		oout.close();
		return true;
	}
}
