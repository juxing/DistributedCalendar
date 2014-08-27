import java.rmi.RemoteException;
import java.util.Calendar;

public class CalendarNotificator implements Runnable{
	private CalendarObjectImpl co = null;
	private CalendarUI ui = null;  //CalendarUI should be notified.
	
	public CalendarNotificator(CalendarObjectImpl myco, CalendarUI myui) {
		co = myco;
		ui = myui;
	}
	
	public void run() {		
		try {
			//System.out.println("Thread is running.");
			String lastDes = null;
			while(ui.isRunning()) {  //As long as CalendarUI is running, we need to notify them.
				Calendar currTime = Calendar.getInstance();
				Event e = co.getUpComingEvent(currTime);
				if(e != null) {
					long diffMins = (e.getBeginCalendar().getTime().getTime() - currTime.getTime().getTime()) / (1000 * 60);
					if((!e.getDescription().equals(lastDes)) && (e.getAccessCtrl() != 3) && (diffMins < 1)) {  //If left with less than 1 min.
						ui.remind(e);
						lastDes = e.getDescription();  //If just notified, do not notify again.				
					}
				}
				//Thread.sleep(1 * 60 * 1000);
			}
			System.out.println("Notificator thread stop.");
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
