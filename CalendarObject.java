import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TreeMap;

public interface CalendarObject extends Remote {
    public boolean scheduleEvent(ArrayList<String> users, Event e) throws RemoteException;
    public ArrayList<Event> retrieveEvent(String user, Calendar begin, Calendar end) throws RemoteException;
    public boolean deleteEvent(Calendar begin) throws RemoteException;
    public boolean hasUI() throws RemoteException;
    public TreeMap<Calendar, Event> getEvents() throws RemoteException;
    public void setEvents(TreeMap<Calendar, Event> events) throws RemoteException;
    public String getName() throws RemoteException;
    public Event getAvailableOpenEvent(Event e) throws RemoteException;
    public void insertEvent(Event open, Event e) throws RemoteException;
    public String getLock() throws RemoteException;
}
