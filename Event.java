import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Event implements Serializable {
	public Calendar begin = null;
	public Calendar end = null;
	public String description = null;
	public int accessCtrl = -1;
	
	public Event(Calendar cb, Calendar ce, String des, int ac) {
		begin = cb;
		end = ce;
		description = des;
		accessCtrl = ac;
	}
	
	public void setEvent(Calendar cb, Calendar ce, String des, int ac) {
		begin = cb;
		end = ce;
		description = des;
		accessCtrl = ac;
	}
	
	public Calendar getBeginCalendar() {
		return begin;
	}
	
	public Calendar getEndCalendar() {
		return end;
	}
	
	public int getAccessCtrl() {
		return accessCtrl;
	}
	
	public void setAccessCtrl(int ac) {
		accessCtrl = ac;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String s) {
		description = s;
	}
	
	public String toString() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String beginStr = format.format(begin.getTime());
		String endStr = format.format(end.getTime());
		String res = beginStr + " " + endStr + " " + description + " " + String.valueOf(accessCtrl);
		return res;
	}
}
